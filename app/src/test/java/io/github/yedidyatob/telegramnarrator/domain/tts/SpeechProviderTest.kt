package io.github.yedidyatob.telegramnarrator.domain.tts

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeechProviderTest {

    @Test
    fun providers_areSystemAndGemini() {
        assertEquals(listOf(SpeechProvider.SYSTEM, SpeechProvider.GEMINI), SpeechProvider.entries)
        assertEquals("system", SpeechProvider.SYSTEM.id)
        assertEquals("gemini", SpeechProvider.GEMINI.id)
    }

    @Test
    fun default_isSystem() {
        assertEquals(SpeechProvider.SYSTEM, SpeechProvider.DEFAULT)
    }

    @Test
    fun fromId_fallsBackToSystem() {
        assertEquals(SpeechProvider.GEMINI, SpeechProvider.fromId("gemini"))
        for (id in listOf("edge", "unknown", "openai", "", null)) assertEquals(SpeechProvider.SYSTEM, SpeechProvider.fromId(id))
    }

    @Test
    fun fromStored_explicitIds() {
        assertEquals(SpeechProvider.SYSTEM, SpeechProvider.fromStored("system", legacyOpenAiEnabled = false))
        assertEquals(SpeechProvider.GEMINI, SpeechProvider.fromStored("gemini", legacyOpenAiEnabled = false))
        assertEquals(SpeechProvider.SYSTEM, SpeechProvider.fromStored("system", legacyOpenAiEnabled = true))
    }

    @Test
    fun fromStored_removedEdgeEngineFallsBackToSystem() {
        assertEquals(SpeechProvider.SYSTEM, SpeechProvider.fromStored("edge", legacyOpenAiEnabled = false))
    }

    @Test
    fun fromStored_nothingStoredIsSystem() {
        assertEquals(SpeechProvider.SYSTEM, SpeechProvider.fromStored(null, legacyOpenAiEnabled = false))
    }

    @Test
    fun fromStored_legacyOpenAiMigratesToGemini() {
        assertEquals(SpeechProvider.GEMINI, SpeechProvider.fromStored("openai", legacyOpenAiEnabled = false))
        assertEquals(SpeechProvider.GEMINI, SpeechProvider.fromStored(null, legacyOpenAiEnabled = true))
        assertEquals(SpeechProvider.GEMINI, SpeechProvider.fromStored("future_engine", legacyOpenAiEnabled = true))
    }
}
