package com.example.telegramnarrator.data.openai

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Minimal client for POST https://api.openai.com/v1/audio/speech.
 * Does not log the API key or request body text.
 */
@Singleton
class OpenAiSpeechClient @Inject constructor() {

    data class Request(
        val apiKey: String,
        val model: String,
        val voice: String,
        val text: String,
        val speed: Float = 1.0f
    )

    /**
     * @return MP3 bytes
     * @throws IOException on network / HTTP / empty body errors
     */
    fun synthesize(request: Request): ByteArray {
        val url = URL(ENDPOINT)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            doOutput = true
            setRequestProperty("Authorization", "Bearer ${request.apiKey}")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "audio/mpeg")
        }
        try {
            val body = JSONObject()
                .put("model", request.model)
                .put("input", request.text)
                .put("voice", request.voice)
                .put("response_format", "mp3")
                .put("speed", request.speed.toDouble().coerceIn(0.25, 4.0))
                .toString()
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            val code = connection.responseCode
            if (code !in 200..299) {
                val err = (connection.errorStream ?: connection.inputStream)
                    ?.bufferedReader()?.use { it.readText() }
                    ?.take(200)
                    .orEmpty()
                throw IOException("OpenAI TTS HTTP $code${if (err.isNotBlank()) ": $err" else ""}")
            }
            val bytes = connection.inputStream.use { it.readBytes() }
            if (bytes.isEmpty()) throw IOException("OpenAI TTS returned empty audio")
            return bytes
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val ENDPOINT = "https://api.openai.com/v1/audio/speech"
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 60_000
    }
}
