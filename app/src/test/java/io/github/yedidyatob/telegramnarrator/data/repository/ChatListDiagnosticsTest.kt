package io.github.yedidyatob.telegramnarrator.data.repository

import io.github.yedidyatob.telegramnarrator.data.tdlib.TdFixtures.chat
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** #131 diagnostics, to be removed with them. */
class ChatListDiagnosticsTest {
    private val lines = mutableListOf<String>()
    private val diagnostics = ChatListDiagnostics { lines += it }

    @Test
    fun `new chat is logged with ids and flags only, never the title`() {
        diagnostics.update(TdApi.UpdateNewChat(chat(5, title = "Secret title", unread = 3, mainOrder = 9, muteFor = 60)), { null })
        val line = lines.single()
        assertFalse(line.contains("Secret"))
        assertEquals("#131 new 5 unread=3 lastRead=0 mute=60 defMute=false marked=false pos=[main:9] lastMsg=false", line)
    }

    @Test
    fun `updates are logged only for chats with unread messages or not cached yet`() {
        val read = ChatSnapshot.of(chat(1, unread = 0))
        val unread = ChatSnapshot.of(chat(2, unread = 4))
        val cached = mapOf(1L to read, 2L to unread)::get
        diagnostics.update(TdApi.UpdateChatNotificationSettings(1, TdApi.ChatNotificationSettings()), cached)
        diagnostics.update(TdApi.UpdateChatNotificationSettings(2, TdApi.ChatNotificationSettings()), cached)
        diagnostics.update(TdApi.UpdateChatReadInbox(1, 0, 0), cached)
        diagnostics.update(TdApi.UpdateChatReadInbox(1, 0, 2), cached)
        diagnostics.update(TdApi.UpdateChatLastMessage(3, null, emptyArray()), cached)
        assertEquals(
            listOf(
                "#131 notif 2 mute=0 defMute=false",
                "#131 read 1 unread=2 lastRead=0",
                "#131 last 3 msg=false pos=[] uncached",
            ),
            lines,
        )
    }

    @Test
    fun `load summary counts cached, unread and listed chats`() {
        val chats = listOf(chat(1, unread = 0), chat(2, unread = 1, muteFor = 60), chat(3, unread = 2)).map(ChatSnapshot::of)
        diagnostics.loaded("page=1", chats)
        assertEquals("#131 LoadChats page=1 cached=3 unread=2 listed=1", lines.single())
    }

    @Test
    fun `stops after the budget`() {
        repeat(ChatListDiagnostics.BUDGET + 10) { diagnostics.log("x") }
        assertEquals(ChatListDiagnostics.BUDGET + 1, lines.size)
        assertTrue(lines.last().contains("budget"))
    }
}
