package com.example.telegramnarrator.domain.audio

import com.example.telegramnarrator.domain.audio.ReadCheckpointer.Batch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReadCheckpointerTest {

    @Test
    fun `a single played message waits for the delay`() {
        val c = ReadCheckpointer(maxPending = 3, maxDelayMs = 2000)
        assertEquals(emptyList<Batch>(), c.messagePlayed(1, 10, nowMs = 1000))
        assertEquals(3000L, c.nextDueAtMs())

        assertEquals(emptyList<Batch>(), c.flushIfDue(2999))
        assertEquals(listOf(Batch(1, listOf(10))), c.flushIfDue(3000))
        assertNull(c.nextDueAtMs())
        assertEquals(emptyList<Batch>(), c.flushIfDue(10_000))
    }

    @Test
    fun `flushes as soon as the max number of messages is pending`() {
        val c = ReadCheckpointer(maxPending = 3, maxDelayMs = 2000)
        assertEquals(emptyList<Batch>(), c.messagePlayed(1, 10, 0))
        assertEquals(emptyList<Batch>(), c.messagePlayed(1, 11, 100))
        assertEquals(listOf(Batch(1, listOf(10, 11, 12))), c.messagePlayed(1, 12, 200))
        assertNull(c.nextDueAtMs())
        // A new group starts after the flush
        assertEquals(emptyList<Batch>(), c.messagePlayed(1, 13, 300))
        assertEquals(2300L, c.nextDueAtMs())
    }

    @Test
    fun `a late message flushes the group that has waited too long`() {
        val c = ReadCheckpointer(maxPending = 3, maxDelayMs = 2000)
        c.messagePlayed(1, 10, 0)
        // e.g. a long voice note: the next message is played 5 s later
        assertEquals(listOf(Batch(1, listOf(10, 11))), c.messagePlayed(1, 11, 5000))
        assertNull(c.nextDueAtMs())
    }

    @Test
    fun `flush returns everything immediately and only once`() {
        val c = ReadCheckpointer()
        c.messagePlayed(1, 10, 0)
        c.messagePlayed(1, 11, 10)
        assertEquals(listOf(Batch(1, listOf(10, 11))), c.flush())
        assertEquals(emptyList<Batch>(), c.flush())
        assertNull(c.nextDueAtMs())
    }

    @Test
    fun `flush with nothing played returns nothing`() {
        assertEquals(emptyList<Batch>(), ReadCheckpointer().flush())
    }

    @Test
    fun `batches never mix chats`() {
        val c = ReadCheckpointer(maxPending = 3, maxDelayMs = 2000)
        c.messagePlayed(1, 10, 0)
        assertEquals(listOf(Batch(1, listOf(10))), c.messagePlayed(2, 20, 100))
        assertEquals(listOf(Batch(2, listOf(20))), c.flush())
    }

    @Test
    fun `the same message is only reported once per batch`() {
        val c = ReadCheckpointer()
        c.messagePlayed(1, 10, 0)
        c.messagePlayed(1, 10, 50)
        assertEquals(listOf(Batch(1, listOf(10))), c.flush())
    }

    @Test
    fun `interrupted messages are never reported`() {
        // 10 and 11 finish, 12 is interrupted by a pause (never passed to messagePlayed), the pause flushes
        val c = ReadCheckpointer()
        c.messagePlayed(1, 10, 0)
        c.messagePlayed(1, 11, 500)
        assertEquals(listOf(Batch(1, listOf(10, 11))), c.flush())
        // after resuming 12 is replayed and finishes
        c.messagePlayed(1, 12, 9000)
        assertEquals(listOf(Batch(1, listOf(12))), c.flush())
    }

    @Test
    fun `skip message marks the skipped one and flushes, skip chat only flushes`() {
        val c = ReadCheckpointer()
        c.messagePlayed(1, 10, 0)
        // skip message: the skipped message counts as handled, flush immediately
        c.messagePlayed(1, 11, 100)
        assertEquals(listOf(Batch(1, listOf(10, 11))), c.flush())
        // skip chat: the current message (12) and the rest are not added, only pending ones flushed
        c.messagePlayed(1, 13, 200)
        assertEquals(listOf(Batch(1, listOf(13))), c.flush())
    }

    @Test
    fun `flush on pause sends finished messages only`() {
        // Two messages finished; the third is interrupted by pause (never messagePlayed).
        val c = ReadCheckpointer(maxPending = 5, maxDelayMs = 60_000)
        assertEquals(emptyList<Batch>(), c.messagePlayed(7, 100, 0))
        assertEquals(emptyList<Batch>(), c.messagePlayed(7, 101, 50))
        // pause -> flush
        assertEquals(listOf(Batch(7, listOf(100, 101))), c.flush())
        assertNull(c.nextDueAtMs())
    }
}
