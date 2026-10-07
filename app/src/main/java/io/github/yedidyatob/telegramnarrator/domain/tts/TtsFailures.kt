package io.github.yedidyatob.telegramnarrator.domain.tts

import io.github.yedidyatob.telegramnarrator.domain.openai.OpenAiTts
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** Maps engine errors to a [FallbackReason] (#63). Pure; only status codes and machine-readable error codes. */
object TtsFailures {

    /** OpenAI HTTP error: [code] and the body's `code`/`type` values ([OpenAiTts.safeErrorSummary]). */
    fun openAiHttp(code: Int, errorSummary: String?): FallbackReason {
        val summary = errorSummary.orEmpty().lowercase()
        return when {
            code == 401 -> FallbackReason.OPENAI_INVALID_KEY
            code == 429 && "insufficient_quota" in summary -> FallbackReason.OPENAI_NO_CREDIT
            code == 429 -> FallbackReason.OPENAI_RATE_LIMITED
            code == 400 && LENGTH_HINTS.any { it in summary } -> FallbackReason.OPENAI_TOO_LONG
            code == 400 -> FallbackReason.OPENAI_BAD_REQUEST
            code in 500..599 -> FallbackReason.OPENAI_SERVER_ERROR
            else -> FallbackReason.OPENAI_FAILED
        }
    }

    /** Edge WebSocket handshake rejected with HTTP [code]. */
    fun edgeHandshake(code: Int): FallbackReason =
        if (code == 429) FallbackReason.EDGE_THROTTLED else FallbackReason.EDGE_UNAVAILABLE

    /**
     * Transport failures (no network, timeout) anywhere in [error]'s cause chain, or null when [error] is not
     * one. [timeout] is the engine's timeout reason.
     */
    fun transport(error: Throwable, timeout: FallbackReason): FallbackReason? {
        var e: Throwable? = error
        var depth = 0
        while (e != null && depth++ < 8) {
            when (e) {
                is UnknownHostException, is ConnectException, is NoRouteToHostException -> return FallbackReason.NO_NETWORK
                is SocketTimeoutException -> return timeout
                is InterruptedIOException -> if (e.message?.contains("timeout", ignoreCase = true) == true) return timeout
            }
            e = e.cause
        }
        return null
    }

    /** Where the user can fix [reason] (opened from the notification), or null. */
    fun helpUrl(reason: FallbackReason): String? = when (reason) {
        FallbackReason.OPENAI_INVALID_KEY -> OpenAiTts.API_KEYS_URL
        FallbackReason.OPENAI_NO_CREDIT -> OpenAiTts.BILLING_URL
        else -> null
    }

    // OpenAI's codes for an input over the limit ("string_above_max_length", "..._too_long")
    private val LENGTH_HINTS = listOf("max_length", "too_long", "string_above")
}

/**
 * Backoff for OpenAI HTTP 429 rate limits: retry [MAX_RETRIES] times before falling back. Honours the
 * `Retry-After` header (capped at [MAX_DELAY_MS]), otherwise 1 s, 2 s.
 */
object RateLimitRetry {
    const val MAX_RETRIES = 2
    const val MAX_DELAY_MS = 10_000L
    private const val BASE_DELAY_MS = 1_000L

    /** Delay before retry number [retry] (0-based), or null when no retry is left. */
    fun delayMs(retry: Int, retryAfterSeconds: Long?): Long? {
        if (retry >= MAX_RETRIES) return null
        val fromHeader = retryAfterSeconds?.takeIf { it > 0 }?.let { it * 1_000L }
        return (fromHeader ?: (BASE_DELAY_MS shl retry)).coerceAtMost(MAX_DELAY_MS)
    }
}

/**
 * "One message per failure episode" (#63): the first failure tells the user; the same failure again stays quiet
 * until a network synthesis succeeds (or a different failure happens). Cache hits don't end an episode, so
 * cached messages played during an outage don't cause a second message. Thread-safe.
 */
class TtsFailureEpisodes {
    private var active: FallbackReason? = null

    /** Records [reason]; true when the user should be told (a new episode). */
    @Synchronized
    fun onFailure(reason: FallbackReason): Boolean {
        if (active == reason) return false
        active = reason
        return true
    }

    /** A synthesis over the network worked: the episode is over. */
    @Synchronized
    fun onNetworkSuccess() {
        active = null
    }
}

/**
 * Splits text for OpenAI's [OpenAiTts.MAX_INPUT_CHARS] input limit (#63 "split gracefully"): at paragraph or
 * sentence ends when possible, then at spaces, and only as a last resort mid-word. The MP3s of the chunks are
 * concatenated into one file.
 */
object SpeechTextSplitter {
    fun split(text: String, maxChars: Int): List<String> {
        require(maxChars > 0)
        val chunks = mutableListOf<String>()
        var rest = text.trim()
        while (rest.length > maxChars) {
            val window = rest.substring(0, maxChars)
            val cut = lastBreak(window, PARAGRAPH) ?: lastBreak(window, SENTENCE) ?: lastBreak(window, SPACE) ?: maxChars
            chunks += rest.substring(0, cut).trim()
            rest = rest.substring(cut).trim()
        }
        if (rest.isNotEmpty()) chunks += rest
        return chunks.filter { it.isNotBlank() }
    }

    // Index just after the last break in the second half of the window (so chunks don't get tiny)
    private fun lastBreak(window: String, breaks: Regex): Int? =
        breaks.findAll(window).map { it.range.last + 1 }.filter { it >= window.length / 2 }.lastOrNull()

    private val PARAGRAPH = Regex("\n\\s*")
    private val SENTENCE = Regex("[.!?…。؟]+[\"'”’)]*\\s+")
    private val SPACE = Regex("\\s+")
}
