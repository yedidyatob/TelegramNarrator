package io.github.yedidyatob.telegramnarrator.domain.sponsored

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SponsoredViewTrackerTest {
    @Test
    fun `once per ad per fetch`() {
        val tracker = SponsoredViewTracker()
        val ad = ad().copy(fetchId = 1)
        assertTrue(tracker.markIfNew(ad))
        assertFalse(tracker.markIfNew(ad))
        assertTrue(tracker.markIfNew(ad.copy(fetchId = 2))) // new fetch
        assertTrue(tracker.markIfNew(ad.copy(messageId = 101))) // other ad
        assertTrue(tracker.markIfNew(ad.copy(chatId = 9))) // other chat
    }

    @Test
    fun `unmark allows a retry`() {
        val tracker = SponsoredViewTracker()
        val ad = ad()
        tracker.markIfNew(ad)
        tracker.unmark(ad)
        assertFalse(tracker.wasReported(ad))
        assertTrue(tracker.markIfNew(ad))
    }
}
