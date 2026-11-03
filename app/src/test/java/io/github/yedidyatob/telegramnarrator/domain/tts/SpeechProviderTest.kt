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
    fun fromId_resolvesKnownIdsAndFallsBackToSystem() {
        assertEquals(SpeechProvider.SYSTEM, SpeechProvider.fromId("system"))
        assertEquals(SpeechProvider.GEMINI, SpeechProvider.fromId("gemini"))
        for (id in listOf("unknown", "", null)) assertEquals(SpeechProvider.SYSTEM, SpeechProvider.fromId(id))
    }
}
