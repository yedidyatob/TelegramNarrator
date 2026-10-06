package io.github.yedidyatob.telegramnarrator.domain.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnreadChatFilterTest {

    @Test
    fun `unmuted unread chats are included`() {
        assertTrue(UnreadChatFilter.shouldIncludeInUnreadList(3, muteFor = 0))
        assertTrue(UnreadChatFilter.shouldIncludeInUnreadList(1, muteFor = null))
    }

    @Test
    fun `muted chats are excluded even when unread`() {
        assertFalse(UnreadChatFilter.shouldIncludeInUnreadList(5, muteFor = 60))
        assertFalse(UnreadChatFilter.shouldIncludeInUnreadList(1, muteFor = Int.MAX_VALUE))
        assertTrue(UnreadChatFilter.isMuted(1))
        assertFalse(UnreadChatFilter.isMuted(0))
        assertFalse(UnreadChatFilter.isMuted(null))
    }

    @Test
    fun `zero unread chats are excluded`() {
        assertFalse(UnreadChatFilter.shouldIncludeInUnreadList(0, muteFor = 0))
        assertFalse(UnreadChatFilter.shouldIncludeInUnreadList(0, muteFor = 10))
    }
}
