package com.example.telegramnarrator.data.repository

import kotlinx.coroutines.delay

/**
 * Collects a chat's unread incoming messages from a `GetChatHistory`-style source.
 * Pure logic (no TDLib types) so it can be unit tested.
 */
object UnreadHistoryPager {
    // TDLib returns at most 100 messages per GetChatHistory request
    const val HISTORY_PAGE_SIZE = 100
    // How many history requests in a row may return nothing new before giving up
    const val MAX_STALLED_REQUESTS = 3
    const val DEFAULT_RETRY_DELAY_MS = 300L

    /**
     * Returns the **oldest** [maxResults] unread incoming messages of a chat, oldest first, so that
     * a chat with more unread messages than that is read from the first unread message forward and
     * the rest can be read in a later run.
     *
     * The history can only be walked backwards from the newest message, so this pages back
     * until it reaches the read boundary ([lastReadId], the last read incoming message) or has seen
     * all [unreadCount] unread messages, and then keeps the oldest ones.
     *
     * TDLib may return fewer messages than requested (the first request often only returns what is
     * cached locally, even a single message), so it simply keeps paging back from the oldest message
     * received so far, and gives up after [MAX_STALLED_REQUESTS] requests in a row without new messages.
     *
     * @param fetchPage returns the messages newest-first, like `GetChatHistory(chatId, fromMessageId,
     * offset = 0, limit)`: starting at the newest message for fromMessageId == 0, otherwise starting
     * with fromMessageId itself.
     */
    suspend fun <T> collectOldestUnread(
        unreadCount: Int,
        lastReadId: Long,
        maxResults: Int,
        idOf: (T) -> Long,
        isOutgoing: (T) -> Boolean,
        retryDelayMs: Long = DEFAULT_RETRY_DELAY_MS,
        fetchPage: suspend (fromMessageId: Long, limit: Int) -> List<T>
    ): List<T> {
        if (unreadCount <= 0 || maxResults <= 0) return emptyList()

        val unread = ArrayList<T>() // newest first, the order the history is walked in
        val seenIds = HashSet<Long>()
        var fromMessageId = 0L // 0 = start from the newest message
        var stalledRequests = 0
        var reachedReadMessages = false

        while (unread.size < unreadCount && !reachedReadMessages && stalledRequests < MAX_STALLED_REQUESTS) {
            // With a non-zero fromMessageId that message itself is returned again, hence the +1
            val remaining = unreadCount - unread.size + (if (fromMessageId == 0L) 0 else 1)
            val page = fetchPage(fromMessageId, remaining.coerceIn(1, HISTORY_PAGE_SIZE))

            var newMessages = 0
            for (message in page) {
                if (!seenIds.add(idOf(message))) continue
                newMessages++
                if (idOf(message) <= lastReadId) {
                    reachedReadMessages = true
                    break
                }
                // Outgoing messages are never unread
                if (!isOutgoing(message)) unread.add(message)
            }

            if (newMessages == 0) {
                stalledRequests++
                delay(retryDelayMs)
            } else {
                stalledRequests = 0
            }
            page.lastOrNull()?.let { fromMessageId = idOf(it) }
        }

        // The oldest messages are at the end of the newest-first list
        return unread.takeLast(maxResults).reversed()
    }
}
