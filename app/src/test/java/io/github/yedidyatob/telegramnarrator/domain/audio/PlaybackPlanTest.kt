package io.github.yedidyatob.telegramnarrator.domain.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackPlanTest {

    private fun msg(chatId: Long, id: Long, text: String = "hello", dropped: Boolean = false, voice: Int? = null) =
        PlaybackItem.MessageItem(sender = "Dana", text = text, messageId = id, chatId = chatId, voiceNoteFileId = voice, dropped = dropped)

    private fun chat(chatId: Long, silent: Boolean = false, vararg messages: PlaybackItem.MessageItem): List<PlaybackItem> =
        listOf(PlaybackItem.Intro("Chat $chatId", chatId, silent), PlaybackItem.ChatTitle("Chat $chatId", chatId)) +
            messages + PlaybackItem.SponsoredSlot(chatId, "Chat $chatId")

    @Test
    fun `chats and messages are numbered in play order`() {
        val plan = PlaybackPlan.from(
            chat(1, false, msg(1, 10), msg(1, 11), msg(1, 12)) + chat(2, false, msg(2, 20)) + PlaybackItem.Outro
        )
        assertEquals(PlaybackPosition(1, 2), plan.chatPosition(1))
        assertEquals(PlaybackPosition(2, 2), plan.chatPosition(2))
        assertEquals(PlaybackPosition(2, 3), plan.messagePosition(1, 11))
        assertEquals(PlaybackPosition(1, 1), plan.messagePosition(2, 20))
        assertEquals(3, plan.messageCount(1))
    }

    @Test
    fun `dropped and silent messages don't count, voice notes do`() {
        val plan = PlaybackPlan.from(
            chat(1, false, msg(1, 10, dropped = true), msg(1, 11, text = ""), msg(1, 12), msg(1, 13, text = "", voice = 7))
        )
        assertNull(plan.messagePosition(1, 10))
        assertNull(plan.messagePosition(1, 11))
        assertEquals(PlaybackPosition(1, 2), plan.messagePosition(1, 12))
        assertEquals(PlaybackPosition(2, 2), plan.messagePosition(1, 13))
        assertEquals(2, plan.messageCount(1))
    }

    @Test
    fun `silent chats are not counted as chats`() {
        val plan = PlaybackPlan.from(
            chat(1, true, msg(1, 10, dropped = true)) + chat(2, false, msg(2, 20)) + chat(3, false, msg(3, 30))
        )
        assertNull(plan.chatPosition(1))
        assertEquals(PlaybackPosition(1, 2), plan.chatPosition(2))
        assertEquals(PlaybackPosition(2, 2), plan.chatPosition(3))
        assertEquals(0, plan.messageCount(1))
    }

    @Test
    fun `empty plan knows nothing`() {
        assertNull(PlaybackPlan.EMPTY.chatPosition(1))
        assertNull(PlaybackPlan.EMPTY.messagePosition(1, 1))
        assertEquals(0, PlaybackPlan.EMPTY.messageCount(1))
    }

    @Test
    fun `position fraction`() {
        assertEquals(0.25f, PlaybackPosition(1, 4).fraction, 0.0001f)
        assertEquals(1f, PlaybackPosition(4, 4).fraction, 0.0001f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `position outside the count is rejected`() {
        PlaybackPosition(5, 4)
    }
}
