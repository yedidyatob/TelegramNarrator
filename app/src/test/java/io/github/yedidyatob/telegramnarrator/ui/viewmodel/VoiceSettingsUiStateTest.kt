package io.github.yedidyatob.telegramnarrator.ui.viewmodel

import io.github.yedidyatob.telegramnarrator.domain.edge.EdgeTtsOptions
import io.github.yedidyatob.telegramnarrator.domain.edge.EdgeVoiceGender
import io.github.yedidyatob.telegramnarrator.domain.gemini.GeminiTts
import io.github.yedidyatob.telegramnarrator.domain.gemini.GeminiTtsOptions
import io.github.yedidyatob.telegramnarrator.domain.tts.SpeechProvider
import io.github.yedidyatob.telegramnarrator.domain.tts.VoiceSettingsSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceSettingsUiStateTest {

    private val rawKey = "AI-proj-AbCdEfGhIjKlMnOpQrStUvWxYz0123456789secretWXYZ"

    @Test
    fun `raw api key never appears in ui state`() {
        val gemini = GeminiVoiceUiState.from(GeminiTtsOptions(), rawKey)
        val state = TtsSettingsUiState(provider = SpeechProvider.GEMINI, gemini = gemini)
        assertTrue(gemini.hasKey)
        assertEquals("AI-…WXYZ", gemini.keyHint)
        // Neither the whole key nor its secret middle is reachable from the state
        val dump = state.toString()
        assertFalse(dump.contains(rawKey))
        assertFalse(dump.contains(rawKey.substring(3, rawKey.length - 4)))
        assertFalse(dump.contains("secret"))
    }

    @Test
    fun `no key gives no hint`() {
        val none = GeminiVoiceUiState.from(GeminiTtsOptions(), null)
        assertFalse(none.hasKey)
        assertNull(none.keyHint)
        assertFalse(GeminiVoiceUiState.from(GeminiTtsOptions(), "   ").hasKey)
    }

    @Test
    fun `short keys are fully masked`() {
        val short = "ai-123456789"
        val state = GeminiVoiceUiState.from(GeminiTtsOptions(), short)
        assertTrue(state.hasKey)
        assertEquals("••••", state.keyHint)
        assertFalse(state.toString().contains("123"))
    }

    @Test
    fun `mask never contains the key for any length`() {
        for (length in 1..80) {
            val key = (1..length).joinToString("") { ('a' + (it % 26)).toString() }
            val masked = GeminiTts.maskKey(key)!!
            if (length > 1) assertFalse("length $length", masked.contains(key))
            assertTrue(masked.length <= 8)
        }
    }

    @Test
    fun `gemini options are normalized`() {
        val state = GeminiVoiceUiState.from(GeminiTtsOptions(voice = "nope"), null)
        assertEquals(GeminiTts.DEFAULT_VOICE, state.voice)
    }

    @Test
    fun `edge ui state mirrors gender and custom voice`() {
        val edge = EdgeVoiceUiState.from(EdgeTtsOptions(EdgeVoiceGender.FEMALE, "en-GB-SoniaNeural"))
        assertEquals(EdgeVoiceGender.FEMALE, edge.gender)
        assertEquals("en-GB-SoniaNeural", edge.customVoice)
        assertFalse(edge.testing)
    }

    @Test
    fun `ui state sections follow the provider`() {
        assertEquals(
            listOf(VoiceSettingsSection.ENGINE, VoiceSettingsSection.EDGE_VOICE, VoiceSettingsSection.PLAYBACK),
            TtsSettingsUiState(provider = SpeechProvider.EDGE).sections
        )
        assertEquals(VoiceSettingsSection.SYSTEM_VOICES, TtsSettingsUiState(provider = SpeechProvider.SYSTEM).sections[1])
        // New installs start on Edge
        assertEquals(VoiceSettingsSection.EDGE_VOICE, TtsSettingsUiState().sections[1])
    }
}
