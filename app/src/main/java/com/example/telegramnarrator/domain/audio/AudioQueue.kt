package com.example.telegramnarrator.domain.audio

import com.example.telegramnarrator.domain.model.Message

sealed class PlaybackItem {
    data class Intro(val chatName: String) : PlaybackItem()
    data class MessageItem(val sender: String?, val text: String, val messageId: Long, val voiceNoteFileId: Int? = null) : PlaybackItem()
    data class Silence(val durationMs: Long) : PlaybackItem()
    data class MarkAsRead(val chatId: Long) : PlaybackItem()
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
