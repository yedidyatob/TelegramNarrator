package com.example.telegramnarrator.ui.viewmodel

import com.example.telegramnarrator.domain.edge.EdgeTtsOptions
import com.example.telegramnarrator.domain.edge.EdgeVoiceGender
import com.example.telegramnarrator.domain.openai.OpenAiTts
import com.example.telegramnarrator.domain.openai.OpenAiTtsOptions
import com.example.telegramnarrator.domain.tts.SpeechProvider
import com.example.telegramnarrator.domain.tts.VoiceSettingsSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceSettingsUiStateTest {

    private val rawKey = "sk-proj-AbCdEfGhIjKlMnOpQrStUvWxYz0123456789secretWXYZ"

    @Test
    fun `raw api key never appears in ui state`() {
        val openAi = OpenAiVoiceUiState.from(OpenAiTtsOptions(), rawKey)
        val state = TtsSettingsUiState(provider = SpeechProvider.OPENAI, openAi = openAi)
        assertTrue(openAi.hasKey)
        assertEquals("sk-…WXYZ", openAi.keyHint)
        // Neither the whole key nor its secret middle is reachable from the state
        val dump = state.toString()
        assertFalse(dump.contains(rawKey))
        assertFalse(dump.contains(rawKey.substring(3, rawKey.length - 4)))
        assertFalse(dump.contains("secret"))
    }

    @Test
    fun `no key gives no hint`() {
        val none = OpenAiVoiceUiState.from(OpenAiTtsOptions(), null)
        assertFalse(none.hasKey)
        assertNull(none.keyHint)
        assertFalse(OpenAiVoiceUiState.from(OpenAiTtsOptions(), "   ").hasKey)
    }

    @Test
    fun `short keys are fully masked`() {
        val short = "sk-123456789"
        val state = OpenAiVoiceUiState.from(OpenAiTtsOptions(), short)
        assertTrue(state.hasKey)
        assertEquals("••••", state.keyHint)
        assertFalse(state.toString().contains("123"))
    }

    @Test
    fun `mask never contains the key for any length`() {
        for (length in 1..80) {
            val key = (1..length).joinToString("") { ('a' + (it % 26)).toString() }
            val masked = OpenAiTts.maskKey(key)!!
            if (length > 1) assertFalse("length $length", masked.contains(key))
            assertTrue(masked.length <= 8)
        }
    }

    @Test
    fun `openai options are normalized`() {
        val state = OpenAiVoiceUiState.from(OpenAiTtsOptions(model = "bogus", voice = "nope"), null)
        assertEquals(OpenAiTts.DEFAULT_MODEL, state.model)
        assertEquals(OpenAiTts.DEFAULT_VOICE, state.voice)
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
        assertEquals(VoiceSettingsSection.SYSTEM_VOICES, TtsSettingsUiState().sections[1])
    }
}
