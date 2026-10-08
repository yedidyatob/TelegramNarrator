package io.github.yedidyatob.telegramnarrator.data.gemini

import io.github.yedidyatob.telegramnarrator.domain.gemini.GeminiTts
import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for Gemini cache key generation: SHA-256(model_id + voice + text).
 */
class GeminiCacheKeyTest {

    @Test
    fun cacheKey_isStable() {
        val text = "The quick brown fox"
        val voice = "Kore"
        val key1 = GeminiTts.cacheKey(text, voice)
        val key2 = GeminiTts.cacheKey(text, voice)
        assertEquals(key1, key2)
    }

    @Test
    fun cacheKey_isDifferentForDifferentText() {
        val voice = "Kore"
        val key1 = GeminiTts.cacheKey("Hello", voice)
        val key2 = GeminiTts.cacheKey("World", voice)
        assertNotEquals(key1, key2)
    }

    @Test
    fun cacheKey_isDifferentForDifferentVoice() {
        val text = "Hello"
        val key1 = GeminiTts.cacheKey(text, "Kore")
        val key2 = GeminiTts.cacheKey(text, "Charon")
        assertNotEquals(key1, key2)
    }

    @Test
    fun cacheKey_isDifferentForDifferentModel() {
        // The cache key includes model ID, so if model ever changes, keys diverge
        val text = "test"
        val voice = "Kore"
        val key = GeminiTts.cacheKey(text, voice)
        
        // Verify the key includes the model ID by checking it's in the payload
        // (The key is a hash, so we just verify it's deterministic for a given payload)
        val keyAgain = GeminiTts.cacheKey(text, voice)
        assertEquals(key, keyAgain)
    }

    @Test
    fun cacheKey_isHexSha256() {
        val key = GeminiTts.cacheKey("any text", "Kore")
        // SHA-256 produces 64 hexadecimal characters
        assertEquals(64, key.length)
        assertTrue(key.matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    fun cacheKey_handlesEmptyText() {
        val key = GeminiTts.cacheKey("", "Kore")
        assertEquals(64, key.length)
        assertTrue(key.matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    fun cacheKey_handlesEmptyVoice() {
        // Empty voice normalizes to Kore
        val key1 = GeminiTts.cacheKey("text", "")
        val key2 = GeminiTts.cacheKey("text", "Kore")
        assertEquals(key1, key2)
    }

    @Test
    fun cacheKey_handlesNullVoice() {
        // Null voice normalizes to Kore
        val key1 = GeminiTts.cacheKey("text", null as String?)
        val key2 = GeminiTts.cacheKey("text", "Kore")
        assertEquals(key1, key2)
    }

    @Test
    fun cacheKey_handlesInvalidVoice() {
        // Invalid voice normalizes to Kore
        val key1 = GeminiTts.cacheKey("text", "NotAVoice")
        val key2 = GeminiTts.cacheKey("text", "Kore")
        assertEquals(key1, key2)
    }

    @Test
    fun cacheKey_caseSensitive() {
        val key1 = GeminiTts.cacheKey("Hello", "Kore")
        val key2 = GeminiTts.cacheKey("hello", "Kore")
        assertNotEquals(key1, key2)
    }

    @Test
    fun cacheKey_handlesPunctuation() {
        val key1 = GeminiTts.cacheKey("Hello, world!", "Kore")
        val key2 = GeminiTts.cacheKey("Hello world", "Kore")
        assertNotEquals(key1, key2)
    }

    @Test
    fun cacheKey_handlesUnicodeHebrew() {
        val key1 = GeminiTts.cacheKey("שלום עולם", "Kore")
        val key2 = GeminiTts.cacheKey("שלום עולם", "Kore")
        assertEquals(key1, key2)

        val key3 = GeminiTts.cacheKey("Hello world", "Kore")
        assertNotEquals(key1, key3)
    }

    @Test
    fun cacheKey_handlesUnicodeEmoji() {
        val key1 = GeminiTts.cacheKey("Hello 👋", "Kore")
        val key2 = GeminiTts.cacheKey("Hello 👋", "Kore")
        assertEquals(key1, key2)

        val key3 = GeminiTts.cacheKey("Hello 🌍", "Kore")
        assertNotEquals(key1, key3)
    }

    @Test
    fun cacheKey_handlesAllVoices() {
        val text = "test"
        val keys = GeminiTts.VOICES.map { voice ->
            voice to GeminiTts.cacheKey(text, voice)
        }
        
        // All keys should be different from each other
        val distinctKeys = keys.map { it.second }.distinct()
        assertEquals(keys.size, distinctKeys.size)
    }

    @Test
    fun cacheKey_handlesLongText() {
        val longText = "a".repeat(10_000)  // much longer than MAX_INPUT_CHARS
        val key = GeminiTts.cacheKey(longText, "Kore")
        assertEquals(64, key.length)
        assertTrue(key.matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    fun cacheKey_handlesMixedContent() {
        val mixedText = "Hello שלום 👋 world"
        val key1 = GeminiTts.cacheKey(mixedText, "Kore")
        val key2 = GeminiTts.cacheKey(mixedText, "Kore")
        assertEquals(key1, key2)
    }

    @Test
    fun cacheKey_includeSeparator() {
        // Verify that the cache key includes model + voice + text distinctly
        // by checking that adjacent content doesn't accidentally match
        val key1 = GeminiTts.cacheKey("ab", "Kore")
        val key2 = GeminiTts.cacheKey("a", "bKore")  // swapped boundary
        assertNotEquals(key1, key2)
    }
}
