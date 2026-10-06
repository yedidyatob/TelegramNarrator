package io.github.yedidyatob.telegramnarrator.domain.audio

import io.github.yedidyatob.telegramnarrator.domain.model.MessageContentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class PlaybackReadProgressTest {

    private fun msg(id: Long, chatId: Long = 1L) = PlaybackItem.MessageItem(
        sender = "A",
        text = "hi",
        messageId = id,
        chatId = chatId,
        contentType = MessageContentType.TEXT
    )

    @Test
    fun `after completion clears currentItem when it is the finished message`() {
        val item = msg(10)
        assertNull(PlaybackReadProgress.afterMessageCompleted(item, item))
        assertNull(PlaybackReadProgress.afterMessageCompleted(msg(10), msg(10)))
    }

    @Test
    fun `after completion leaves a different current item alone`() {
        val finished = msg(10)
        val next = msg(11)
        assertSame(next, PlaybackReadProgress.afterMessageCompleted(next, finished))
    }

    @Test
    fun `pause requeues only when currentItem is still set`() {
        val item = msg(10)
        assertSame(item, PlaybackReadProgress.itemToRequeueOnPause(item))
        assertNull(PlaybackReadProgress.itemToRequeueOnPause(null))
    }

    @Test
    fun `pause after completed message does not requeue - flush still returns finished ids`() {
        // Simulates: message 10 finishes -> clear current + checkpoint; pause flushes; nothing to requeue.
        val c = ReadCheckpointer()
        val item = msg(10)
        var current: PlaybackItem? = item
        current = PlaybackReadProgress.afterMessageCompleted(current, item)
        c.messagePlayed(item.chatId, item.messageId, nowMs = 0)
        assertNull(current)
        assertNull(PlaybackReadProgress.itemToRequeueOnPause(current))
        assertEquals(listOf(ReadCheckpointer.Batch(1, listOf(10))), c.flush())
    }

    @Test
    fun `pause mid-message requeues in-progress and flushes only earlier finished ones`() {
        val c = ReadCheckpointer()
        c.messagePlayed(1, 10, 0)
        c.messagePlayed(1, 11, 100)
        val inProgress = msg(12)
        val toRequeue = PlaybackReadProgress.itemToRequeueOnPause(inProgress)
        assertSame(inProgress, toRequeue)
        assertEquals(listOf(ReadCheckpointer.Batch(1, listOf(10, 11))), c.flush())
        // in-progress (12) was never messagePlayed, so it stays unread
        assertEquals(emptyList<ReadCheckpointer.Batch>(), c.flush())
    }
}
