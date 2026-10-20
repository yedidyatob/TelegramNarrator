package io.github.yedidyatob.telegramnarrator.core.service

import io.github.yedidyatob.telegramnarrator.domain.model.Chat
import io.github.yedidyatob.telegramnarrator.domain.model.Message
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpNextTest {
    private val chats = listOf(Chat(1, "News", 3, photoFileId = 11), Chat(2, "Work", 2), Chat(3, "Family", 5))

    @Test
    fun `from the current chat on, current highlighted, queue indices kept for jumping`() {
        val items = UpNext.items(listOf(1L, 2L, 3L), currentChatId = 2L, chats = chats, fallbackTitle = "Chat")
        assertEquals(listOf(2L, 3L), items.map { it.chatId })
        assertEquals(listOf(1, 2), items.map { it.queueIndex })
        assertEquals(listOf(true, false), items.map { it.isCurrent })
        assertEquals(listOf(2, 5), items.map { it.unreadCount })
        // Jumping to an item reads the same chats as Auto's queue selection
        assertEquals(listOf(3L), NarrationQueue.chatsFrom(NarrationQueue.entries(listOf(1L, 2L, 3L), emptyMap(), "x"), items[1].queueIndex))
    }

    @Test
    fun `before the first chat starts the whole run is shown with photo and title`() {
        val items = UpNext.items(listOf(1L, 3L), currentChatId = null, chats = chats, fallbackTitle = "Chat")
        assertEquals(listOf("News", "Family"), items.map { it.title })
        assertEquals(11, items.first().photoFileId)
        assertTrue(items.first().isCurrent)
    }

    @Test
    fun `later chats read elsewhere drop out, the current one stays with a fallback title`() {
        val items = UpNext.items(listOf(9L, 2L, 8L), currentChatId = 9L, chats = chats, fallbackTitle = "Chat")
        assertEquals(listOf(9L, 2L), items.map { it.chatId })
        assertEquals("Chat", items.first().title)
        assertEquals(0, items.first().unreadCount)
    }

    @Test
    fun `idle run is empty and items never carry message text`() {
        assertTrue(UpNext.items(emptyList(), null, chats, "Chat").isEmpty())
        val withText = listOf(Chat(1, "News", 1, lastMessage = Message(id = 1, chatId = 1, senderName = null, text = "secret", timestamp = 0, isOutgoing = false)))
        val item = UpNext.items(listOf(1L), 1L, withText, "Chat").single()
        assertTrue("secret" !in item.toString())
    }
}
