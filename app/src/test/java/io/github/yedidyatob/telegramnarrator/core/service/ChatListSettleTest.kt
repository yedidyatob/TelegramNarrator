package io.github.yedidyatob.telegramnarrator.core.service

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatListSettleTest {
    @Test
    fun `waits for TDLib to finish delivering chats instead of taking the first one`() = runTest {
        val list = MutableStateFlow(emptyList<Int>())
        val result = async { ChatListSettle.await(list, quietMs = 500, maxMs = 4_000) }
        // Chats arrive one by one after LoadChats
        for (i in 1..5) { delay(100); list.value = list.value + i }
        advanceTimeBy(1_000)
        assertEquals(listOf(1, 2, 3, 4, 5), result.await())
    }

    @Test
    fun `a list that keeps changing is returned at the time limit`() = runTest {
        val list = MutableStateFlow(0)
        val result = async { ChatListSettle.await(list, quietMs = 500, maxMs = 2_000) }
        val ticker = async { repeat(100) { delay(100); list.value++ } }
        val value = result.await()
        ticker.cancel()
        assertEquals(true, value in 15..21)
    }

    @Test
    fun `an already settled list is returned after the quiet time`() = runTest {
        val list = MutableStateFlow(listOf(7))
        assertEquals(listOf(7), ChatListSettle.await(list, quietMs = 500, maxMs = 4_000))
    }
}
