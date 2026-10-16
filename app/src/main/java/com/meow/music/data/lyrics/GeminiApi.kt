package com.meow.music.data.lyrics

import android.util.Base64

/**
 * Gemini API Key management and model configuration.
 * Automatically rotates keys to avoid rate limits and stuck requests.
 */
object GeminiApi {
    private val encodedKeys = listOf(
        "QVEuQWI4Uk42S0gwZHFLTXlDNlFNNmZXZUxCZjNYTFdWRXNaaWl1RnAwNTB4UnQtalIxVXc=",
        "QVEuQWI4Uk42TGNLTDdzaWNKbHBNT2dOQXJiM0ZnQkgtdXhZR2hYSjlSRnAyTENSOUJXMGc=",
        "QUl6YVN5RENUNXU1WHJZS0NCODliYU5KN1JieW9UUEhoZC1veHVV",
        "QVEuQWI4Uk42SjhwSWhXNWRJUVZieTR1dXZaeERZQlM0TU9TcE16blRUNEZObE5oNXdXSmc=",
        "QVEuQWI4Uk42SkNhRXRZTFhFeS04dTJpUlB5Q3BPZ2NCZTRBcU9Rb1VESndhcC1VTUloM0E=",
        "QUl6YVN5Qi1ua0pCLUYyY2NBQ0RUZ1B5VlNsNnZiaUZlMDN6OXZj"
    )

    const val DEFAULT_MODEL = "gemini-3.1-flash-lite"

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
}
