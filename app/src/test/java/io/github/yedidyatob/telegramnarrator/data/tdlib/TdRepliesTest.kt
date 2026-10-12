package io.github.yedidyatob.telegramnarrator.data.tdlib

import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class TdRepliesTest {

    @Test
    fun `reply resumes the caller`() = runTest {
        val ok = TdReplies.await<TdApi.Ok>("Test", 1_000) { onReply -> onReply(TdApi.Ok()) }
        assertTrue(ok is TdApi.Ok)
    }

    @Test
    fun `an Error reply becomes a TdLibException with its code`() = runTest {
        try {
            TdReplies.await<TdApi.Ok>("Test", 1_000) { onReply -> onReply(TdApi.Error(404, "Not Found")) }
            fail()
        } catch (e: TdLibException) {
            assertEquals(404, e.code)
            assertEquals("TDLib Error: 404 - Not Found", e.message)
        }
    }

    @Test
    fun `no reply times out`() = runTest {
        try {
            TdReplies.await<TdApi.Ok>("GetChat", 5_000) { /* never answers */ }
            fail()
        } catch (e: TdLibTimeoutException) {
            assertEquals(TdLibTimeoutException.TIMEOUT_CODE, e.code)
            assertTrue(e.message!!.contains("GetChat"))
        }
    }

    @Test
    fun `cancelled call drops the late reply`() = runTest {
        var reply: ((TdApi.Object) -> Unit)? = null
        val call = async { TdReplies.await<TdApi.Ok>("Test", 60_000) { reply = it } }
        runCurrent()
        call.cancel()
        advanceTimeBy(1)
        // A reply after cancellation must not throw or resume anything
        reply!!.invoke(TdApi.Ok())
        assertTrue(call.isCancelled)
    }
}
