package io.github.yedidyatob.telegramnarrator.domain.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioQueueTest {

    private fun message(chatId: Long, id: Long) = PlaybackItem.MessageItem("sender", "text $id", id, chatId)

    private fun chat(id: Long, vararg messageIds: Long): List<PlaybackItem> =
        listOf<PlaybackItem>(PlaybackItem.Intro("chat $id", id)) +
            messageIds.map { message(id, it) } +
            listOf(PlaybackItem.Silence(1000))

    @Test
    fun `items come out in insertion order`() {
        val queue = AudioQueue()
        val items = chat(1, 10, 11)
        queue.addAll(items)
        val out = generateSequence { queue.next() }.toList()
        assertEquals(items, out)
        assertTrue(queue.isEmpty())
        assertNull(queue.next())
    }

    @Test
    fun `addFirst puts an item back to the front`() {
        val queue = AudioQueue()
        queue.addAll(chat(1, 10, 11))
        val first = queue.next()!!
        val second = queue.next()!!
        queue.addFirst(second)
        assertEquals(second, queue.peek())
        assertEquals(second, queue.next())
        assertEquals(message(1, 11), queue.next())
        assertEquals(PlaybackItem.Intro("chat 1", 1L), first)
    }

    @Test
    fun `skipToNextChat drops the rest of the current chat`() {
        val queue = AudioQueue()
        queue.addAll(chat(1, 10, 11))
        queue.addAll(chat(2, 20))
        queue.add(PlaybackItem.Outro)

        queue.next() // Intro of chat 1 is playing
        queue.skipToNextChat()

        assertEquals(PlaybackItem.Intro("chat 2", 2L), queue.next())
        assertEquals(message(2, 20), queue.next())
        assertEquals(PlaybackItem.Silence(1000), queue.next())
        assertEquals(PlaybackItem.Outro, queue.next())
    }

    @Test
    fun `skipToNextChat in the last chat leaves nothing but the end`() {
        val queue = AudioQueue()
        queue.addAll(chat(1, 10))
        queue.add(PlaybackItem.Outro)
        queue.next()
        queue.skipToNextChat()
        assertTrue(queue.isEmpty())
    }

    @Test
    fun `message items know their chat`() {
        val item = message(5, 7)
        assertEquals(5L, item.chatId)
        assertEquals(7L, item.messageId)
    }

    @Test
    fun `dropped and silent flags default to false`() {
        assertEquals(false, message(1, 2).dropped)
        assertEquals(false, PlaybackItem.Intro("chat", 1L).silent)
        assertEquals(1L, PlaybackItem.Intro("chat", 1L).chatId)
        assertEquals(true, PlaybackItem.MessageItem("s", "", 2, 1, dropped = true).dropped)
    }
}
