package io.github.yedidyatob.telegramnarrator.domain.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class TtsFailuresTest {
    @Test
    fun `Gemini HTTP errors map to specific reasons`() {
        assertEquals(FallbackReason.GEMINI_INVALID_KEY, TtsFailures.geminiHttp(401, "invalid_key"))
        assertEquals(FallbackReason.GEMINI_INVALID_KEY, TtsFailures.geminiHttp(403, "permission_denied"))
        assertEquals(FallbackReason.GEMINI_NO_CREDIT, TtsFailures.geminiHttp(429, "RESOURCE_EXHAUSTED/quota"))
        assertEquals(FallbackReason.GEMINI_RATE_LIMITED, TtsFailures.geminiHttp(429, "rate_limit_exceeded"))
        assertEquals(FallbackReason.GEMINI_RATE_LIMITED, TtsFailures.geminiHttp(429, ""))
        assertEquals(FallbackReason.GEMINI_BAD_REQUEST, TtsFailures.geminiHttp(400, "invalid_request"))
        assertEquals(FallbackReason.GEMINI_SERVER_ERROR, TtsFailures.geminiHttp(500, "internal_error"))
        assertEquals(FallbackReason.GEMINI_SERVER_ERROR, TtsFailures.geminiHttp(503, "service_unavailable"))
        assertEquals(FallbackReason.GEMINI_FAILED, TtsFailures.geminiHttp(404, "not_found"))
    }

    @Test
    fun `Edge handshake codes`() {
        assertEquals(FallbackReason.EDGE_THROTTLED, TtsFailures.edgeHandshake(429))
        assertEquals(FallbackReason.EDGE_UNAVAILABLE, TtsFailures.edgeHandshake(403))
        assertEquals(FallbackReason.EDGE_UNAVAILABLE, TtsFailures.edgeHandshake(503))
    }

    @Test
    fun `transport errors are found in the cause chain`() {
        assertEquals(FallbackReason.NO_NETWORK, TtsFailures.transport(UnknownHostException("api.google.com"), FallbackReason.GEMINI_TIMEOUT))
        assertEquals(FallbackReason.NO_NETWORK, TtsFailures.transport(IOException("wrapped", ConnectException()), FallbackReason.EDGE_TIMEOUT))
        assertEquals(FallbackReason.EDGE_TIMEOUT, TtsFailures.transport(SocketTimeoutException(), FallbackReason.EDGE_TIMEOUT))
        assertEquals(FallbackReason.GEMINI_TIMEOUT, TtsFailures.transport(java.io.InterruptedIOException("timeout"), FallbackReason.GEMINI_TIMEOUT))
        assertNull(TtsFailures.transport(IOException("Gemini: some error"), FallbackReason.EDGE_TIMEOUT))
    }

    @Test
    fun `help URL for Gemini key errors`() {
        assertNotNull(TtsFailures.helpUrl(FallbackReason.GEMINI_INVALID_KEY))
        assertNotNull(TtsFailures.helpUrl(FallbackReason.GEMINI_NO_CREDIT))
        assertNull(TtsFailures.helpUrl(FallbackReason.GEMINI_FAILED))
        assertNull(TtsFailures.helpUrl(FallbackReason.NO_NETWORK))
    }

    @Test
    fun `rate limit retry backoff`() {
        // Retry 0: 1 s
        assertEquals(1_000L, RateLimitRetry.delayMs(0, null))
        // Retry 1: 2 s
        assertEquals(2_000L, RateLimitRetry.delayMs(1, null))
        // Retry 2+: no retry
        assertNull(RateLimitRetry.delayMs(2, null))
        // Retry-After header (capped at 10 s)
        assertEquals(3_000L, RateLimitRetry.delayMs(0, 3L))
        assertEquals(10_000L, RateLimitRetry.delayMs(1, 20L))
    }

    @Test
    fun `SpeechTextSplitter splits on boundaries`() {
        val text = "Hello world. This is a test.\n\nSecond paragraph here."
        val chunks = SpeechTextSplitter.split(text, 30)
        assertEquals(listOf("Hello world. This is a test.", "Second paragraph here."), chunks)
    }

    @Test
    fun `SpeechTextSplitter hard-cuts at limit`() {
        val text = "a".repeat(50)
        val chunks = SpeechTextSplitter.split(text, 30)
        assertEquals(2, chunks.size)
        assertEquals(30, chunks[0].length)
        assertEquals(20, chunks[1].length)
    }
}
