package io.github.yedidyatob.telegramnarrator.domain.sponsored

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SponsoredAdCacheTest {
    private val minute = 60_000L

    @Test
    fun `cached for five minutes`() {
        assertEquals(5 * minute, SponsoredAdCache.TTL_MS)
        val cache = SponsoredAdCache()
        val stored = cache.put(1L, listOf(ad()), nowMs = 0L)
        assertEquals(stored, cache.get(1L, nowMs = 5 * minute - 1))
        assertNull(cache.get(1L, nowMs = 5 * minute))
    }

    @Test
    fun `every put is a new fetch with its own id`() {
        val cache = SponsoredAdCache()
        val first = cache.put(1L, listOf(ad()), 0L).single()
        val second = cache.put(1L, listOf(ad()), 10L).single()
        assertNotEquals(first.fetchId, second.fetchId)
        assertNotEquals(first.viewKey, second.viewKey)
        assertEquals(1L, first.chatId)
    }

    @Test
    fun `chats are cached separately`() {
        val cache = SponsoredAdCache()
        cache.put(1L, listOf(ad(chatId = 1L)), 0L)
        assertNull(cache.get(2L, 0L))
    }

    @Test
    fun `remove drops one ad and keeps the fetch time`() {
        val cache = SponsoredAdCache()
        val stored = cache.put(1L, listOf(ad(messageId = 1), ad(messageId = 2)), 0L)
        cache.remove(stored[0])
        assertEquals(listOf(stored[1]), cache.get(1L, 4 * minute))
        assertNull(cache.get(1L, 5 * minute))
    }

    @Test
    fun `a clock that went backwards expires the entry`() {
        val cache = SponsoredAdCache()
        cache.put(1L, listOf(ad()), 1000L)
        assertNull(cache.get(1L, 0L))
    }
}
