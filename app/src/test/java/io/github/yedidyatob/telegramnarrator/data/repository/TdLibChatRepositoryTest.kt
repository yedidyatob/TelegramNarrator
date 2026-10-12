package io.github.yedidyatob.telegramnarrator.data.repository

import io.github.yedidyatob.telegramnarrator.data.tdlib.FakeTdGateway
import io.github.yedidyatob.telegramnarrator.data.tdlib.TdFixtures.chat
import io.github.yedidyatob.telegramnarrator.data.tdlib.TdFixtures.file
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.drinkless.tdlib.TdApi
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.concurrent.thread

class TdLibChatRepositoryTest {
    private val gateway = FakeTdGateway()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var markAsRead = true

    private fun repository(): TdLibChatRepository {
        val repo = TdLibChatRepository(gateway, TdLibUserCache(gateway), { markAsRead }, scope)
        // The repository and the user cache both collect the update stream
        runBlocking { withTimeout(5_000) { while (gateway.bus.subscriberCount < 2) yield() } }
        return repo
    }

    @After
    fun tearDown() = scope.cancel()

    private fun <T> eventually(block: suspend () -> T): T = runBlocking { withTimeout(10_000) { block() } }

    @Test
    fun `no update is lost in a burst of 1000 UpdateNewChat`() {
        val repo = repository()
        thread { (1L..1000L).forEach { gateway.publish(TdApi.UpdateNewChat(chat(it))) } }.join()
        val unread = eventually { repo.getUnreadChats().first { it.size == 1000 } }
        assertEquals(1000L, unread.first().id) // highest main order first
        assertEquals((1L..1000L).toSet(), unread.map { it.id }.toSet())
    }

    @Test
    fun `update of an unknown chat fetches it`() {
        gateway.reply = { f -> if (f is TdApi.GetChat) chat(f.chatId, title = "Fetched") else TdApi.Ok() }
        val repo = repository()
        gateway.publish(TdApi.UpdateChatReadInbox(42, 0, 3))
        val unread = eventually { repo.getUnreadChats().first { it.isNotEmpty() } }
        assertEquals("Fetched", unread.single().title)
    }

    @Test
    fun `ready loads chats and closed clears the cache`() {
        val repo = repository()
        gateway.publish(TdApi.UpdateNewChat(chat(1)))
        eventually { repo.getUnreadChats().first { it.isNotEmpty() } }
        gateway.publish(TdApi.UpdateAuthorizationState(TdApi.AuthorizationStateReady()))
        eventually { while (gateway.sentOf<TdApi.LoadChats>().isEmpty()) yield() }
        gateway.publish(TdApi.UpdateAuthorizationState(TdApi.AuthorizationStateClosed()))
        eventually { repo.getUnreadChats().first { it.isEmpty() } }
        assertNull(runBlocking { gateway.reply = { TdApi.Error(400, "gone") }; repo.getChat(1) })
    }

    @Test
    fun `ready reached before the repository existed still loads chats`() {
        gateway.authorizationState.value = TdApi.AuthorizationStateReady()
        repository()
        eventually { while (gateway.sentOf<TdApi.LoadChats>().isEmpty()) yield() }
    }

    @Test
    fun `voice note path arrives through UpdateFile`() {
        gateway.reply = { f -> if (f is TdApi.DownloadFile) file(7, completed = false, active = true) else TdApi.Ok() }
        val repo = repository()
        val path = runBlocking {
            val result = async(Dispatchers.Default) { repo.getVoiceFilePath(7) }
            withTimeout(5_000) { while (gateway.sentOf<TdApi.DownloadFile>().isEmpty()) yield() }
            gateway.publish(TdApi.UpdateFile(file(8, completed = true, active = false)))
            gateway.publish(TdApi.UpdateFile(file(7, completed = true, active = false)))
            withTimeout(5_000) { result.await() }
        }
        assertEquals("/voice/7.oga", path)
        assertEquals(false, gateway.sentOf<TdApi.DownloadFile>().single().synchronous)
    }

    @Test
    fun `voice note already downloaded returns at once, failed download returns null`() {
        gateway.reply = { f -> if (f is TdApi.DownloadFile) file(f.fileId, completed = f.fileId == 1, active = false) else TdApi.Ok() }
        val repo = repository()
        assertEquals("/voice/1.oga", runBlocking { repo.getVoiceFilePath(1) })
        assertNull(runBlocking { repo.getVoiceFilePath(2) })
    }

    @Test
    fun `mark as read opens the chat, views the messages and refreshes the unread count`() {
        gateway.reply = { f -> if (f is TdApi.GetChat) chat(f.chatId, unread = 0) else TdApi.Ok() }
        val repo = repository()
        runBlocking { repo.markChatAsRead(5, listOf(10L, 11L)) }
        val view = gateway.sentOf<TdApi.ViewMessages>().single()
        assertEquals(5L, view.chatId)
        assertTrue(view.forceRead)
        assertEquals(listOf(10L, 11L), view.messageIds.toList())
        assertEquals(1, gateway.sentOf<TdApi.OpenChat>().size)
        assertEquals(0, runBlocking { repo.getChat(5) }!!.unreadCount)
    }

    @Test
    fun `mark as read turned off sends nothing`() {
        markAsRead = false
        val repo = repository()
        runBlocking { repo.markChatAsRead(5, listOf(10L)) }
        assertTrue(gateway.sent.isEmpty())
    }
}
