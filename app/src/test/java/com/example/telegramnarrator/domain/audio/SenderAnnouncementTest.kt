package com.example.telegramnarrator.domain.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SenderAnnouncementTest {

    @Test
    fun `first message of a chat does not announce the sender`() {
        val d = SenderAnnouncement.decide("Alice", lastSender = null, isFirstMessageInChat = true)
        assertFalse(d.announceSender)
        assertEquals("Alice", d.nextLastSender)
        assertFalse(d.nextIsFirstMessageInChat)
        assertEquals(null, d.previousLastSender)
        assertTrue(d.previousIsFirstMessageInChat)
    }

    @Test
    fun `second message from the same sender is still quiet`() {
        val d = SenderAnnouncement.decide("Alice", lastSender = "Alice", isFirstMessageInChat = false)
        assertFalse(d.announceSender)
        assertEquals("Alice", d.nextLastSender)
    }

    @Test
    fun `new sender after the first message is announced`() {
        val d = SenderAnnouncement.decide("Bob", lastSender = "Alice", isFirstMessageInChat = false)
        assertTrue(d.announceSender)
        assertEquals("Bob", d.nextLastSender)
        assertEquals("Alice", d.previousLastSender)
    }

    @Test
    fun `without the first-message flag a null lastSender announces`() {
        // Safety: if isFirst were forgotten, the old "lastSender == null => announce" behaviour remains
        val d = SenderAnnouncement.decide("Alice", lastSender = null, isFirstMessageInChat = false)
        assertTrue(d.announceSender)
    }

    @Test
    fun `empty sender key still advances the first-message flag`() {
        val d = SenderAnnouncement.decide("", lastSender = null, isFirstMessageInChat = true)
        assertFalse(d.announceSender)
        assertEquals("", d.nextLastSender)
        assertFalse(d.nextIsFirstMessageInChat)
    }
}
