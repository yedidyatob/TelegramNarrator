package com.example.telegramnarrator.domain.tts

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeechProviderTest {

    @Test
    fun `system is the default`() {
        assertEquals(SpeechProvider.SYSTEM, SpeechProvider.DEFAULT)
        assertEquals(SpeechProvider.SYSTEM, TtsSettings().provider)
        assertEquals(SpeechProvider.SYSTEM, SpeechProvider.fromStored(null, legacyOpenAiEnabled = false))
    }

    @Test
    fun `ids round trip and unknown ids fall back to system`() {
        SpeechProvider.values().forEach { assertEquals(it, SpeechProvider.fromId(it.id)) }
        assertEquals(SpeechProvider.SYSTEM, SpeechProvider.fromId("bogus"))
        assertEquals(SpeechProvider.SYSTEM, SpeechProvider.fromId(null))
    }

    @Test
    fun `legacy openai flag migrates only when no provider was stored`() {
        assertEquals(SpeechProvider.OPENAI, SpeechProvider.fromStored(null, legacyOpenAiEnabled = true))
        assertEquals(SpeechProvider.EDGE, SpeechProvider.fromStored("edge", legacyOpenAiEnabled = true))
        assertEquals(SpeechProvider.SYSTEM, SpeechProvider.fromStored("system", legacyOpenAiEnabled = true))
    }
}
