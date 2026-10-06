package io.github.yedidyatob.telegramnarrator.domain.openai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiTtsTest {

    @Test
    fun `cache key is stable for same text voice model`() {
        val a = OpenAiTts.cacheKey("שלום עולם", "alloy", "tts-1")
        val b = OpenAiTts.cacheKey("שלום עולם", "alloy", "tts-1")
        assertEquals(64, a.length)
        assertEquals(a, b)
    }

    @Test
    fun `cache key changes when text voice or model changes`() {
        val base = OpenAiTts.cacheKey("hello", "alloy", "tts-1")
        assertNotEquals(base, OpenAiTts.cacheKey("hello!", "alloy", "tts-1"))
        assertNotEquals(base, OpenAiTts.cacheKey("hello", "nova", "tts-1"))
        assertNotEquals(base, OpenAiTts.cacheKey("hello", "alloy", "tts-1-hd"))
    }

    @Test
    fun `normalize falls back to defaults`() {
        assertEquals(OpenAiTts.DEFAULT_MODEL, OpenAiTts.normalizeModel("nope"))
        assertEquals(OpenAiTts.DEFAULT_VOICE, OpenAiTts.normalizeVoice("robot"))
        assertEquals("nova", OpenAiTts.normalizeVoice("nova"))
        assertEquals("tts-1-hd", OpenAiTts.normalizeModel("tts-1-hd"))
    }

    @Test
    fun `api limit rejects very long text`() {
        assertTrue(OpenAiTts.isWithinApiLimit("x".repeat(OpenAiTts.MAX_INPUT_CHARS)))
        assertFalse(OpenAiTts.isWithinApiLimit("x".repeat(OpenAiTts.MAX_INPUT_CHARS + 1)))
    }

    @Test
    fun `error summary keeps only code and type, never the message`() {
        val body = """
            {"error": {"message": "Incorrect API key provided: sk-proj-abc*****wxyz.",
                       "type": "invalid_request_error", "param": null, "code": "invalid_api_key"}}
        """.trimIndent()
        val summary = OpenAiTts.safeErrorSummary(body)
        assertEquals("invalid_request_error/invalid_api_key", summary)
        assertFalse(summary.contains("sk-"))
    }

    @Test
    fun `error summary is empty for missing or non-JSON bodies`() {
        assertEquals("", OpenAiTts.safeErrorSummary(null))
        assertEquals("", OpenAiTts.safeErrorSummary("<html>Bad gateway</html>"))
        assertEquals("", OpenAiTts.safeErrorSummary("""{"error": {"code": null}}"""))
    }

    @Test
    fun `Get an API key link points to the OpenAI API keys page over https`() {
        assertEquals("https://platform.openai.com/api-keys", OpenAiTts.API_KEYS_URL)
    }
}
