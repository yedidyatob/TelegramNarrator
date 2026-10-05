package com.example.telegramnarrator.domain.audio

import com.example.telegramnarrator.domain.model.Message
import com.example.telegramnarrator.domain.model.MessageContentType

sealed class PlaybackItem {
    // silent: every message of the chat is dropped by the channel rules, so the chat isn't announced
    data class Intro(val chatName: String, val chatId: Long, val silent: Boolean = false) : PlaybackItem()
    data class MessageItem(
        val sender: String?,
        val text: String,
        val messageId: Long,
        val chatId: Long,
        val voiceNoteFileId: Int? = null,
        val contentType: MessageContentType = MessageContentType.TEXT,
        // Dropped by the channel rules: not read, but marked as read when its turn comes
        val dropped: Boolean = false
    ) : PlaybackItem()
    data class Silence(val durationMs: Long) : PlaybackItem()
    object Outro : PlaybackItem()
}

class AudioQueue {
    private val queue = ArrayDeque<PlaybackItem>()
    
    fun add(item: PlaybackItem) {
        queue.add(item)
    }

    fun addFirst(item: PlaybackItem) {
        queue.addFirst(item)
    }
    
    fun addAll(items: List<PlaybackItem>) {
        queue.addAll(items)
    }
    
    fun next(): PlaybackItem? {
        return queue.removeFirstOrNull()
    }
    
    fun peek(): PlaybackItem? = queue.firstOrNull()
    
    fun clear() {
        queue.clear()
    }
    
    fun isEmpty() = queue.isEmpty()

    // Skip to next item (basic next)
    // Managed by caller pulling next()

    // Skip to next chat
    fun skipToNextChat() {
        while (queue.isNotEmpty()) {
            val item = queue.removeFirst()
            if (item is PlaybackItem.Intro) {
                // Found the start of next chat. Add it back to front so it's played next.
                queue.addFirst(item)
                return
            }
        }
    }
}
