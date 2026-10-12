package io.github.yedidyatob.telegramnarrator.data.repository

import io.github.yedidyatob.telegramnarrator.data.tdlib.TdFixtures.chat
import io.github.yedidyatob.telegramnarrator.data.tdlib.TdFixtures.position
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatCacheTest {
    private val cache = ChatCache()

    @Test
    fun `snapshots are copies, never the TDLib object`() {
        val td = chat(1, title = "Old")
        cache.apply(TdApi.UpdateNewChat(td))
        td.title = "Mutated elsewhere"
        td.unreadCount = 99
        assertEquals("Old", cache[1]!!.title)
        assertEquals(1, cache[1]!!.unreadCount)
    }

    @Test
    fun `updates of a known chat change the snapshot`() {
        cache.put(chat(1))
        assertEquals(ChatCache.Result.Changed, cache.apply(TdApi.UpdateChatTitle(1, "New")))
        assertEquals(ChatCache.Result.Changed, cache.apply(TdApi.UpdateChatReadInbox(1, 50, 4)))
        assertEquals(ChatCache.Result.Changed, cache.apply(TdApi.UpdateChatUnreadMentionCount(1, 2)))
        val c = cache[1]!!
        assertEquals("New", c.title)
        assertEquals(4, c.unreadCount)
        assertEquals(50, c.lastReadInboxMessageId)
        assertEquals(2, c.unreadMentionCount)
    }

    @Test
    fun `update of an unknown chat asks for a fetch`() {
        assertEquals(ChatCache.Result.Unknown(5), cache.apply(TdApi.UpdateChatTitle(5, "x")))
        assertEquals(null, cache[5])
        assertEquals(ChatCache.Result.Ignored, cache.apply(TdApi.UpdateOption()))
    }

    @Test
    fun `position with order 0 removes the chat from that list`() {
        cache.put(chat(1, mainOrder = 10))
        cache.apply(TdApi.UpdateChatPosition(1, position(TdApi.ChatListArchive(), 7)))
        assertTrue(cache[1]!!.isArchived)
        assertEquals(10, cache[1]!!.mainOrder)
        cache.apply(TdApi.UpdateChatPosition(1, position(TdApi.ChatListMain(), 0)))
        assertEquals(0, cache[1]!!.mainOrder)
        assertTrue(cache[1]!!.isArchived)
        cache.apply(TdApi.UpdateChatLastMessage(1, null, arrayOf(position(TdApi.ChatListArchive(), 0), position(TdApi.ChatListMain(), 3))))
        assertFalse(cache[1]!!.isArchived)
        assertEquals(3, cache[1]!!.mainOrder)
    }

    @Test
    fun `unread list keeps unread, unmuted chats by main order and follows mute changes`() {
        cache.put(chat(1, unread = 2, mainOrder = 5))
        cache.put(chat(2, unread = 1, mainOrder = 9))
        cache.put(chat(3, unread = 0, mainOrder = 7))
        cache.put(chat(4, unread = 3, mainOrder = 8, muteFor = Int.MAX_VALUE))
        assertEquals(listOf(2L, 1L), cache.unreadChats().map { it.id })
        cache.apply(TdApi.UpdateChatNotificationSettings(2, TdApi.ChatNotificationSettings().apply { muteFor = Int.MAX_VALUE }))
        assertEquals(listOf(1L), cache.unreadChats().map { it.id })
    }

    @Test
    fun `clear empties the cache`() {
        cache.put(chat(1))
        cache.clear()
        assertTrue(cache.snapshot.value.isEmpty())
    }
}
