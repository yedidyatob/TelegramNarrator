package com.example.telegramnarrator.domain.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceNotePlaybackTest {

    @Test
    fun `downloadable path is played without a TTS label`() {
        val outcome = VoiceNotePlayback.afterDownload("/data/voice.ogg")
        assertEquals(VoiceNotePlayback.Outcome.Play("/data/voice.ogg"), outcome)
    }

    @Test
    fun `missing path skips silently like a photo`() {
        assertEquals(VoiceNotePlayback.Outcome.SkipSilently, VoiceNotePlayback.afterDownload(null))
    }

    @Test
    fun `voice note with a file id is not skipped by MessageSpeechBody even without caption`() {
        assertTrue(
            !MessageSpeechBody.shouldSkipSilently(
                "",
                com.example.telegramnarrator.domain.model.MessageContentType.VOICE_NOTE,
                voiceNoteFileId = 7
            )
        )
    }
}
