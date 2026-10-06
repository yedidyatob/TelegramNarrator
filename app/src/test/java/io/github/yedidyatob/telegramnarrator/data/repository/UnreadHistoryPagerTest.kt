package io.github.yedidyatob.telegramnarrator.data.repository

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class UnreadHistoryPagerTest {

    private data class Msg(val id: Long, val outgoing: Boolean = false)

    /**
     * Fake GetChatHistory(chatId, fromMessageId, offset = 0, limit): newest-first, starts at the newest
     * message for fromMessageId == 0, otherwise at fromMessageId itself. [maxPerPage] simulates TDLib
     * returning fewer messages than requested; [firstPageSize] the tiny first response.
     */
    private class FakeHistory(
        ids: List<Msg>,
        private val maxPerPage: Int = 100,
        private val firstPageSize: Int = maxPerPage
    ) {
        private val newestFirst = ids.sortedByDescending { it.id }
        var requests = 0

        fun page(fromMessageId: Long, limit: Int): List<Msg> {
            val size = if (requests == 0) firstPageSize else maxPerPage
            requests++
            val start = if (fromMessageId == 0L) 0 else newestFirst.indexOfFirst { it.id <= fromMessageId }
            if (start < 0) return emptyList()
            return newestFirst.drop(start).take(minOf(limit, size))
        }
    }

    private fun collect(
        history: FakeHistory,
        unreadCount: Int,
        lastReadId: Long,
        maxResults: Int = 100
    ): List<Long> = runBlocking {
        UnreadHistoryPager.collectOldestUnread(
            unreadCount = unreadCount,
            lastReadId = lastReadId,
            maxResults = maxResults,
            idOf = { it: Msg -> it.id },
            isOutgoing = { it: Msg -> it.outgoing },
            retryDelayMs = 0
        ) { from, limit -> history.page(from, limit) }.map { it.id }
    }

    // 50 read messages (ids 1..50) followed by unread ones
    private fun chat(unread: Int, outgoingIds: Set<Long> = emptySet()): List<Msg> =
        (1L..50L + unread).map { Msg(it, outgoing = it in outgoingIds) }

    @Test
    fun `few unread messages are all returned oldest first`() {
        val history = FakeHistory(chat(unread = 5))
        assertEquals(listOf(51L, 52L, 53L, 54L, 55L), collect(history, unreadCount = 5, lastReadId = 50))
    }

    @Test
    fun `more than the cap returns the oldest unread messages not the newest`() {
        val history = FakeHistory(chat(unread = 250))
        val result = collect(history, unreadCount = 250, lastReadId = 50, maxResults = 100)
        assertEquals((51L..150L).toList(), result)
    }

    @Test
    fun `next run continues after the played ids`() {
        // First run played 51..150; afterwards the chat has 150 unread starting at 151
        val history = FakeHistory(chat(unread = 250))
        val result = collect(history, unreadCount = 150, lastReadId = 150, maxResults = 100)
        assertEquals((151L..250L).toList(), result)
    }

    @Test
    fun `first request returning a single message is handled`() {
        val history = FakeHistory(chat(unread = 30), maxPerPage = 7, firstPageSize = 1)
        assertEquals((51L..80L).toList(), collect(history, unreadCount = 30, lastReadId = 50))
    }

    @Test
    fun `small last page with a pivot message makes progress`() {
        // 2 unread: the second request only needs one new message (pivot + 1)
        val history = FakeHistory(chat(unread = 2), maxPerPage = 100, firstPageSize = 1)
        assertEquals(listOf(51L, 52L), collect(history, unreadCount = 2, lastReadId = 50))
    }

    @Test
    fun `outgoing messages are skipped and do not count as unread`() {
        val history = FakeHistory(chat(unread = 6, outgoingIds = setOf(53L, 56L)))
        assertEquals(listOf(51L, 52L, 54L, 55L), collect(history, unreadCount = 4, lastReadId = 50))
    }

    @Test
    fun `stops at the read boundary when the unread count is too high`() {
        val history = FakeHistory(chat(unread = 3))
        assertEquals(listOf(51L, 52L, 53L), collect(history, unreadCount = 10, lastReadId = 50))
    }

    @Test
    fun `chat that was never read is paged by unread count`() {
        val history = FakeHistory((1L..20L).map { Msg(it) }, maxPerPage = 8)
        assertEquals((1L..20L).toList(), collect(history, unreadCount = 20, lastReadId = 0))
    }

    @Test
    fun `empty history gives up after three requests`() {
        val history = FakeHistory(emptyList())
        assertEquals(emptyList<Long>(), collect(history, unreadCount = 5, lastReadId = 50))
        assertEquals(UnreadHistoryPager.MAX_STALLED_REQUESTS, history.requests)
    }

    @Test
    fun `no unread messages means no requests`() {
        val history = FakeHistory(chat(unread = 5))
        assertEquals(emptyList<Long>(), collect(history, unreadCount = 0, lastReadId = 55))
        assertEquals(0, history.requests)
    }

    @Test
    fun `result is capped by maxResults`() {
        val history = FakeHistory(chat(unread = 20))
        assertEquals(listOf(51L, 52L, 53L), collect(history, unreadCount = 20, lastReadId = 50, maxResults = 3))
    }
}
