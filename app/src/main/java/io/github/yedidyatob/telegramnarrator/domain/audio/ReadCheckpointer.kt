package io.github.yedidyatob.telegramnarrator.domain.audio

/**
 * Collects the ids of messages that were fully played and decides when they should be sent to
 * Telegram as read, so that messages are marked read shortly after they finish playing instead of
 * once per chat. Pure logic: the caller supplies the time and sends the returned batches.
 *
 * - Only messages passed to [messagePlayed] are ever reported; messages that were interrupted
 *   (paused, stopped, skipped away with skip-chat) are simply never added, so they stay unread.
 * - Played messages are held back for a moment and flushed in tiny groups: as soon as [maxPending]
 *   messages are waiting, or the oldest one has waited [maxDelayMs] (see [flushIfDue] / [nextDueAtMs]).
 * - A batch only ever contains messages of one chat; when the chat changes the previous chat's
 *   messages are flushed first.
 * - [flush] returns everything that is waiting right away (call it on pause, stop, skip, destroy).
 */
class ReadCheckpointer(
    private val maxPending: Int = DEFAULT_MAX_PENDING,
    private val maxDelayMs: Long = DEFAULT_MAX_DELAY_MS
) {
    companion object {
        const val DEFAULT_MAX_PENDING = 3
        const val DEFAULT_MAX_DELAY_MS = 2000L
    }

    data class Batch(val chatId: Long, val messageIds: List<Long>)

    private val lock = Any()
    private var pendingChatId = 0L
    private val pendingIds = ArrayList<Long>()
    private var oldestPendingAtMs = 0L

    /** Records that [messageId] finished playing at [nowMs]; returns the batches that are due now. */
    fun messagePlayed(chatId: Long, messageId: Long, nowMs: Long): List<Batch> = synchronized(lock) {
        val due = ArrayList<Batch>()
        if (pendingIds.isNotEmpty() && pendingChatId != chatId) drainInto(due)

        if (pendingIds.isEmpty()) oldestPendingAtMs = nowMs
        pendingChatId = chatId
        if (messageId !in pendingIds) pendingIds.add(messageId)

        if (pendingIds.size >= maxPending || nowMs - oldestPendingAtMs >= maxDelayMs) drainInto(due)
        due
    }

    /** Batches that have waited long enough at [nowMs] (call when the timer from [nextDueAtMs] fires). */
    fun flushIfDue(nowMs: Long): List<Batch> = synchronized(lock) {
        val due = ArrayList<Batch>()
        if (pendingIds.isNotEmpty() && nowMs - oldestPendingAtMs >= maxDelayMs) drainInto(due)
        due
    }

    /** Everything that is waiting, immediately. */
    fun flush(): List<Batch> = synchronized(lock) {
        val due = ArrayList<Batch>()
        drainInto(due)
        due
    }

    /** When the pending messages become due ([flushIfDue]), or null if nothing is waiting. */
    fun nextDueAtMs(): Long? = synchronized(lock) {
        if (pendingIds.isEmpty()) null else oldestPendingAtMs + maxDelayMs
    }

    private fun drainInto(out: MutableList<Batch>) {
        if (pendingIds.isEmpty()) return
        out.add(Batch(pendingChatId, pendingIds.toList()))
        pendingIds.clear()
    }
}
