package io.github.yedidyatob.telegramnarrator.core.service

import io.github.yedidyatob.telegramnarrator.domain.model.Chat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoBrowseTreeTest {
    private val chats = listOf(Chat(1, "News", 3), Chat(2, "Family", 0), Chat(3, "Work", 2))

    @Test
    fun `root lists play all first, then unread chats in order`() {
        val nodes = AutoBrowseTree.rootChildren(chats, "Play all unread")
        assertEquals(listOf("play_all", "chat:1", "chat:3"), nodes.map { it.mediaId })
        assertEquals(5, nodes.first().unreadCount)
        assertEquals("Work", nodes.last().title)
    }

    @Test
    fun `nothing unread gives an empty root`() {
        assertTrue(AutoBrowseTree.rootChildren(listOf(Chat(2, "Family", 0)), "x").isEmpty())
    }

    @Test
    fun `root is capped for driving`() {
        val many = (1L..80L).map { Chat(it, "c$it", 1) }
        assertEquals(AutoBrowseTree.MAX_CHATS + 1, AutoBrowseTree.rootChildren(many, "x").size)
    }

    @Test
    fun `playing a node resolves to its chats, unknown ids are rejected`() {
        assertEquals(listOf(1L, 3L), AutoBrowseTree.chatsToPlay("play_all", chats))
        assertEquals(listOf(3L), AutoBrowseTree.chatsToPlay("chat:3", chats))
        assertNull(AutoBrowseTree.chatsToPlay("chat:abc", chats))
        assertNull(AutoBrowseTree.chatsToPlay("narration", chats))
    }

    @Test
    fun `voice search plays the best matching unread chat, else everything unread`() {
        assertEquals(listOf(3L), AutoBrowseTree.chatsForSearch("work", chats))
        assertEquals(listOf(1L), AutoBrowseTree.chatsForSearch("ne", chats))
        assertEquals(listOf(1L, 3L), AutoBrowseTree.chatsForSearch("", chats))
        assertEquals(listOf(1L, 3L), AutoBrowseTree.chatsForSearch(null, chats))
        // A chat with nothing unread is never chosen
        assertEquals(listOf(1L, 3L), AutoBrowseTree.chatsForSearch("family", chats))
    }

    @Test
    fun `signed out shows a single non-playable sign-in item`() {
        val nodes = AutoBrowseTree.rootChildren(AutoBrowseTree.Library.SignedOut, "Play all", "Sign in on your phone")
        assertEquals(listOf(AutoBrowseTree.SIGN_IN_ID), nodes.map { it.mediaId })
        assertEquals("Sign in on your phone", nodes.single().title)
        assertTrue(!nodes.single().playable)
        assertNull(AutoBrowseTree.chatsToPlay(AutoBrowseTree.SIGN_IN_ID, chats))
    }

    @Test
    fun `signed in shows the unread tree`() {
        val nodes = AutoBrowseTree.rootChildren(AutoBrowseTree.Library.Ready(chats), "Play all", "Sign in")
        assertEquals(listOf("play_all", "chat:1", "chat:3"), nodes.map { it.mediaId })
        assertTrue(nodes.all { it.playable })
    }
}
