package io.github.yedidyatob.telegramnarrator.domain.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatPlaybackHistoryTest {

    private fun msg(id: Long, chatId: Long = 1) = PlaybackItem.MessageItem(sender = null, text = "m$id", messageId = id, chatId = chatId)

    @Test
    fun `previous during a message replays the one before, then the current`() {
        val history = ChatPlaybackHistory()
        history.onChatStarted()
        history.onMessageStarted(msg(1))
        history.onMessageStarted(msg(2))
        assertEquals(listOf(msg(1), msg(2)), history.previous(msg(2)))
    }

    @Test
    fun `previous on the chat's first message restarts it`() {
        val history = ChatPlaybackHistory()
        history.onChatStarted()
        history.onMessageStarted(msg(1))
        assertEquals(listOf(msg(1)), history.previous(msg(1)))
    }

    @Test
    fun `previous never crosses into the chat before`() {
        val history = ChatPlaybackHistory()
        history.onMessageStarted(msg(1, chatId = 1))
        history.onChatStarted()
        history.onMessageStarted(msg(5, chatId = 2))
        assertEquals(listOf(msg(5, chatId = 2)), history.previous(msg(5, chatId = 2)))
    }

    @Test
    fun `pressing previous repeatedly walks back one message at a time`() {
        val history = ChatPlaybackHistory()
        listOf(1L, 2L, 3L).forEach { history.onMessageStarted(msg(it)) }
        val first = history.previous(msg(3))
        assertEquals(listOf(msg(2), msg(3)), first)
        // The service replays message 2, which starts again
        history.onMessageStarted(msg(2))
        assertEquals(listOf(msg(1), msg(2)), history.previous(msg(2)))
    }

    @Test
    fun `restarting a message doesn't add it twice`() {
        val history = ChatPlaybackHistory()
        history.onMessageStarted(msg(1))
        history.onMessageStarted(msg(2))
        history.onMessageStarted(msg(2))
        assertEquals(listOf(msg(1), msg(2)), history.previous(msg(2)))
    }

    @Test
    fun `previous from the sponsored slot replays the chat's last message`() {
        val history = ChatPlaybackHistory()
        history.onMessageStarted(msg(1))
        history.onMessageStarted(msg(2))
        val slot = PlaybackItem.SponsoredSlot(1, "Chat")
        assertEquals(listOf(msg(2), slot), history.previous(slot))
    }

    @Test
    fun `paused before the next message (re-queued) goes back to the one that was heard`() {
        val history = ChatPlaybackHistory()
        history.onMessageStarted(msg(1))
        // Message 2 is next in the queue but never started
        assertEquals(listOf(msg(1), msg(2)), history.previous(msg(2)))
    }
}
