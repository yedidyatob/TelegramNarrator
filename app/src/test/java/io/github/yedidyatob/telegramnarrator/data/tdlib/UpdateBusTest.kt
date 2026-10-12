package io.github.yedidyatob.telegramnarrator.data.tdlib

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.concurrent.thread

class UpdateBusTest {

    private suspend fun UpdateBus<*>.awaitSubscribers(n: Int) {
        withTimeout(5_000) { while (subscriberCount < n) yield() }
    }

    @Test
    fun `a slow collector receives every item of a burst, in order`() = runBlocking {
        val bus = UpdateBus<Int>()
        val received = async(Dispatchers.Default) {
            bus.flow.take(1000).toList().also { delay(1) }
        }
        bus.awaitSubscribers(1)
        // Published from another thread, as TDLib does, much faster than the collector
        thread { repeat(1000) { bus.publish(it) } }.join()
        assertEquals((0 until 1000).toList(), withTimeout(10_000) { received.await() })
    }

    @Test
    fun `every collector gets every item and a finished collector unsubscribes`() = runBlocking {
        val bus = UpdateBus<Int>()
        val fast = async(Dispatchers.Default) { bus.flow.take(500).toList() }
        val slow = async(Dispatchers.Default) {
            val out = mutableListOf<Int>()
            bus.flow.take(500).collect { out += it; if (it % 50 == 0) delay(5) }
            out
        }
        bus.awaitSubscribers(2)
        thread { repeat(500) { bus.publish(it) } }.join()
        assertEquals((0 until 500).toList(), withTimeout(10_000) { fast.await() })
        assertEquals((0 until 500).toList(), withTimeout(10_000) { slow.await() })
        assertEquals(0, bus.subscriberCount)
    }
}
