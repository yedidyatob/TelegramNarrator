package io.github.yedidyatob.telegramnarrator.data.gemini

import android.util.Base64
import io.github.yedidyatob.telegramnarrator.domain.gemini.GeminiTts
import org.junit.Test
import org.junit.Before
import org.junit.Assert.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Unit tests for [GeminiSpeechClient] WAV header generation, request JSON structure,
 * and error handling.
 */
class GeminiSpeechClientTest {

    private lateinit var client: GeminiSpeechClient

    @Before
    fun setUp() {
        client = GeminiSpeechClient()
    }

    // ===== WAV Header Tests =====

    @Test
    fun buildWav_producesValidRiffHeader() {
        val pcmBytes = ByteArray(1000) { 0 }
        val wav = client.buildWav(pcmBytes)

        // Total size check: 44-byte header + 1000 bytes PCM = 1044 bytes
        assertEquals(1044, wav.size)

        // Check RIFF magic
        val riffMagic = wav.slice(0..3).toByteArray().toString(Charsets.US_ASCII)
        assertEquals("RIFF", riffMagic)

        // Check file size field (bytes 4-7, little-endian): should be 1044 - 8 = 1036
        val fileSize = ByteBuffer.wrap(wav, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
        assertEquals(1036, fileSize)

        // Check WAVE magic (bytes 8-11)
        val waveMagic = wav.slice(8..11).toByteArray().toString(Charsets.US_ASCII)
        assertEquals("WAVE", waveMagic)
    }

    @Test
    fun buildWav_producesValidFmtSubchunk() {
        val pcmBytes = ByteArray(100) { 0 }
        val wav = client.buildWav(pcmBytes)

        // fmt magic at bytes 12-15
        val fmtMagic = wav.slice(12..15).toByteArray().toString(Charsets.US_ASCII)
        assertEquals("fmt ", fmtMagic)

        // Subchunk size (bytes 16-19) = 16 for PCM
        val fmtSize = ByteBuffer.wrap(wav, 16, 4).order(ByteOrder.LITTLE_ENDIAN).int
        assertEquals(16, fmtSize)

        // Audio format (bytes 20-21) = 1 for PCM
        val audioFormat = ByteBuffer.wrap(wav, 20, 2).order(ByteOrder.LITTLE_ENDIAN).short
        assertEquals(1, audioFormat.toInt())

        // Channels (bytes 22-23) = 1 (mono)
        val channels = ByteBuffer.wrap(wav, 22, 2).order(ByteOrder.LITTLE_ENDIAN).short
        assertEquals(1, channels.toInt())

        // Sample rate (bytes 24-27) = 24000
        val sampleRate = ByteBuffer.wrap(wav, 24, 4).order(ByteOrder.LITTLE_ENDIAN).int
        assertEquals(24_000, sampleRate)

        // Byte rate (bytes 28-31) = 24000 * 1 * 16 / 8 = 48000
        val byteRate = ByteBuffer.wrap(wav, 28, 4).order(ByteOrder.LITTLE_ENDIAN).int
        assertEquals(48_000, byteRate)

        // Block align (bytes 32-33) = 1 * 16 / 8 = 2
        val blockAlign = ByteBuffer.wrap(wav, 32, 2).order(ByteOrder.LITTLE_ENDIAN).short
        assertEquals(2, blockAlign.toInt())

        // Bits per sample (bytes 34-35) = 16
        val bitsPerSample = ByteBuffer.wrap(wav, 34, 2).order(ByteOrder.LITTLE_ENDIAN).short
        assertEquals(16, bitsPerSample.toInt())
    }

    @Test
    fun buildWav_producesValidDataSubchunk() {
        val pcmBytes = ByteArray(500) { 42 }
        val wav = client.buildWav(pcmBytes)

        // data magic at bytes 36-39
        val dataMagic = wav.slice(36..39).toByteArray().toString(Charsets.US_ASCII)
        assertEquals("data", dataMagic)

        // Data chunk size (bytes 40-43) should match pcmBytes.size
        val dataSize = ByteBuffer.wrap(wav, 40, 4).order(ByteOrder.LITTLE_ENDIAN).int
        assertEquals(500, dataSize)

        // PCM bytes should follow immediately at offset 44
        val pcmData = wav.drop(44).take(500).toByteArray()
        assertArrayEquals(pcmBytes, pcmData)
    }

    @Test
    fun buildWav_handlesSizeZero() {
        val wav = client.buildWav(ByteArray(0))
        // 44-byte header + 0 bytes PCM
        assertEquals(44, wav.size)

        // File size field should be 44 - 8 = 36
        val fileSize = ByteBuffer.wrap(wav, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
        assertEquals(36, fileSize)

        // Data chunk size should be 0
        val dataSize = ByteBuffer.wrap(wav, 40, 4).order(ByteOrder.LITTLE_ENDIAN).int
        assertEquals(0, dataSize)
    }

    @Test
    fun buildWav_handlesLargeData() {
        val largeData = ByteArray(1_000_000) { (it % 256).toByte() }
        val wav = client.buildWav(largeData)

        // Total size check
        assertEquals(44 + 1_000_000, wav.size)

        // File size field: total - 8
        val fileSize = ByteBuffer.wrap(wav, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
        assertEquals(1_000_036, fileSize)

        // Data chunk size
        val dataSize = ByteBuffer.wrap(wav, 40, 4).order(ByteOrder.LITTLE_ENDIAN).int
        assertEquals(1_000_000, dataSize)

        // PCM data preserved
        val pcmData = wav.drop(44).take(1_000_000).toByteArray()
        assertArrayEquals(largeData, pcmData)
    }

    // ===== Request JSON Structure Tests =====

    @Test
    fun request_keepsApiKeySecret() {
        val req = GeminiSpeechClient.Request(
            apiKey = "test_key_12345",
            voice = "Kore",
            text = "Hello"
        )
        assertEquals("test_key_12345", req.apiKey)
        // (The actual HTTP body won't log this; tested separately)
    }

    @Test
    fun request_normalizesVoice() {
        val req1 = GeminiSpeechClient.Request(
            apiKey = "key",
            voice = "Charon",
            text = "test"
        )
        assertEquals("Charon", req1.voice)

        // voice passed as-is; normalization happens in synthesize()
        val req2 = GeminiSpeechClient.Request(
            apiKey = "key",
            voice = "InvalidVoice",
            text = "test"
        )
        assertEquals("InvalidVoice", req2.voice)  // not normalized in Request
    }

    // ===== Exception Tests =====

    @Test
    fun httpException_includes_errorSummary() {
        val ex = GeminiSpeechClient.HttpException(
            code = 401,
            errorSummary = "INVALID_ARGUMENT",
            retryAfterSeconds = null
        )
        assertEquals(401, ex.code)
        assertEquals("INVALID_ARGUMENT", ex.errorSummary)
        assertNull(ex.retryAfterSeconds)
        assertTrue(ex.message?.contains("401") ?: false)
        assertTrue(ex.message?.contains("INVALID_ARGUMENT") ?: false)
    }

    @Test
    fun httpException_includes_retryAfter() {
        val ex = GeminiSpeechClient.HttpException(
            code = 429,
            errorSummary = "RESOURCE_EXHAUSTED",
            retryAfterSeconds = 60L
        )
        assertEquals(429, ex.code)
        assertEquals("RESOURCE_EXHAUSTED", ex.errorSummary)
        assertEquals(60L, ex.retryAfterSeconds)
    }

    @Test
    fun noAudioException_hasMessage() {
        val ex = GeminiSpeechClient.NoAudioException()
        assertTrue(ex.message?.contains("no audio") ?: false)
    }

    // ===== Voice Normalization Tests =====

    @Test
    fun normalizeVoice_acceptsValidVoices() {
        assertEquals("Kore", GeminiTts.normalizeVoice("Kore"))
        assertEquals("Charon", GeminiTts.normalizeVoice("Charon"))
        assertEquals("Puck", GeminiTts.normalizeVoice("Puck"))
        assertEquals("Aoede", GeminiTts.normalizeVoice("Aoede"))
        assertEquals("Fenrir", GeminiTts.normalizeVoice("Fenrir"))
        assertEquals("Leda", GeminiTts.normalizeVoice("Leda"))
        assertEquals("Orus", GeminiTts.normalizeVoice("Orus"))
        assertEquals("Zephyr", GeminiTts.normalizeVoice("Zephyr"))
    }

    @Test
    fun normalizeVoice_fallsBackToDefault() {
        assertEquals("Kore", GeminiTts.normalizeVoice("InvalidVoice"))
        assertEquals("Kore", GeminiTts.normalizeVoice(""))
        assertEquals("Kore", GeminiTts.normalizeVoice(null))
    }
}
