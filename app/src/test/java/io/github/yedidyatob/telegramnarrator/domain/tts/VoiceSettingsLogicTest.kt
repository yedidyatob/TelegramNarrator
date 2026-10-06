package io.github.yedidyatob.telegramnarrator.domain.tts

import io.github.yedidyatob.telegramnarrator.domain.tts.VoiceSettingsSection.EDGE_VOICE
import io.github.yedidyatob.telegramnarrator.domain.tts.VoiceSettingsSection.ENGINE
import io.github.yedidyatob.telegramnarrator.domain.tts.VoiceSettingsSection.OPENAI_VOICE
import io.github.yedidyatob.telegramnarrator.domain.tts.VoiceSettingsSection.PLAYBACK
import io.github.yedidyatob.telegramnarrator.domain.tts.VoiceSettingsSection.SYSTEM_VOICES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceSettingsLogicTest {

    @Test
    fun `each engine shows engine picker, only its own section, then playback`() {
        assertEquals(listOf(ENGINE, SYSTEM_VOICES, PLAYBACK), VoiceSettingsLogic.visibleSections(SpeechProvider.SYSTEM))
        assertEquals(listOf(ENGINE, EDGE_VOICE, PLAYBACK), VoiceSettingsLogic.visibleSections(SpeechProvider.EDGE))
        assertEquals(listOf(ENGINE, OPENAI_VOICE, PLAYBACK), VoiceSettingsLogic.visibleSections(SpeechProvider.OPENAI))
    }

    @Test
    fun `edge and openai never show the system engine and voice pickers`() {
        for (provider in listOf(SpeechProvider.EDGE, SpeechProvider.OPENAI)) {
            assertFalse(SYSTEM_VOICES in VoiceSettingsLogic.visibleSections(provider))
        }
    }

    @Test
    fun `exactly one engine section is visible and engine plus playback are always visible`() {
        val engineSections = setOf(SYSTEM_VOICES, EDGE_VOICE, OPENAI_VOICE)
        for (provider in SpeechProvider.values()) {
            val sections = VoiceSettingsLogic.visibleSections(provider)
            assertEquals(1, sections.count { it in engineSections })
            assertEquals(ENGINE, sections.first())
            assertEquals(PLAYBACK, sections.last())
        }
    }

    @Test
    fun `only the system section waits for the system tts engine`() {
        assertTrue(VoiceSettingsLogic.systemSectionLoading(SpeechProvider.SYSTEM, systemTtsReady = false))
        assertFalse(VoiceSettingsLogic.systemSectionLoading(SpeechProvider.SYSTEM, systemTtsReady = true))
        assertFalse(VoiceSettingsLogic.systemSectionLoading(SpeechProvider.EDGE, systemTtsReady = false))
        assertFalse(VoiceSettingsLogic.systemSectionLoading(SpeechProvider.OPENAI, systemTtsReady = false))
    }

    @Test
    fun `system test voice needs a ready engine and no reading`() {
        assertTrue(VoiceSettingsLogic.canTestSystem(isPlaying = false, systemTtsReady = true))
        assertFalse(VoiceSettingsLogic.canTestSystem(isPlaying = true, systemTtsReady = true))
        assertFalse(VoiceSettingsLogic.canTestSystem(isPlaying = false, systemTtsReady = false))
    }

    @Test
    fun `edge test voice is disabled while reading or while a sample is loading`() {
        assertTrue(VoiceSettingsLogic.canTestEdge(isPlaying = false, testing = false))
        assertFalse(VoiceSettingsLogic.canTestEdge(isPlaying = true, testing = false))
        assertFalse(VoiceSettingsLogic.canTestEdge(isPlaying = false, testing = true))
    }

    @Test
    fun `openai test voice requires a saved key`() {
        assertTrue(VoiceSettingsLogic.canTestOpenAi(isPlaying = false, testing = false, hasKey = true))
        assertFalse(VoiceSettingsLogic.canTestOpenAi(isPlaying = false, testing = false, hasKey = false))
        assertFalse(VoiceSettingsLogic.canTestOpenAi(isPlaying = true, testing = false, hasKey = true))
        assertFalse(VoiceSettingsLogic.canTestOpenAi(isPlaying = false, testing = true, hasKey = true))
    }

    @Test
    fun `a custom edge voice disables the male female toggle`() {
        assertTrue(VoiceSettingsLogic.edgeGenderEnabled(isPlaying = false, customVoice = null))
        assertFalse(VoiceSettingsLogic.edgeGenderEnabled(isPlaying = false, customVoice = "en-GB-SoniaNeural"))
        assertFalse(VoiceSettingsLogic.edgeGenderEnabled(isPlaying = true, customVoice = null))
    }
}
