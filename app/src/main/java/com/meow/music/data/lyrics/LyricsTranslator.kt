package com.meow.music.data.lyrics

import com.meow.music.data.innertube.arr
import com.meow.music.data.innertube.obj
import com.meow.music.data.innertube.str
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * Lyric translations, line by line, from Prism / Google Translate keyless web endpoint,
 * Google Gemini GenAI API, or Groq AI API when an API key is provided.
 */
class LyricsTranslator(private val http: OkHttpClient) {
    /** A song's lines in another language; [translations] lines up with the lines asked about (null: nothing to show). */
    class Translation(val from: String?, val to: String, val translations: List<String?>) {
        /** The song is already in [to]: nothing worth showing. */
        val sameLanguage: Boolean get() = from != null && from.substringBefore('-').equals(to.substringBefore('-'), true)
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val cache = object : LinkedHashMap<String, Translation>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Translation>?) = size > 40
    }

    fun clearCache() {
        synchronized(cache) { cache.clear() }
    }

    private fun cleanTranslationText(text: String): String {
        return text
            .replace("&#39;", "'")
            .replace("&#x27;", "'")
            .replace("&quot;", "\"")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
            .replace("<br>", "")
            .replace("<br/>", "")
            .replace("<br />", "")
            .trim()
    }

    /** Translates [lines] into [to] (a language code such as "es" or "pt-BR"). Blank lines stay null. */
    suspend fun translate(
        lines: List<String>,
        to: String,
        geminiApiKey: String = "",
        geminiModel: String = GeminiApi.DEFAULT_MODEL,
        groqApiKey: String = "",
    ): Translation = withContext(Dispatchers.IO) {
        val target = to.ifBlank { "en" }
        val cleanModel = geminiModel.trim().ifBlank { GeminiApi.DEFAULT_MODEL }
            .let { if (it.contains("3.6")) GeminiApi.DEFAULT_MODEL else it }
        val key = "$target|${geminiApiKey.trim()}|$cleanModel|${groqApiKey.trim()}|" + lines.joinToString("\n").hashCode()
        synchronized(cache) { cache[key] }?.let { return@withContext it }

        val wanted = lines.withIndex().filter { it.value.isNotBlank() }
        val out = arrayOfNulls<String>(lines.size)
        var detected: String? = null

        if (wanted.isNotEmpty()) {
            val wantedTexts = wanted.map { it.value.trim() }

            // 1. Try Gemini AI with auto-model fallback and randomized key rotation to prevent rate limits
            val keysToTry = GeminiApi.getShuffledKeys(geminiApiKey)
            val modelsToTry = listOf(
                cleanModel,
                GeminiApi.DEFAULT_MODEL,
                "gemini-2.5-flash-lite",
                "gemini-2.0-flash-lite",
                "gemini-1.5-flash-lite",
                "gemini-.5-flash-lite",
                "gemini-2.0-flash-lite",
                "gemini-1.5-flash-lite",
                "gemini-2.5-flash",
                "gemini-2.0-flash",
                "gemini-1.5-flash"
            ).distinct()

            var aiTranslated: List<String>? = null

            for (mod in modelsToTry) {
                for (apiKey in keysToTry) {
                    aiTranslated = runCatching {
                        translateGemini(wantedTexts, target, apiKey, mod)
                    }.onFailure {
                        android.util.Log.w("LyricsTranslator", "Gemini ($mod) error: ${it.message}")
                    }.getOrNull()

                    if (!aiTranslated.isNullOrEmpty()) break
                }
                if (!aiTranslated.isNullOrEmpty()) break
            }

            if (!aiTranslated.isNullOrEmpty()) {
                wantedTexts.forEachIndexed { i, _ ->
                    out[wanted[i].index] = aiTranslated.getOrNull(i)?.let { cleanTranslationText(it) }?.takeIf { it.isNotEmpty() }
                }
                return@withContext Translation(null, target, out.toList()).also {
                    synchronized(cache) { cache[key] = it }
                }
            }

            // 2. Try Groq AI if Groq API key provided
            if (groqApiKey.isNotBlank()) {
                val groqTranslated = runCatching {
                    translateGroq(wantedTexts, target, groqApiKey.trim())
                }.onFailure {
                    android.util.Log.w("LyricsTranslator", "Groq error: ${it.message}")
                }.getOrNull()

                if (!groqTranslated.isNullOrEmpty()) {
                    wantedTexts.forEachIndexed { i, _ ->
                        out[wanted[i].index] = groqTranslated.getOrNull(i)?.let { cleanTranslationText(it) }?.takeIf { it.isNotEmpty() }
                    }
                    return@withContext Translation(null, target, out.toList()).also {
                        synchronized(cache) { cache[key] = it }
                    }
                }
            }

            // 3. Prism keyless Google Translate engine
            for (batch in batches(wantedTexts)) {
                val (translated, det) = requestKeylessBatchOrFallback(batch.texts, target)
                detected = detected ?: det
                translated.forEachIndexed { i, t ->
                    if (i < batch.texts.size) {
                        out[wanted[batch.start + i].index] = cleanTranslationText(t).takeIf { it.isNotEmpty() }
                    }
                }
            }
        }

        Translation(detected, target, out.toList()).also { synchronized(cache) { cache[key] = it } }
    }

    /** Test connection for Gemini API Key & Model. Returns status message. */
    suspend fun testGeminiKey(apiKey: String, model: String, target: String = "en"): String = withContext(Dispatchers.IO) {
        val keysToTry = GeminiApi.getShuffledKeys(apiKey)
        val modelsToTry = listOf(
            model.trim().ifBlank { GeminiApi.DEFAULT_MODEL },
            GeminiApi.DEFAULT_MODEL,
            "gemini-2.5-flash-lite",
            "gemini-2.0-flash-lite",
            "gemini-1.5-flash-lite",
            "gemini-2.5-flash"
        ).distinct()
        var lastError = ""
        for (m in modelsToTry) {
            for (k in keysToTry) {
                val res = runCatching { translateGemini(listOf("Hello, world!"), target, k, m) }
                    .onFailure { lastError = it.message ?: it.toString() }
                    .getOrNull()
                if (!res.isNullOrEmpty()) {
                    return@withContext "✅ Success ($m): \"${res[0]}\""
                }
            }
        }
        return@withContext "❌ Error: ${lastError.ifBlank { "Could not connect to Gemini API" }}"
    }

    /** Test connection for Groq API Key. Returns status message. */
    suspend fun testGroqKey(apiKey: String, target: String = "en"): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext "API key is blank"
        val res = runCatching { translateGroq(listOf("Hello, world!"), target, apiKey.trim()) }
        return@withContext res.fold(
            onSuccess = { list ->
                if (list.isNotEmpty()) "✅ Success (Groq Llama-3.3): \"${list[0]}\""
                else "❌ Error: Groq returned empty response"
            },
            onFailure = { err ->
                "❌ Error: ${err.message ?: "Could not connect to Groq API"}"
            }
        )
    }

    /** Gemini GenAI Processor supporting Flash, Pro, and custom models. */
    private fun translateGemini(texts: List<String>, to: String, apiKey: String, model: String): List<String> {
        val rawModel = model.trim()
        val modelName = when {
            rawModel.isBlank() || rawModel.contains("3.6") -> GeminiApi.DEFAULT_MODEL
            else -> rawModel
        }
        val prompt = "You are an expert song lyrics translator.\n" +
                "Translate each line of the following song lyrics line-by-line into '$to'.\n" +
                "Maintain poetic tone, emotion, rhyme scheme, and exact line structure of the original song.\n" +
                "Output ONLY a JSON array of strings corresponding line-for-line to input (${texts.size} strings). Example: [\"line 1\", \"line 2\"].\n\n" +
                "Lyrics:\n" + texts.joinToString("\n")

        val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"
        val payload = buildJsonObject {
            putJsonArray("contents") {
                addJsonObject {
                    putJsonArray("parts") {
                        addJsonObject { put("text", prompt) }
                    }
                }
            }
            putJsonObject("generationConfig") {
                put("temperature", 0.3)
                put("responseMimeType", "application/json")
            }
        }

        val req = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                val errBody = resp.body.string()
                throw IOException("Gemini API HTTP ${resp.code}: $errBody")
            }
            val jsonText = resp.body.string()
            val root = json.parseToJsonElement(jsonText).jsonObject
            val contentText = root.arr("candidates")?.getOrNull(0)
                ?.obj("content")?.arr("parts")?.getOrNull(0)
                ?.str("text") ?: throw IOException("Empty Gemini response")

            val cleanContent = contentText.trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            val jsonArray = runCatching { json.parseToJsonElement(cleanContent).jsonArray }.getOrNull()
                ?: throw IOException("Invalid JSON array from Gemini: $cleanContent")

            return jsonArray.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim() }
        }
    }

    /** Groq AI Processor supporting Llama-3.3-70b. */
    private fun translateGroq(texts: List<String>, to: String, apiKey: String): List<String> {
        val prompt = "You are an expert song lyrics translator.\n" +
                "Translate each line of the following song lyrics line-by-line into '$to'.\n" +
                "Output ONLY a JSON object with key 'translations' containing a JSON array of strings corresponding line-for-line to input (${texts.size} strings).\n" +
                "Example format: {\"translations\": [\"line 1\", \"line 2\"]}.\n\n" +
                "Lyrics:\n" + texts.joinToString("\n")

        val payload = buildJsonObject {
            put("model", "llama-3.3-70b-versatile")
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "user")
                    put("content", prompt)
                }
            }
            put("temperature", 0.2)
            putJsonObject("response_format") {
                put("type", "json_object")
            }
        }

        val req = Request.Builder()
            .url("https://api.groq.com/openai/v1/chat/completions")
            .header("Authorization", "Bearer ${apiKey.trim()}")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                val errBody = resp.body.string()
                throw IOException("Groq API HTTP ${resp.code}: $errBody")
            }
            val jsonText = resp.body.string()
            val root = json.parseToJsonElement(jsonText).jsonObject
            val contentText = root.arr("choices")?.getOrNull(0)
                ?.obj("message")?.str("content") ?: throw IOException("Empty Groq response")

            val cleanContent = contentText.trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            val parsedJson = json.parseToJsonElement(cleanContent)
            val jsonArray = when {
                parsedJson is JsonArray -> parsedJson
                parsedJson is JsonObject -> {
                    parsedJson.arr("translations")
                        ?: parsedJson.arr("lines")
                        ?: parsedJson.values.firstOrNull { it is JsonArray } as? JsonArray
                }
                else -> null
            } ?: throw IOException("Invalid JSON array from Groq: $cleanContent")

            return jsonArray.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim() }
        }
    }

    private class Batch(val start: Int, val texts: List<String>)

    private fun batches(texts: List<String>): List<Batch> {
        val out = ArrayList<Batch>()
        var start = 0
        var size = 0
        for (i in texts.indices) {
            if (size + texts[i].length + 1 > 4500 && i > start) {
                out += Batch(start, texts.subList(start, i)); start = i; size = 0
            }
            size += texts[i].length + 1
        }
        if (start < texts.size) out += Batch(start, texts.subList(start, texts.size))
        return out
    }

    private fun requestKeylessBatchOrFallback(texts: List<String>, to: String): Pair<List<String>, String?> {
        return runCatching {
            val (translated, det) = requestKeyless(texts, to)
            if (translated.size == texts.size) {
                translated to det
            } else {
                // If line count differed slightly due to formatting, align by index up to texts.size
                val aligned = texts.indices.map { i ->
                    translated.getOrNull(i)?.takeIf { it.isNotBlank() } ?: texts[i]
                }
                aligned to det
            }
        }.getOrElse { err ->
            android.util.Log.w("LyricsTranslator", "Meow keyless batch error: ${err.message}")
            texts to null
        }
    }

    /** Prism keyless request. */
    private fun requestKeyless(texts: List<String>, to: String): Pair<List<String>, String?> {
        val query = texts.joinToString("\n")
        val url = "https://translate.googleapis.com/translate_a/single".toHttpUrl().newBuilder()
            .addQueryParameter("client", "gtx")
            .addQueryParameter("sl", "auto")
            .addQueryParameter("tl", to)
            .addQueryParameter("dt", "t")
            .addQueryParameter("q", query)
            .build()

        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
            .get()
            .build()

        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("Translate returned HTTP ${resp.code}")
            val jsonText = resp.body.string()
            val root = json.parseToJsonElement(jsonText).jsonArray
            val rawSegments = (root.getOrNull(0) as? JsonArray).orEmpty().mapNotNull { seg ->
                ((seg as? JsonArray)?.getOrNull(0) as? JsonPrimitive)?.contentOrNull
            }
            val detected = (root.getOrNull(2) as? JsonPrimitive)?.contentOrNull

            val fullText = rawSegments.joinToString("\n")
            val splitLines = fullText.split("\n").map { it.trim() }

            val resultLines = when {
                splitLines.size == texts.size -> splitLines
                rawSegments.size == texts.size -> rawSegments.map { it.trim() }
                splitLines.size > 1 -> texts.indices.map { i -> splitLines.getOrNull(i) ?: "" }
                else -> rawSegments
            }

            return resultLines to detected
        }
    }
}