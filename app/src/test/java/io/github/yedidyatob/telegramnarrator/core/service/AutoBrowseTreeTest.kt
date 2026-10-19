package io.github.yedidyatob.telegramnarrator.core.service

import io.github.yedidyatob.telegramnarrator.domain.model.Chat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoBrowseTreeTest {
    // Home's unread list as the repository gives it: already filtered (unread, not muted) and ordered
    private val chats = listOf(Chat(1, "News", 3), Chat(3, "Work", 2))

    @Test
    fun `root is play-all first, then exactly Home's unread list, in order`() {
        val nodes = AutoBrowseTree.rootChildren(chats, "Play all")
        assertEquals(listOf(AutoBrowseTree.PLAY_ALL_ID, "chat:1", "chat:3"), nodes.map { it.mediaId })
        assertEquals(listOf(null, 1L, 3L), nodes.map { it.chatId })
        assertEquals("Play all", nodes.first().title)
        assertEquals(5, nodes.first().unreadCount)
        assertTrue(nodes.all { it.playable })
        assertEquals("Work", nodes.last().title)
    }

    @Test
    fun `nothing unread gives an empty root`() {
        assertTrue(AutoBrowseTree.rootChildren(emptyList(), "Play all").isEmpty())
    }

    @Test
    fun `long lists are not capped, so no Home chat goes missing`() {
        val many = (1L..120L).map { Chat(it, "c$it", 1) }
        val nodes = AutoBrowseTree.rootChildren(many, "Play all")
        assertEquals(121, nodes.size)
        // Every page of Auto's paged requests together covers the list exactly once
        val paged = (0 until 6).flatMap { page -> nodes.slice(AutoBrowseTree.pageRange(nodes.size, page, 25)) }
        assertEquals(nodes, paged)
    }

    @Test
    fun `playing a node resolves to its chats, unknown ids are rejected`() {
        assertEquals(listOf(1L, 3L), AutoBrowseTree.chatsToPlay(AutoBrowseTree.PLAY_ALL_ID, chats))
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
        assertEquals(listOf(1L, 3L), AutoBrowseTree.chatsForSearch("family", chats))
    }

    @Test
    fun `signed out shows a single non-playable sign-in item`() {
        val nodes = AutoBrowseTree.rootChildren(AutoBrowseTree.Library.SignedOut, "Sign in on your phone", "Play all")
        assertEquals(listOf(AutoBrowseTree.SIGN_IN_ID), nodes.map { it.mediaId })
        assertEquals("Sign in on your phone", nodes.single().title)
        assertTrue(!nodes.single().playable)
        assertNull(nodes.single().chatId)
        assertNull(AutoBrowseTree.chatsToPlay(AutoBrowseTree.SIGN_IN_ID, chats))
    }

    @Test
    fun `signed in shows the unread chats`() {
        val nodes = AutoBrowseTree.rootChildren(AutoBrowseTree.Library.Ready(chats), "Sign in", "Play all")
        assertEquals(listOf(AutoBrowseTree.PLAY_ALL_ID, "chat:1", "chat:3"), nodes.map { it.mediaId })
    }

    @Test
    fun `unpaged requests from a legacy browser return everything without overflow`() {
        assertEquals(0 until 5, AutoBrowseTree.pageRange(5, 0, Int.MAX_VALUE))
        assertEquals(5 until 5, AutoBrowseTree.pageRange(5, 1, Int.MAX_VALUE))
        assertEquals(2 until 4, AutoBrowseTree.pageRange(5, 1, 2))
        assertEquals(4 until 5, AutoBrowseTree.pageRange(5, 2, 2))
        assertEquals(0 until 5, AutoBrowseTree.pageRange(5, -1, 0))
    }

    @Test
    fun `log ids never contain titles or free text`() {
        assertEquals("root", AutoBrowseTree.logId("root"))
        assertEquals("chat:42", AutoBrowseTree.logId("chat:42"))
        assertEquals("<other:4>", AutoBrowseTree.logId("Dana"))
        assertEquals("Ready(unread=2)", AutoBrowseTree.Library.Ready(chats).logState())
    }
}
