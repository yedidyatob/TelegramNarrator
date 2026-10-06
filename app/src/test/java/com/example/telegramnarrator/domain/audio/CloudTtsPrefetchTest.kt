package com.example.telegramnarrator.domain.audio

import com.example.telegramnarrator.domain.model.MessageContentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudTtsPrefetchTest {

    private fun msg(
        text: String,
        id: Long = 1L,
        dropped: Boolean = false,
        voiceNoteFileId: Int? = null,
        contentType: MessageContentType = MessageContentType.TEXT
    ) = PlaybackItem.MessageItem("s", text, id, chatId = 1L, voiceNoteFileId, contentType, dropped)

    @Test
    fun `COUNT is a small positive constant`() {
        assertTrue(CloudTtsPrefetch.COUNT in 2..3)
    }

    @Test
    fun `upcomingSpeechTexts returns the next speakable bodies in order`() {
        val items = listOf(
            PlaybackItem.Intro("chat", 1L),
            msg("first", id = 1),
            msg("second", id = 2),
            msg("third", id = 3),
            PlaybackItem.Silence(500),
            msg("fourth", id = 4),
            PlaybackItem.Outro
        )
        assertEquals(listOf("first", "second"), CloudTtsPrefetch.upcomingSpeechTexts(items, limit = 2))
        assertEquals(
            listOf("first", "second", "third"),
            CloudTtsPrefetch.upcomingSpeechTexts(items, limit = 3)
        )
    }

    @Test
    fun `skips dropped voice notes and symbol-only rows`() {
        val items = listOf(
            msg("keep me", id = 1),
            msg("dropped", id = 2, dropped = true),
            msg("voice", id = 3, voiceNoteFileId = 99),
            msg("####", id = 4),
            msg("also keep", id = 5),
            msg("", id = 6, contentType = MessageContentType.PHOTO)
        )
        assertEquals(
            listOf("keep me", "also keep"),
            CloudTtsPrefetch.upcomingSpeechTexts(items, limit = 5)
        )
    }

    @Test
    fun `cleans text the same way playback does`() {
        // MessageCleaner strips URLs; leftover body must match what speakMessage would synthesize
        val items = listOf(msg("hello https://example.com world", id = 1))
        val expected = MessageCleaner.clean("hello https://example.com world")
        assertEquals(listOf(expected), CloudTtsPrefetch.upcomingSpeechTexts(items, limit = 1))
    }

    @Test
    fun `empty or zero limit yields nothing`() {
        assertEquals(emptyList<String>(), CloudTtsPrefetch.upcomingSpeechTexts(listOf(msg("a")), limit = 0))
        assertEquals(emptyList<String>(), CloudTtsPrefetch.upcomingSpeechTexts(emptyList(), limit = 2))
    }

    @Test
    fun `AudioQueue snapshot is used without consuming the queue`() {
        val queue = AudioQueue()
        queue.add(PlaybackItem.Intro("c", 1L))
        queue.add(msg("one", id = 1))
        queue.add(msg("two", id = 2))
        val texts = CloudTtsPrefetch.upcomingSpeechTexts(queue.snapshot(), limit = 2)
        assertEquals(listOf("one", "two"), texts)
        assertEquals(PlaybackItem.Intro("c", 1L), queue.peek())
        assertEquals(PlaybackItem.Intro("c", 1L), queue.next())
        assertEquals(msg("one", id = 1), queue.next())
    }

    @Test
    fun `spoken chat titles are prefetched like messages, the intro ding is not`() {
        val items = listOf(
            msg("last of chat 1", id = 1),
            PlaybackItem.Silence(1000),
            PlaybackItem.Intro("🔴 חדשות", 2L),
            PlaybackItem.ChatTitle("חדשות", 2L, "🔴 חדשות"),
            msg("first of chat 2", id = 2)
        )
        assertEquals(
            listOf("last of chat 1", "חדשות", "first of chat 2"),
            CloudTtsPrefetch.upcomingSpeechTexts(items, limit = 3)
        )
        assertEquals("חדשות", CloudTtsPrefetch.speechTextFor(PlaybackItem.ChatTitle("חדשות", 2L)))
        assertEquals(null, CloudTtsPrefetch.speechTextFor(PlaybackItem.Intro("חדשות", 2L)))
    }
}
