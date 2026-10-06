package io.github.yedidyatob.telegramnarrator.domain.audio

/**
 * The messages of the current chat that have started playing, for "previous message". Previous stays within
 * the chat: on the chat's first message it restarts that message.
 */
class ChatPlaybackHistory {
    private val started = ArrayDeque<PlaybackItem.MessageItem>()

    /** A new chat starts (its ding): previous never goes back into the chat before. */
    @Synchronized
    fun onChatStarted() = started.clear()

    /** [item] starts playing (spoken or voice note). Restarting the same message doesn't add it twice. */
    @Synchronized
    fun onMessageStarted(item: PlaybackItem.MessageItem) {
        if (started.lastOrNull()?.let { sameMessage(it, item) } != true) started.addLast(item)
    }

    /**
     * Items to put back at the front of the queue, in play order, for "previous" while [current] plays (or is
     * paused / re-queued): the message before [current] and then [current] itself. On the chat's first message
     * that is just [current] (restart). [current] may also be the chat title or the sponsored slot, in which
     * case the previous item is the last message that started.
     */
    @Synchronized
    fun previous(current: PlaybackItem?): List<PlaybackItem> {
        if (current is PlaybackItem.MessageItem && started.lastOrNull()?.let { sameMessage(it, current) } == true) {
            started.removeLast()
        }
        val before = started.removeLastOrNull()
        return listOfNotNull(before, current)
    }

    private fun sameMessage(a: PlaybackItem.MessageItem, b: PlaybackItem.MessageItem) =
        a.chatId == b.chatId && a.messageId == b.messageId
}
