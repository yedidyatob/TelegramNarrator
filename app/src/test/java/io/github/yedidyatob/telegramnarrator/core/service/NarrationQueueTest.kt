package io.github.yedidyatob.telegramnarrator.core.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NarrationQueueTest {
    private val entries = NarrationQueue.entries(listOf(5L, 7L, 9L), mapOf(5L to "Alice", 9L to ""), "Chat")

    @Test fun `one entry per chat in run order with titles and fallback`() {
        assertEquals(listOf(5L, 7L, 9L), entries.map { it.chatId })
        assertEquals(listOf("Alice", "Chat", "Chat"), entries.map { it.title })
        assertEquals(AutoBrowseTree.chatId(7L), entries[1].mediaId)
    }

    @Test fun `current index follows the playing chat`() {
        assertEquals(0, NarrationQueue.currentIndex(entries, 5L))
        assertEquals(2, NarrationQueue.currentIndex(entries, 9L))
        assertEquals(0, NarrationQueue.currentIndex(entries, 42L))
    }

    @Test fun `selecting a queue item plays from that chat on`() {
        assertEquals(listOf(7L, 9L), NarrationQueue.chatsFrom(entries, 1))
        assertNull(NarrationQueue.chatsFrom(entries, 3))
        assertNull(NarrationQueue.chatsFrom(entries, -1))
    }

    @Test fun `queue selection seeks are recognised, next and previous are not`() {
        assert(NarrationTransport.isQueueSelection(androidx.media3.common.Player.COMMAND_SEEK_TO_MEDIA_ITEM))
        assert(!NarrationTransport.isQueueSelection(androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT))
    }
}
