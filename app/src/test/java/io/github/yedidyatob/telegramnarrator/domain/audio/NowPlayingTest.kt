package io.github.yedidyatob.telegramnarrator.domain.audio

import io.github.yedidyatob.telegramnarrator.domain.model.MessageContentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NowPlayingTest {

    private fun playing(chatTitle: String, sender: String?) = NowPlaying(
        chatId = 1,
        chatTitle = chatTitle,
        content = NowPlaying.Content.Message(1, sender, "hi", MessageContentType.TEXT, isVoiceNote = false)
    )

    @Test
    fun `group senders are named`() {
        assertEquals("Dana", playing("Family", "Dana").distinctSender)
    }

    @Test
    fun `channel or private chat sender is the chat itself`() {
        assertNull(playing("Tech Daily", "Tech Daily").distinctSender)
        assertNull(playing("Mom ", "Mom").distinctSender)
        assertNull(playing("Mom", null).distinctSender)
        assertNull(playing("Mom", " ").distinctSender)
    }

    @Test
    fun `no sender outside messages`() {
        assertNull(NowPlaying(1, "News", content = NowPlaying.Content.ChatOpening).distinctSender)
        assertNull(NowPlaying(1, "News", content = NowPlaying.Content.Sponsored).distinctSender)
    }
}
