package com.meow.music.data.lyrics

import android.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Gemini API Key management and model configuration.
 * Automatically rotates keys and Flash / Flash-Lite models to avoid rate limits and stuck requests.
 */
object GeminiApi {
    private val encodedKeys = listOf(
        "QVEuQWI4Uk42S0gwZHFLTXlDNlFNNmZXZUxCZjNYTFdWRXNaaWl1RnAwNTB4UnQtalIxVXc=",
        "QVEuQWI4Uk42SGNLTDdzaWNKbHBNT2dOQXJiM0ZnQkgtdXhZR2hYSjlSRnAyTENSOUJXMGc=",
        "QUl6YVN5RENUNXU1WHJZS0NCODliYU5KN1JieW9UUEhoZC1veHVV",
        "QVEuQWI4Uk42Sjh3SWhXNWRJUVZieTR1dXZaeERZQlM0TU9TcE16blRUNEZObE5oNXdXSmc=",
        "QVEuQWI4Uk42SkNhRXRZTFhFeS04dTJpUlB5Q3BPZ2NCZTRBcU9Rb1VESndhcC1VTUloM0E=",
        "QUl6YVN5Qi1ua0pCLUYyY2NBQ0RUZ1B5VlNsNnZiaUZlMDN6OXZj"
    )

    val FLASH_MODELS = listOf(
        "gemini-2.5-flash-lite",
        "gemini-2.0-flash-lite",
        "gemini-2.0-flash-lite-preview-02-05",
        "gemini-2.0-flash",
        "gemini-1.5-flash",
        "gemini-1.5-flash-8b",
        "gemini-2.5-flash",
        "gemini-3.1-flash-lite"
    )

    @Volatile
    var activeModel: String = FLASH_MODELS.first()

    private fun decodeKey(encoded: String): String {
        return try {
            String(Base64.decode(encoded, Base64.NO_WRAP), Charsets.UTF_8)
        } catch (_: Exception) {
            encoded
        }
    }

    private val defaultKeys: List<String>
        get() = encodedKeys.map { decodeKey(it) }

    /**
     * Get a random Gemini API key from the default pool.
     */
    fun getRandomKey(): String = defaultKeys.random()

    /**
     * Returns a list of Gemini API keys to try in shuffled/random order.
     * If a custom user key is provided, it is prepended first.
     */
    fun getShuffledKeys(customKey: String = ""): List<String> {
        val shuffled = defaultKeys.shuffled().toMutableList()
        val trimmedCustom = customKey.trim()
        if (trimmedCustom.isNotBlank() && trimmedCustom !in shuffled) {
            shuffled.add(0, trimmedCustom)
        }
        return shuffled
    }

    /**
     * Returns candidate Flash models, prioritizing the last working model.
     */
    fun getOrderedModels(): List<String> {
        val current = activeModel
        return listOf(current) + FLASH_MODELS.filter { it != current }
    }

    /**
     * Translates lines of lyrics line-by-line using Gemini Flash models with auto-key/model rotation.
     * Returns null if all keys/models fail or are rate limited.
     */
    fun translateWithGemini(
        http: OkHttpClient,
        texts: List<String>,
        targetLang: String
    ): List<String>? {
        if (texts.isEmpty()) return emptyList()

        val prompt = "Translate the following lyrics line by line into target language ($targetLang).\n" +
                "Maintain the exact line count (${texts.size} lines).\n" +
                "Do NOT include explanations, line numbers, or code blocks. Output strictly ${texts.size} translated lines, one per line:\n\n" +
                texts.joinToString("\n")

        val jsonBody = JsonObject(
            mapOf(
                "contents" to JsonArray(
                    listOf(
                        JsonObject(
                            mapOf(
                                "parts" to JsonArray(
                                    listOf(
                                        JsonObject(mapOf("text" to JsonPrimitive(prompt)))
                                    )
                                )
                            )
                        )
                    )
                ),
                "generationConfig" to JsonObject(
                    mapOf(
                        "temperature" to JsonPrimitive(0.1),
                        "maxOutputTokens" to JsonPrimitive(4096)
                    )
                )
            )
        ).toString()

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = jsonBody.toRequestBody(mediaType)

        val keys = getShuffledKeys()
        val models = getOrderedModels()

        for (model in models) {
            for (key in keys) {
                val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$key"
                val request = Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .build()

                try {
                    http.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) return@use
                        val responseString = response.body.string()
                        val root = Json.parseToJsonElement(responseString).jsonObject
                        val candidate = root["candidates"]?.jsonArray?.getOrNull(0)?.jsonObject
                        val parts = candidate?.get("content")?.jsonObject?.get("parts")?.jsonArray
                        val rawText = parts?.getOrNull(0)?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
                            ?: return@use

                        val cleanedText = rawText.trim()
                            .removePrefix("```markdown")
                            .removePrefix("```text")
                            .removePrefix("```")
                            .removeSuffix("```")
                            .trim()

                        val resultLines = cleanedText.split("\n").map { it.trim() }
                        if (resultLines.size == texts.size) {
                            activeModel = model
                            return resultLines
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.w("GeminiApi", "Error with model $model: ${e.message}")
                }
            }
        }
        return null
    }
}
