package io.github.yedidyatob.telegramnarrator.domain.tts

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeechProviderTest {

    @Test
    fun `edge is the default for new installs`() {
        assertEquals(SpeechProvider.EDGE, SpeechProvider.DEFAULT)
        assertEquals(SpeechProvider.EDGE, TtsSettings().provider)
        assertEquals(SpeechProvider.EDGE, SpeechProvider.fromStored(null, legacyOpenAiEnabled = false, freshInstall = true))
    }

    @Test
    fun `an updated install without a stored choice keeps the system voice it was using`() {
        assertEquals(SpeechProvider.SYSTEM, SpeechProvider.fromStored(null, legacyOpenAiEnabled = false, freshInstall = false))
        assertEquals(SpeechProvider.SYSTEM, SpeechProvider.defaultFor(freshInstall = false))
    }

    @Test
    fun `a stored choice always wins`() {
        SpeechProvider.values().forEach { provider ->
            listOf(true, false).forEach { fresh ->
                assertEquals(provider, SpeechProvider.fromStored(provider.id, legacyOpenAiEnabled = false, freshInstall = fresh))
            }
        }
    }

    @Test
    fun `ids round trip and unknown ids fall back to the offline system voice`() {
        SpeechProvider.values().forEach { assertEquals(it, SpeechProvider.fromId(it.id)) }
        assertEquals(SpeechProvider.SYSTEM, SpeechProvider.fromId("bogus"))
        assertEquals(SpeechProvider.SYSTEM, SpeechProvider.fromId(null))
    }

    @Test
    fun `legacy openai flag migrates only when no provider was stored`() {
        assertEquals(SpeechProvider.OPENAI, SpeechProvider.fromStored(null, legacyOpenAiEnabled = true, freshInstall = false))
        assertEquals(SpeechProvider.OPENAI, SpeechProvider.fromStored(null, legacyOpenAiEnabled = true, freshInstall = true))
        assertEquals(SpeechProvider.EDGE, SpeechProvider.fromStored("edge", legacyOpenAiEnabled = true, freshInstall = false))
        assertEquals(SpeechProvider.SYSTEM, SpeechProvider.fromStored("system", legacyOpenAiEnabled = true, freshInstall = true))
    }
}
