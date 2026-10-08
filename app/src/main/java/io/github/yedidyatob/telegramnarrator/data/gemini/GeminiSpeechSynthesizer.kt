package io.github.yedidyatob.telegramnarrator.data.gemini

import io.github.yedidyatob.telegramnarrator.data.tts.TtsPreferences
import io.github.yedidyatob.telegramnarrator.domain.gemini.GeminiTts
import io.github.yedidyatob.telegramnarrator.domain.tts.FallbackReason
import io.github.yedidyatob.telegramnarrator.domain.tts.RateLimitRetry
import io.github.yedidyatob.telegramnarrator.domain.tts.SpeechProvider
import io.github.yedidyatob.telegramnarrator.domain.tts.SpeechSynthesisOutcome
import io.github.yedidyatob.telegramnarrator.domain.tts.SpeechTextSplitter
import io.github.yedidyatob.telegramnarrator.domain.tts.TtsFailures
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves Gemini-synthesized WAV audio for a message: cache hit or network call.
 *
 * Text over [GeminiTts.MAX_INPUT_CHARS] is split by [SpeechTextSplitter]; each chunk yields a separate
 * WAV from the API. The raw PCM portions of those WAVs are concatenated, then a single new WAV header
 * is written — so the cached file is always one valid WAV regardless of how many chunks it came from.
 *
 * HTTP 429 rate limits are retried with backoff ([RateLimitRetry]). Every failure maps to a specific
 * [FallbackReason] ([TtsFailures]). If a chunk returns no audio, the whole message falls back to system
 * TTS. Logs only the HTTP status code and Gemini's machine-readable error fields — never the key, the
 * request text, or the response body.
 *
 * Speech rate is applied by MediaPlayer at playback time (does not bust the cache).
 */
@Singleton
class GeminiSpeechSynthesizer @Inject constructor(
    private val preferences: TtsPreferences,
    private val keyStore: GeminiKeyStore,
    private val cache: GeminiSpeechCache,
    private val client: GeminiSpeechClient
) {
    /** Backoff sleep between rate-limit retries (runs on the caller's IO thread). */
    internal var sleep: (Long) -> Unit = { Thread.sleep(it) }

    fun isGeminiEnabled(): Boolean = preferences.settings.value.provider == SpeechProvider.GEMINI

    fun shouldPreferGemini(): Boolean = isGeminiEnabled() && keyStore.hasApiKey()

    /** A cached file that turned out to be unplayable is dropped so the next attempt re-fetches it. */
    fun discard(file: File) = cache.remove(file)

    fun synthesize(text: String): SpeechSynthesisOutcome {
        if (!isGeminiEnabled()) return SpeechSynthesisOutcome.UseSystem
        return synthesizeWithSavedKey(text)
    }

    /** "Test voice" in settings: the selected voice with the saved key (caller checks that a key is saved). */
    fun synthesizeForTest(text: String): SpeechSynthesisOutcome = synthesizeWithSavedKey(text)

    private fun synthesizeWithSavedKey(text: String): SpeechSynthesisOutcome {
        val voice = GeminiTts.normalizeVoice(preferences.settings.value.gemini.voice)
        val apiKey = keyStore.getApiKey()
        if (apiKey == null) {
            return SpeechSynthesisOutcome.Fallback(FallbackReason.GEMINI_NO_KEY)
        }
        if (text.isBlank()) return SpeechSynthesisOutcome.UseSystem

        cache.getIfPresent(text, voice)?.let { return SpeechSynthesisOutcome.Ready(it, fromCache = true) }

        val chunks = SpeechTextSplitter.split(text, GeminiTts.MAX_INPUT_CHARS)
        if (chunks.isEmpty()) return SpeechSynthesisOutcome.UseSystem

        return try {
            val pcmOut = ByteArrayOutputStream()
            for (chunk in chunks) {
                val wavBytes = synthesizeChunkWithRetry(GeminiSpeechClient.Request(apiKey, voice, chunk))
                // Strip the 44-byte WAV header to get raw PCM, then accumulate
                if (wavBytes.size <= WAV_HEADER_SIZE) {
                    android.util.Log.w(TAG, "Chunk produced no audio; falling back")
                    return SpeechSynthesisOutcome.Fallback(FallbackReason.GEMINI_NO_AUDIO)
                }
                pcmOut.write(wavBytes, WAV_HEADER_SIZE, wavBytes.size - WAV_HEADER_SIZE)
            }
            // Re-wrap the concatenated PCM in a single WAV header
            val finalWav = client.buildWav(pcmOut.toByteArray())
            val file = cache.put(text, voice, finalWav)
            SpeechSynthesisOutcome.Ready(file, fromCache = false)
        } catch (e: GeminiSpeechClient.HttpException) {
            android.util.Log.w(TAG, "TTS request failed: HTTP ${e.code} ${e.errorSummary}")
            SpeechSynthesisOutcome.Fallback(TtsFailures.geminiHttp(e.code, e.errorSummary))
        } catch (e: GeminiSpeechClient.NoAudioException) {
            android.util.Log.w(TAG, "TTS request returned no audio")
            SpeechSynthesisOutcome.Fallback(FallbackReason.GEMINI_NO_AUDIO)
        } catch (e: IOException) {
            android.util.Log.w(TAG, "TTS request failed: ${e.javaClass.simpleName}")
            SpeechSynthesisOutcome.Fallback(
                TtsFailures.transport(e, FallbackReason.GEMINI_TIMEOUT) ?: FallbackReason.GEMINI_FAILED
            )
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            SpeechSynthesisOutcome.Fallback(FallbackReason.GEMINI_FAILED)
        } catch (e: Exception) {
            android.util.Log.w(TAG, "TTS unexpected error: ${e.javaClass.simpleName}")
            SpeechSynthesisOutcome.Fallback(FallbackReason.GEMINI_FAILED)
        }
    }

    /**
     * Synthesizes one chunk; HTTP 429 rate limits (not out-of-credit) are retried with
     * [RateLimitRetry] backoff.
     */
    private fun synthesizeChunkWithRetry(request: GeminiSpeechClient.Request): ByteArray {
        var retry = 0
        while (true) {
            try {
                return client.synthesize(request)
            } catch (e: GeminiSpeechClient.HttpException) {
                val rateLimited = TtsFailures.geminiHttp(e.code, e.errorSummary) == FallbackReason.GEMINI_RATE_LIMITED
                val delay = if (rateLimited) RateLimitRetry.delayMs(retry, e.retryAfterSeconds) else null
                if (delay == null) throw e
                android.util.Log.i(TAG, "Rate limited; retry ${retry + 1} in ${delay}ms")
                sleep(delay)
                retry++
            }
        }
    }

    private companion object {
        const val TAG = "GeminiSpeech"
        const val WAV_HEADER_SIZE = 44
    }
}
