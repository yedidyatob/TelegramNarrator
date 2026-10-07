package io.github.yedidyatob.telegramnarrator.domain.tts

import io.github.yedidyatob.telegramnarrator.domain.openai.OpenAiTts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class TtsFailuresTest {
    @Test
    fun `OpenAI HTTP errors map to specific reasons`() {
        assertEquals(FallbackReason.OPENAI_INVALID_KEY, TtsFailures.openAiHttp(401, "invalid_api_key/invalid_request_error"))
        assertEquals(FallbackReason.OPENAI_NO_CREDIT, TtsFailures.openAiHttp(429, "insufficient_quota"))
        assertEquals(FallbackReason.OPENAI_RATE_LIMITED, TtsFailures.openAiHttp(429, "rate_limit_exceeded/requests"))
        assertEquals(FallbackReason.OPENAI_RATE_LIMITED, TtsFailures.openAiHttp(429, ""))
        assertEquals(FallbackReason.OPENAI_TOO_LONG, TtsFailures.openAiHttp(400, "string_above_max_length"))
        assertEquals(FallbackReason.OPENAI_BAD_REQUEST, TtsFailures.openAiHttp(400, "invalid_request_error"))
        assertEquals(FallbackReason.OPENAI_SERVER_ERROR, TtsFailures.openAiHttp(500, null))
        assertEquals(FallbackReason.OPENAI_SERVER_ERROR, TtsFailures.openAiHttp(503, "server_error"))
        assertEquals(FallbackReason.OPENAI_FAILED, TtsFailures.openAiHttp(404, "model_not_found"))
    }

    @Test
    fun `Edge handshake codes`() {
        assertEquals(FallbackReason.EDGE_THROTTLED, TtsFailures.edgeHandshake(429))
        assertEquals(FallbackReason.EDGE_UNAVAILABLE, TtsFailures.edgeHandshake(403))
        assertEquals(FallbackReason.EDGE_UNAVAILABLE, TtsFailures.edgeHandshake(503))
    }

    @Test
    fun `transport errors are found in the cause chain`() {
        assertEquals(FallbackReason.NO_NETWORK, TtsFailures.transport(UnknownHostException("api.openai.com"), FallbackReason.OPENAI_TIMEOUT))
        assertEquals(FallbackReason.NO_NETWORK, TtsFailures.transport(IOException("wrapped", ConnectException()), FallbackReason.EDGE_TIMEOUT))
        assertEquals(FallbackReason.EDGE_TIMEOUT, TtsFailures.transport(SocketTimeoutException(), FallbackReason.EDGE_TIMEOUT))
        assertEquals(FallbackReason.OPENAI_TIMEOUT, TtsFailures.transport(java.io.InterruptedIOException("timeout"), FallbackReason.OPENAI_TIMEOUT))
        assertNull(TtsFailures.transport(IOException("Edge TTS: malformed binary frame"), FallbackReason.EDGE_TIMEOUT))
    }

    @Test
    fun `help links only for fixable account problems`() {
        assertEquals(OpenAiTts.API_KEYS_URL, TtsFailures.helpUrl(FallbackReason.OPENAI_INVALID_KEY))
        assertEquals(OpenAiTts.BILLING_URL, TtsFailures.helpUrl(FallbackReason.OPENAI_NO_CREDIT))
        assertNull(TtsFailures.helpUrl(FallbackReason.NO_NETWORK))
        assertNull(TtsFailures.helpUrl(FallbackReason.OPENAI_RATE_LIMITED))
    }

    @Test
    fun `connectivity reasons`() {
        assertTrue(FallbackReason.NO_NETWORK.isConnectivity)
        assertTrue(FallbackReason.EDGE_TIMEOUT.isConnectivity)
        assertTrue(FallbackReason.OPENAI_SERVER_ERROR.isConnectivity)
        assertFalse(FallbackReason.OPENAI_TOO_LONG.isConnectivity)
        assertFalse(FallbackReason.OPENAI_INVALID_KEY.isConnectivity)
    }

    @Test
    fun `rate limit retry backoff`() {
        assertEquals(1_000L, RateLimitRetry.delayMs(0, null))
        assertEquals(2_000L, RateLimitRetry.delayMs(1, null))
        assertNull(RateLimitRetry.delayMs(2, null))
        assertEquals(3_000L, RateLimitRetry.delayMs(0, 3))
        assertEquals(RateLimitRetry.MAX_DELAY_MS, RateLimitRetry.delayMs(0, 120))
        assertEquals(1_000L, RateLimitRetry.delayMs(0, 0))
    }

    @Test
    fun `one message per failure episode`() {
        val episodes = TtsFailureEpisodes()
        assertTrue(episodes.onFailure(FallbackReason.NO_NETWORK))
        repeat(5) { assertFalse(episodes.onFailure(FallbackReason.NO_NETWORK)) }
        // A different problem is a new episode
        assertTrue(episodes.onFailure(FallbackReason.OPENAI_INVALID_KEY))
        assertFalse(episodes.onFailure(FallbackReason.OPENAI_INVALID_KEY))
        // After a success the same failure is told again
        episodes.onNetworkSuccess()
        assertTrue(episodes.onFailure(FallbackReason.OPENAI_INVALID_KEY))
    }

    @Test
    fun `splitter keeps short text whole`() {
        assertEquals(listOf("Hello there."), SpeechTextSplitter.split("  Hello there.  ", 100))
        assertEquals(emptyList<String>(), SpeechTextSplitter.split("   ", 100))
    }

    @Test
    fun `splitter cuts at sentence ends within the limit`() {
        val sentence = "This is sentence number one. "
        val text = sentence.repeat(10).trim()
        val chunks = SpeechTextSplitter.split(text, 100)
        assertTrue(chunks.all { it.length <= 100 })
        assertTrue(chunks.all { it.endsWith(".") })
        assertEquals(text.replace(" ", ""), chunks.joinToString("").replace(" ", ""))
    }

    @Test
    fun `splitter falls back to spaces then hard cuts`() {
        val words = List(60) { "word" }.joinToString(" ")
        val chunks = SpeechTextSplitter.split(words, 50)
        assertTrue(chunks.all { it.length <= 50 && !it.startsWith(" ") })
        assertEquals(words.replace(" ", ""), chunks.joinToString("").replace(" ", ""))
        val blob = "x".repeat(120)
        assertEquals(listOf(50, 50, 20), SpeechTextSplitter.split(blob, 50).map { it.length })
    }

    @Test
    fun `splitter handles Hebrew sentences and the OpenAI limit`() {
        val text = "זה משפט בעברית. ".repeat(400)
        val chunks = SpeechTextSplitter.split(text, OpenAiTts.MAX_INPUT_CHARS)
        assertTrue(chunks.size >= 2)
        assertTrue(chunks.all { it.length <= OpenAiTts.MAX_INPUT_CHARS && it.endsWith(".") })
    }
}
