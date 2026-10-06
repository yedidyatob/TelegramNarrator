package com.example.telegramnarrator.domain.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DelayedLoadingTest {

    @Test
    fun `default delay is 300 ms`() {
        assertEquals(300L, DelayedLoading().delayMs)
    }

    @Test
    fun `nothing is visible before the delay`() {
        val loading = DelayedLoading(300)
        loading.begin(nowMs = 1_000)
        assertFalse(loading.isVisible(1_000))
        assertFalse(loading.isVisible(1_299))
        assertTrue(loading.isVisible(1_300))
        assertTrue(loading.isVisible(5_000))
        assertEquals(1_300L, loading.visibleAtMs())
    }

    @Test
    fun `a cache hit that finishes within the delay never becomes visible`() {
        val loading = DelayedLoading(300)
        val token = loading.begin(nowMs = 0)
        assertTrue(loading.end(token))
        // The delayed check fires later and finds nothing pending
        assertFalse(loading.isVisible(300))
        assertFalse(loading.isPending(token))
        assertNull(loading.visibleAtMs())
    }

    @Test
    fun `a slow load is hidden as soon as it ends`() {
        val loading = DelayedLoading(300)
        val token = loading.begin(nowMs = 0)
        assertTrue(loading.isVisible(2_000))
        assertTrue(loading.end(token))
        assertFalse(loading.isVisible(2_001))
    }

    @Test
    fun `a newer load supersedes an older one and its late end is ignored`() {
        val loading = DelayedLoading(300)
        val first = loading.begin(nowMs = 0)
        val second = loading.begin(nowMs = 1_000)
        assertFalse(loading.isPending(first))
        assertTrue(loading.isPending(second))
        // The first load's late completion must not hide the second one
        assertFalse(loading.end(first))
        assertTrue(loading.isVisible(1_300))
        // The delay restarts with the newer load
        assertFalse(loading.isVisible(1_299))
        assertTrue(loading.end(second))
        assertFalse(loading.end(second)) // ending twice is a no-op
    }

    @Test
    fun `clear drops a pending load (skip, pause, next item)`() {
        val loading = DelayedLoading(300)
        val token = loading.begin(nowMs = 0)
        loading.clear()
        assertFalse(loading.isVisible(10_000))
        assertFalse(loading.isPending(token))
        assertFalse(loading.end(token))
    }
}
