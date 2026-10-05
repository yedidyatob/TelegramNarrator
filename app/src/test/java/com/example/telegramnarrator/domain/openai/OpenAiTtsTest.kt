package com.example.telegramnarrator.domain.openai

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
}
