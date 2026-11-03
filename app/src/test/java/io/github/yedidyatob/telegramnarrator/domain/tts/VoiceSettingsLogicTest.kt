package io.github.yedidyatob.telegramnarrator.domain.tts

import io.github.yedidyatob.telegramnarrator.domain.tts.VoiceSettingsSection.ENGINE
import io.github.yedidyatob.telegramnarrator.domain.tts.VoiceSettingsSection.GEMINI_VOICE
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
        assertEquals(listOf(ENGINE, GEMINI_VOICE, PLAYBACK), VoiceSettingsLogic.visibleSections(SpeechProvider.GEMINI))
    }

    @Test
    fun `gemini never shows the system engine and voice pickers`() {
        assertFalse(SYSTEM_VOICES in VoiceSettingsLogic.visibleSections(SpeechProvider.GEMINI))
    }

    @Test
    fun `exactly one engine section is visible and engine plus playback are always visible`() {
        val engineSections = setOf(SYSTEM_VOICES, GEMINI_VOICE)
        for (provider in SpeechProvider.values()) {
            val sections = VoiceSettingsLogic.visibleSections(provider)
            assertEquals(1, sections.count { it in engineSections })
            assertEquals(ENGINE, sections.first())
            assertEquals(PLAYBACK, sections.last())
        }
    }

    @Test
    fun `system test voice needs a ready engine and no reading`() {
        assertTrue(VoiceSettingsLogic.canTestSystem(isPlaying = false, systemTtsReady = true))
        assertFalse(VoiceSettingsLogic.canTestSystem(isPlaying = true, systemTtsReady = true))
        assertFalse(VoiceSettingsLogic.canTestSystem(isPlaying = false, systemTtsReady = false))
        // Disabled while the sample is still waiting for the engine to start speaking
        assertFalse(VoiceSettingsLogic.canTestSystem(isPlaying = false, systemTtsReady = true, testing = true))
    }

    @Test
    fun `gemini test voice requires a saved key`() {
        assertTrue(VoiceSettingsLogic.canTestGemini(isPlaying = false, testing = false, hasKey = true))
        assertFalse(VoiceSettingsLogic.canTestGemini(isPlaying = false, testing = false, hasKey = false))
        assertFalse(VoiceSettingsLogic.canTestGemini(isPlaying = true, testing = false, hasKey = true))
        assertFalse(VoiceSettingsLogic.canTestGemini(isPlaying = false, testing = true, hasKey = true))
    }
}
