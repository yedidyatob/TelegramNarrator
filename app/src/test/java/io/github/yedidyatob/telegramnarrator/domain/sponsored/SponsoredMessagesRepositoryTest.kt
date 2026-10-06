package io.github.yedidyatob.telegramnarrator.domain.sponsored

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SponsoredMessagesRepositoryTest {
    private var now = 0L
    private val source = FakeSponsoredSource().apply {
        kinds[1L] = SponsoredChatKind.CHANNEL
        kinds[2L] = null // a group: TDLib has no ads for it
        kinds[3L] = SponsoredChatKind.BOT
        ads[1L] = listOf(ad(chatId = 1L, messageId = 100L))
        ads[3L] = listOf(ad(chatId = 3L, messageId = 300L))
    }
    private val repo = SponsoredMessagesRepository(source) { now }

    @Test
    fun `ads are fetched once and cached for five minutes`() = runBlocking {
        val first = repo.adFor(1L)
        now += SponsoredAdCache.TTL_MS - 1
        assertEquals(first, repo.adFor(1L))
        assertEquals(1, source.fetchCalls)
        now += 1
        val refetched = repo.adFor(1L)
        assertEquals(2, source.fetchCalls)
        assertTrue(refetched!!.fetchId > first!!.fetchId)
    }

    @Test
    fun `chats without ads are never fetched and their kind is remembered`() = runBlocking {
        assertNull(repo.adFor(2L))
        assertNull(repo.adFor(2L))
        assertEquals(0, source.fetchCalls)
        assertEquals(1, source.kindCalls)
    }

    @Test
    fun `bot chats get ads too, marked as bot ads`() = runBlocking {
        val botAd = repo.adFor(3L)!!
        assertEquals(SponsoredChatKind.BOT, botAd.chatKind)
        assertEquals(SponsoredChatKind.CHANNEL, repo.adFor(1L)!!.chatKind)
    }

    @Test
    fun `fetch errors are not cached`() = runBlocking {
        source.failFetch = true
        assertNull(repo.adFor(1L))
        source.failFetch = false
        assertEquals(100L, repo.adFor(1L)!!.messageId)
        assertEquals(2, source.fetchCalls)
    }

    @Test
    fun `view is reported once per ad per fetch, whichever trigger comes first`() = runBlocking {
        val ad = repo.adFor(1L)!!
        assertTrue(repo.reportViewed(ad)) // read aloud in full
        assertFalse(repo.reportViewed(ad)) // card fully visible afterwards
        assertFalse(repo.reportViewed(repo.adFor(1L)!!)) // same fetch from the cache
        assertEquals(listOf(1L to 100L), source.views)
        assertTrue(repo.wasViewReported(ad))

        now += SponsoredAdCache.TTL_MS // a new fetch: a new view may be reported
        assertTrue(repo.reportViewed(repo.adFor(1L)!!))
        assertEquals(2, source.views.size)
    }

    @Test
    fun `a failed view report can be retried`() = runBlocking {
        val ad = repo.adFor(1L)!!
        source.failView = true
        assertFalse(repo.reportViewed(ad))
        assertFalse(repo.wasViewReported(ad))
        source.failView = false
        assertTrue(repo.reportViewed(ad))
        assertEquals(1, source.views.size)
    }

    @Test
    fun `clicks pass the media flag`() = runBlocking {
        val ad = repo.adFor(1L)!!
        repo.reportClick(ad)
        repo.reportClick(ad, isMediaClick = true)
        assertEquals(listOf(Triple(1L, 100L, false), Triple(1L, 100L, true)), source.clicks)
    }

    @Test
    fun `report flow sends an empty option first, then the chosen one, and drops the reported ad`() = runBlocking {
        val ad = repo.adFor(1L)!!
        val option = SponsoredReportOption(byteArrayOf(7, 8), "Spam")
        source.reportResults += SponsoredReportResult.OptionRequired("Why?", listOf(option))
        source.reportResults += SponsoredReportResult.Reported

        val first = repo.report(ad)
        assertEquals(SponsoredReportResult.OptionRequired("Why?", listOf(option)), first)
        assertEquals(1L, repo.adFor(1L)!!.chatId) // still cached while the user picks
        assertEquals(SponsoredReportResult.Reported, repo.report(ad, option))

        assertArrayEquals(ByteArray(0), source.reports[0].third)
        assertArrayEquals(byteArrayOf(7, 8), source.reports[1].third)
        assertNull(repo.adFor(1L)) // removed from the cache until the next fetch
        assertEquals(1, source.fetchCalls)
    }

    @Test
    fun `ads hidden clears every cached chat`() = runBlocking {
        val ad = repo.adFor(1L)!!
        repo.adFor(3L)
        source.reportResults += SponsoredReportResult.AdsHidden
        assertEquals(SponsoredReportResult.AdsHidden, repo.report(ad))
        repo.adFor(1L)
        repo.adFor(3L)
        assertEquals(4, source.fetchCalls)
    }

    @Test
    fun `report errors return null and keep the ad`() = runBlocking {
        val ad = repo.adFor(1L)!!
        source.failReport = true
        assertNull(repo.report(ad))
        assertEquals(ad, repo.adFor(1L))
    }

    @Test
    fun `premium required keeps the ad`() = runBlocking {
        val ad = repo.adFor(1L)!!
        source.reportResults += SponsoredReportResult.PremiumRequired
        repo.report(ad)
        assertEquals(ad, repo.adFor(1L))
    }
}
