package com.example.telegramnarrator.domain.audio

import com.example.telegramnarrator.domain.model.MessageContentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageSpeechBodyTest {

    @Test
    fun `caption or text is spoken regardless of content type`() {
        assertEquals("hello", MessageSpeechBody.resolve("hello", MessageContentType.PHOTO))
        assertEquals("כותרת", MessageSpeechBody.resolve("כותרת", MessageContentType.VIDEO))
        assertEquals("note", MessageSpeechBody.resolve("note", MessageContentType.TEXT))
    }

    @Test
    fun `media without caption is not spoken as a type placeholder`() {
        for (type in listOf(
            MessageContentType.PHOTO,
            MessageContentType.VIDEO,
            MessageContentType.VIDEO_NOTE,
            MessageContentType.STICKER,
            MessageContentType.ANIMATION,
            MessageContentType.AUDIO,
            MessageContentType.DOCUMENT,
            MessageContentType.UNSUPPORTED,
            MessageContentType.TEXT,
            MessageContentType.VOICE_NOTE
        )) {
            assertNull("expected skip for $type", MessageSpeechBody.resolve("", type))
            assertNull(MessageSpeechBody.resolve("   ", type))
        }
    }

    @Test
    fun `shouldSkipSilently skips empty media but not voice notes with a file`() {
        assertTrue(MessageSpeechBody.shouldSkipSilently("", MessageContentType.PHOTO, voiceNoteFileId = null))
        assertTrue(MessageSpeechBody.shouldSkipSilently("", MessageContentType.VIDEO, voiceNoteFileId = null))
        assertTrue(MessageSpeechBody.shouldSkipSilently("", MessageContentType.STICKER, voiceNoteFileId = null))
        assertFalse(MessageSpeechBody.shouldSkipSilently("", MessageContentType.VOICE_NOTE, voiceNoteFileId = 42))
        assertFalse(MessageSpeechBody.shouldSkipSilently("caption", MessageContentType.PHOTO, voiceNoteFileId = null))
    }

    @Test
    fun `skipped media ids batch with the next spoken text in ReadCheckpointer`() {
        // Simulates: pass photo 10, photo 11 silently (mark played), then finish speaking text 12
        val c = ReadCheckpointer(maxPending = 3, maxDelayMs = 10_000)
        c.messagePlayed(1, 10, nowMs = 0)
        c.messagePlayed(1, 11, nowMs = 10)
        val due = c.messagePlayed(1, 12, nowMs = 20)
        assertEquals(listOf(ReadCheckpointer.Batch(1, listOf(10, 11, 12))), due)
    }
}
