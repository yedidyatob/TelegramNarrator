package io.github.yedidyatob.telegramnarrator.domain.audio

/**
 * Pure helpers for the pause / complete interaction with the read checkpoint.
 *
 * When a message finishes speaking, [currentItem] must be cleared before / as it is marked played.
 * Otherwise a concurrent pause can push the finished message back onto the queue and resume will
 * replay it (unread count / resume-position bugs after pause).
 */
object PlaybackReadProgress {
    /**
     * After [finished] completed speaking, return the [currentItem] value that should remain.
     * If it still refers to [finished], clear it so pause cannot re-queue a completed message.
     */
    fun afterMessageCompleted(currentItem: PlaybackItem?, finished: PlaybackItem.MessageItem): PlaybackItem? {
        val cur = currentItem ?: return null
        if (cur === finished) return null
        if (cur is PlaybackItem.MessageItem &&
            cur.messageId == finished.messageId &&
            cur.chatId == finished.chatId
        ) {
            return null
        }
        return cur
    }

    /**
     * On pause, only the still-in-progress item is pushed back. Null means nothing to re-queue
     * (e.g. the current message already finished and was cleared by [afterMessageCompleted]).
     */
    fun itemToRequeueOnPause(currentItem: PlaybackItem?): PlaybackItem? = currentItem
}
