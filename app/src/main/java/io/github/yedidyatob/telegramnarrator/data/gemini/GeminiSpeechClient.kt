package io.github.yedidyatob.telegramnarrator.data.gemini

import android.util.Base64
import io.github.yedidyatob.telegramnarrator.domain.gemini.GeminiTts
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Minimal client for POST https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-preview-tts:generateContent.
 * The response is raw 24 kHz 16-bit mono PCM in base64; this class wraps it in a 44-byte WAV header.
 * Does not log the API key or request body text.
 */
@Singleton
class GeminiSpeechClient @Inject constructor() {

    /**
     * Non-2xx answer. [errorSummary] holds only machine-readable fields from the error body;
     * [retryAfterSeconds] is the `Retry-After` header, if any.
     */
    class HttpException(val code: Int, val errorSummary: String, val retryAfterSeconds: Long?) :
        IOException("Gemini TTS HTTP $code${if (errorSummary.isNotBlank()) ": $errorSummary" else ""}")

    /** No audio data returned in a successful (2xx) response. */
    class NoAudioException : IOException("Gemini TTS returned no audio data")

    data class Request(
        val apiKey: String,
        val voice: String,
        val text: String
    )

    /**
     * @return WAV bytes (44-byte header + raw PCM)
     * @throws HttpException on non-2xx responses
     * @throws NoAudioException when the response body has no audio part
     * @throws IOException on network errors
     */
    fun synthesize(request: Request): ByteArray {
        val voice = GeminiTts.normalizeVoice(request.voice)
        val prompt = "${GeminiTts.NARRATION_INSTRUCTION}\n\n${request.text}"

        val url = URL("$ENDPOINT_BASE${GeminiTts.MODEL_ID}:generateContent")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            doOutput = true
            setRequestProperty("x-goog-api-key", request.apiKey)
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
        }
        try {
            val body = JSONObject()
                .put("contents", JSONArray().put(
                    JSONObject().put("parts", JSONArray().put(
                        JSONObject().put("text", prompt)
                    ))
                ))
                .put("generationConfig", JSONObject()
                    .put("responseModalities", JSONArray().put("AUDIO"))
                    .put("speechConfig", JSONObject()
                        .put("voiceConfig", JSONObject()
                            .put("prebuiltVoiceConfig", JSONObject()
                                .put("voiceName", voice)
                            )
                        )
                    )
                )
                .toString()
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            val code = connection.responseCode
            if (code !in 200..299) {
                val errorBody = runCatching {
                    connection.errorStream?.bufferedReader()?.use { it.readText().take(2_000) }
                }.getOrNull()
                val retryAfter = connection.getHeaderField("Retry-After")?.trim()?.toLongOrNull()
                // Only log machine-readable summary; the body can echo user text or key
                throw HttpException(code, GeminiTts.safeErrorSummary(errorBody), retryAfter)
            }

            val responseText = connection.inputStream.use { it.bufferedReader().readText() }
            val pcmBase64 = extractPcmBase64(responseText)
                ?: throw NoAudioException()
            val pcmBytes = Base64.decode(pcmBase64, Base64.DEFAULT)
            if (pcmBytes.isEmpty()) throw NoAudioException()
            return buildWav(pcmBytes)
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Extracts the base64 audio data from `candidates[0].content.parts[0].inlineData.data`.
     * Returns null when the field is absent (no audio in the response).
     */
    private fun extractPcmBase64(responseBody: String): String? = runCatching {
        val root = JSONObject(responseBody)
        val candidates = root.optJSONArray("candidates") ?: return null
        if (candidates.length() == 0) return null
        val content = candidates.getJSONObject(0).optJSONObject("content") ?: return null
        val parts = content.optJSONArray("parts") ?: return null
        if (parts.length() == 0) return null
        val inlineData = parts.getJSONObject(0).optJSONObject("inlineData") ?: return null
        inlineData.optString("data").takeIf { it.isNotEmpty() }
    }.getOrNull()

    /**
     * Wraps raw 24 kHz, 16-bit, mono PCM bytes in a standard 44-byte RIFF/WAV header.
     *
     * Header layout (all little-endian):
     *  0- 3  "RIFF"
     *  4- 7  file size - 8
     *  8-11  "WAVE"
     * 12-15  "fmt "
     * 16-19  chunk size = 16 (PCM)
     * 20-21  audio format = 1 (PCM)
     * 22-23  channels = 1
     * 24-27  sample rate = 24000
     * 28-31  byte rate = sampleRate * channels * bitsPerSample/8
     * 32-33  block align = channels * bitsPerSample/8
     * 34-35  bits per sample = 16
     * 36-39  "data"
     * 40-43  data chunk size = pcmBytes.size
     */
    internal fun buildWav(pcmBytes: ByteArray): ByteArray {
        val sampleRate = 24_000
        val channels = 1
        val bitsPerSample = 16
        val byteRate = sampleRate * channels * bitsPerSample / 8  // 48000
        val blockAlign = channels * bitsPerSample / 8             // 2
        val dataSize = pcmBytes.size
        val fileSize = WAV_HEADER_SIZE - 8 + dataSize             // total minus "RIFF" and size field

        val header = ByteBuffer.allocate(WAV_HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        // RIFF chunk
        header.put("RIFF".toByteArray(Charsets.US_ASCII))
        header.putInt(fileSize)
        header.put("WAVE".toByteArray(Charsets.US_ASCII))
        // fmt sub-chunk
        header.put("fmt ".toByteArray(Charsets.US_ASCII))
        header.putInt(16)               // sub-chunk size (PCM = 16)
        header.putShort(1)              // audio format 1 = PCM
        header.putShort(channels.toShort())
        header.putInt(sampleRate)
        header.putInt(byteRate)
        header.putShort(blockAlign.toShort())
        header.putShort(bitsPerSample.toShort())
        // data sub-chunk
        header.put("data".toByteArray(Charsets.US_ASCII))
        header.putInt(dataSize)

        return header.array() + pcmBytes
    }

    private companion object {
        const val ENDPOINT_BASE = "https://generativelanguage.googleapis.com/v1beta/models/"
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 60_000
        const val WAV_HEADER_SIZE = 44
    }
}
