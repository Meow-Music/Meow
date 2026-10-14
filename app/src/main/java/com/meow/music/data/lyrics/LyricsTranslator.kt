package com.meow.music.data.lyrics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * Lyric translations, line by line, from Google Translate's keyless web endpoint (the one its browser extension uses).
 * Only the lyrics' text is sent, and only when the listener has turned translations on.
 */
class LyricsTranslator(private val http: OkHttpClient) {
    /** A song's lines in another language; [translations] lines up with the lines asked about (null: nothing to show). */
    class Translation(val from: String?, val to: String, val translations: List<String?>) {
        /** The song is already in [to] with no differing translations to display. */
        val sameLanguage: Boolean get() = translations.all { it == null }
    }

    private val cache = object : LinkedHashMap<String, Translation>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Translation>?) = size > 40
    }

    /** Translates [lines] into [to] (a language code such as "es" or "pt-BR"). Blank lines stay null. */
    suspend fun translate(lines: List<String>, to: String): Translation = withContext(Dispatchers.IO) {
        val key = to + "|" + lines.joinToString("\n").hashCode()
        synchronized(cache) { cache[key] }?.let { return@withContext it }
        val wanted = lines.withIndex().filter { it.value.isNotBlank() }
        val out = arrayOfNulls<String>(lines.size)
        var from: String? = null
        // Google takes about 5000 characters at a time; lyrics rarely need more than one request.
        for (batch in batches(wanted.map { it.value.trim() })) {
            val (translated, detected) = requestBatchWithFallback(batch.texts, to)
            from = from ?: detected
            translated.forEachIndexed { i, t ->
                val orig = wanted[batch.start + i].value.trim()
                val clean = t.trim()
                out[wanted[batch.start + i].index] = if (clean.isNotEmpty() && !clean.equals(orig, ignoreCase = true)) clean else null
            }
        }
        Translation(from, to, out.toList()).also { synchronized(cache) { cache[key] = it } }
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

    /** Translates [texts] in one request if possible; falls back to line-by-line if line count mismatches or errors occur. */
    private fun requestBatchWithFallback(texts: List<String>, to: String): Pair<List<String>, String?> {
        if (texts.isEmpty()) return emptyList<String>() to null
        try {
            val (translated, detected) = request(texts, to)
            if (translated.size == texts.size) {
                return translated to detected
            }
        } catch (_: Exception) {
            // Batch request failed; fallback to line-by-line requests
        }
        var detectedFrom: String? = null
        val lineResults = texts.map { line ->
            try {
                val (tList, det) = request(listOf(line), to)
                if (detectedFrom == null) detectedFrom = det
                tList.firstOrNull()?.trim() ?: line
            } catch (_: Exception) {
                line
            }
        }
        return lineResults to detectedFrom
    }

    /** One request: the lines joined by newlines (which Google keeps), and the language it detected. */
    private fun request(texts: List<String>, to: String): Pair<List<String>, String?> {
        val req = Request.Builder()
            .url("https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=$to&dt=t")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .post(FormBody.Builder().add("q", texts.joinToString("\n")).build())
            .build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("Translate returned HTTP ${resp.code}")
            val root = Json.parseToJsonElement(resp.body.string()).jsonArray
            val text = (root.getOrNull(0) as? JsonArray).orEmpty().joinToString("") { seg ->
                ((seg as? JsonArray)?.getOrNull(0) as? JsonPrimitive)?.contentOrNull ?: ""
            }
            val detected = (root.getOrNull(2) as? JsonPrimitive)?.contentOrNull
            return text.trimEnd('\n').split("\n") to detected
        }
    }
}
