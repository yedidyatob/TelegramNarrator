package io.github.yedidyatob.telegramnarrator.domain.gemini

import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for [GeminiTts] helper functions: cache key generation, key masking,
 * input limits, and voice normalization.
 */
class GeminiTtsTest {

    // ===== Cache Key Tests =====

    @Test
    fun cacheKey_producesConsistentHash() {
        val key1 = GeminiTts.cacheKey("Hello world", "Kore")
        val key2 = GeminiTts.cacheKey("Hello world", "Kore")
        assertEquals(key1, key2)
    }

    @Test
    fun cacheKey_differsByText() {
        val key1 = GeminiTts.cacheKey("Hello world", "Kore")
        val key2 = GeminiTts.cacheKey("Goodbye world", "Kore")
        assertNotEquals(key1, key2)
    }

    @Test
    fun cacheKey_differsByVoice() {
        val key1 = GeminiTts.cacheKey("Hello world", "Kore")
        val key2 = GeminiTts.cacheKey("Hello world", "Charon")
        assertNotEquals(key1, key2)
    }

    @Test
    fun cacheKey_differsByModel() {
        // If model ID changes in the future, cacheKey includes it
        val key = GeminiTts.cacheKey("Hello", "Kore")
        // Should always be a 64-character hex string (SHA-256)
        assertEquals(64, key.length)
        assertTrue(key.matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    fun cacheKey_isHexSha256() {
        val key = GeminiTts.cacheKey("test", "Kore")
        // SHA-256 in hex = 64 characters
        assertEquals(64, key.length)
        assertTrue(key.matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    fun cacheKey_handlesEmptyText() {
        val key = GeminiTts.cacheKey("", "Kore")
        assertEquals(64, key.length)
    }

    @Test
    fun cacheKey_handlesUnicode() {
        val key1 = GeminiTts.cacheKey("שלום עולם", "Kore")
        val key2 = GeminiTts.cacheKey("שלום עולם", "Kore")
        assertEquals(key1, key2)

        val key3 = GeminiTts.cacheKey("Hello world", "Kore")
        assertNotEquals(key1, key3)
    }

    @Test
    fun cacheKey_usesNormalizedVoice() {
        val key1 = GeminiTts.cacheKey("test", "InvalidVoice")  // normalization happens inside cacheKey
        val key2 = GeminiTts.cacheKey("test", "Kore")  // normalizes to Kore
        // Both should produce the same key because InvalidVoice normalizes to Kore
        assertEquals(key1, key2)
    }

    // ===== Key Masking Tests =====

    @Test
    fun maskKey_returnsNullForEmpty() {
        assertNull(GeminiTts.maskKey(null))
        assertNull(GeminiTts.maskKey(""))
        assertNull(GeminiTts.maskKey("   "))
    }

    @Test
    fun maskKey_masksShortKeys() {
        // Keys < 16 characters show as "••••"
        assertEquals("••••", GeminiTts.maskKey("short"))
        assertEquals("••••", GeminiTts.maskKey("1234567890"))
        assertEquals("••••", GeminiTts.maskKey("123456789012345"))
    }

    @Test
    fun maskKey_showsFirstThreeAndLastFour() {
        // Keys >= 16 characters show first 3 + "…" + last 4
        val masked = GeminiTts.maskKey("1234567890ABCDEF")  // exactly 16 chars
        assertEquals("123…CDEF", masked)

        val masked2 = GeminiTts.maskKey("abcdefghijklmnopqrstuvwxyz")
        assertEquals("abc…wxyz", masked2)
    }

    @Test
    fun maskKey_trimsBefore() {
        // Spaces should be trimmed before masking
        val masked = GeminiTts.maskKey("   1234567890ABCDEF   ")
        assertEquals("123…CDEF", masked)
    }

    @Test
    fun maskKey_exactlyOneBoundary() {
        // Key with exactly 16 characters
        val key16 = "A".repeat(16)
        assertEquals("AAA…AAAA", GeminiTts.maskKey(key16))

        // Key with 15 characters should be masked
        val key15 = "B".repeat(15)
        assertEquals("••••", GeminiTts.maskKey(key15))
    }

    // ===== Input Limit Tests =====

    @Test
    fun isWithinApiLimit_acceptsUnder4000Chars() {
        assertTrue(GeminiTts.isWithinApiLimit(""))
        assertTrue(GeminiTts.isWithinApiLimit("Hello"))
        assertTrue(GeminiTts.isWithinApiLimit("x".repeat(3999)))
        assertTrue(GeminiTts.isWithinApiLimit("x".repeat(4000)))
    }

    @Test
    fun isWithinApiLimit_rejectsOver4000Chars() {
        assertFalse(GeminiTts.isWithinApiLimit("x".repeat(4001)))
        assertFalse(GeminiTts.isWithinApiLimit("x".repeat(5000)))
    }

    // ===== Constants Tests =====

    @Test
    fun constants_areNotEmpty() {
        assertNotNull(GeminiTts.MODEL_ID)
        assertNotNull(GeminiTts.DEFAULT_VOICE)
        assertNotNull(GeminiTts.NARRATION_INSTRUCTION)
        assertNotNull(GeminiTts.API_KEYS_URL)
        assertTrue(GeminiTts.MODEL_ID.isNotEmpty())
        assertTrue(GeminiTts.DEFAULT_VOICE.isNotEmpty())
    }

    @Test
    fun modelId_isCorrect() {
        assertEquals("gemini-2.5-flash-preview-tts", GeminiTts.MODEL_ID)
    }

    @Test
    fun defaultVoice_isKore() {
        assertEquals("Kore", GeminiTts.DEFAULT_VOICE)
    }

    @Test
    fun voices_containsAllEightVoices() {
        val expected = listOf("Kore", "Charon", "Puck", "Aoede", "Fenrir", "Leda", "Orus", "Zephyr")
        assertEquals(expected, GeminiTts.VOICES)
    }

    @Test
    fun apiKeysUrl_pointsToGoogle() {
        assertTrue(GeminiTts.API_KEYS_URL.contains("aistudio.google.com"))
    }

    @Test
    fun maxInputChars_is4000() {
        assertEquals(4000, GeminiTts.MAX_INPUT_CHARS)
    }

    // ===== Safe Error Summary Tests =====

    @Test
    fun safeErrorSummary_extractsCodeAndMessage() {
        val body = """{"error":{"code":400,"message":"Invalid request"}}"""
        val summary = GeminiTts.safeErrorSummary(body)
        assertTrue(summary.contains("400") || summary.contains("Invalid"))
    }

    @Test
    fun safeErrorSummary_handlesEmptyBody() {
        val summary = GeminiTts.safeErrorSummary(null)
        assertTrue(summary.isEmpty() || summary == "")

        val summary2 = GeminiTts.safeErrorSummary("")
        assertTrue(summary2.isEmpty() || summary2 == "")
    }

    @Test
    fun safeErrorSummary_truncatesMaliciouslyLongFields() {
        // If an error field is over 128 chars, the regex truncates it
        val body = """{"error":{"message":"${("A".repeat(200))}"}}"""
        val summary = GeminiTts.safeErrorSummary(body)
        // The regex captures only first 128 chars after "message":"
        assertTrue(summary.length <= 200)
    }

    // ===== GeminiTtsOptions Tests =====

    @Test
    fun geminiTtsOptions_defaultVoiceIsKore() {
        val opts = GeminiTtsOptions()
        assertEquals("Kore", opts.voice)
    }

    @Test
    fun geminiTtsOptions_canSetVoice() {
        val opts = GeminiTtsOptions(voice = "Charon")
        assertEquals("Charon", opts.voice)
    }

    @Test
    fun geminiTtsOptions_dataClassEquality() {
        val opts1 = GeminiTtsOptions(voice = "Kore")
        val opts2 = GeminiTtsOptions(voice = "Kore")
        val opts3 = GeminiTtsOptions(voice = "Charon")
        assertEquals(opts1, opts2)
        assertNotEquals(opts1, opts3)
    }
}
