package io.github.yedidyatob.telegramnarrator.domain.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SponsoredSlotPlacementTest {
    private fun msg(chatId: Long, id: Long) = PlaybackItem.MessageItem("s", "text $id", id, chatId)

    @Test
    fun `slot goes after the chat's last message`() {
        val items = ChatTitleSpeech.chatOpening(1L, "News", "News", silent = false) + listOf(msg(1, 10), msg(1, 11))
        val withSlot = SponsoredSlotPlacement.withSlot(items, 1L, "News", silent = false)
        assertEquals(items + PlaybackItem.SponsoredSlot(1L, "News"), withSlot)
    }

    @Test
    fun `silent chats get no slot`() {
        val items = ChatTitleSpeech.chatOpening(1L, "News", "News", silent = true) +
            listOf(msg(1, 10).copy(dropped = true))
        assertEquals(items, SponsoredSlotPlacement.withSlot(items, 1L, "News", silent = true))
    }

    @Test
    fun `slot is played before the next chat's ding and skip-chat skips it`() {
        val queue = AudioQueue()
        queue.addAll(SponsoredSlotPlacement.withSlot(listOf(PlaybackItem.Intro("A", 1L), msg(1, 10)), 1L, "A", false))
        queue.add(PlaybackItem.Silence(1000))
        queue.addAll(listOf(PlaybackItem.Intro("B", 2L), msg(2, 20)))

        assertEquals(PlaybackItem.Intro("A", 1L), queue.next())
        assertEquals(msg(1, 10), queue.next())
        assertEquals(PlaybackItem.SponsoredSlot(1L, "A"), queue.next())
        assertEquals(PlaybackItem.Silence(1000), queue.next())
        assertEquals(PlaybackItem.Intro("B", 2L), queue.next())

        val q2 = AudioQueue()
        q2.addAll(SponsoredSlotPlacement.withSlot(listOf(PlaybackItem.Intro("A", 1L), msg(1, 10)), 1L, "A", false))
        q2.add(PlaybackItem.Intro("B", 2L))
        q2.next()
        q2.skipToNextChat()
        assertEquals(PlaybackItem.Intro("B", 2L), q2.next())
    }

    @Test
    fun `the slot is not sent to cloud TTS prefetch and is not a message`() {
        val slot: PlaybackItem = PlaybackItem.SponsoredSlot(1L, "A")
        assertEquals(null, CloudTtsPrefetch.speechTextFor(slot))
        assertFalse(slot is PlaybackItem.MessageItem)
    }
}
