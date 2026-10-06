package io.github.yedidyatob.telegramnarrator.data.openai

import io.github.yedidyatob.telegramnarrator.data.tts.TtsPreferences
import io.github.yedidyatob.telegramnarrator.domain.openai.OpenAiTts
import io.github.yedidyatob.telegramnarrator.domain.tts.FallbackReason
import io.github.yedidyatob.telegramnarrator.domain.tts.RateLimitRetry
import io.github.yedidyatob.telegramnarrator.domain.tts.SpeechProvider
import io.github.yedidyatob.telegramnarrator.domain.tts.SpeechSynthesisOutcome
import io.github.yedidyatob.telegramnarrator.domain.tts.SpeechTextSplitter
import io.github.yedidyatob.telegramnarrator.domain.tts.TtsFailures
import io.github.yedidyatob.telegramnarrator.domain.tts.TtsVoiceLogic
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves OpenAI-synthesized MP3 for a message: cache hit or network call.
 * Text over OpenAI's input limit is split and the chunks' MP3s are joined; HTTP 429 rate limits are retried
 * with backoff ([RateLimitRetry]); every failure maps to a specific [FallbackReason] ([TtsFailures]).
 * Logs only the HTTP status and OpenAI's error code, never the response body, the key or the text.
 */
@Singleton
class OpenAiSpeechSynthesizer @Inject constructor(
    private val preferences: TtsPreferences,
    private val keyStore: OpenAiKeyStore,
    private val cache: OpenAiSpeechCache,
    private val client: OpenAiSpeechClient
) {
    /** Backoff sleep between rate-limit retries (runs on the caller's IO thread). */
    internal var sleep: (Long) -> Unit = { Thread.sleep(it) }

    fun isOpenAiEnabled(): Boolean = preferences.settings.value.provider == SpeechProvider.OPENAI

    fun shouldPreferOpenAi(): Boolean = isOpenAiEnabled() && keyStore.hasApiKey()

    /** A cached file that turned out to be unplayable is dropped so the next attempt re-fetches it. */
    fun discard(file: File) = cache.remove(file)

    fun synthesize(text: String): SpeechSynthesisOutcome {
        if (!isOpenAiEnabled()) return SpeechSynthesisOutcome.UseSystem
        return synthesizeWithSavedKey(text)
    }

    /** "Test voice" in settings: the selected model / voice with the saved key (the caller checks a key is saved). */
    fun synthesizeForTest(text: String): SpeechSynthesisOutcome = synthesizeWithSavedKey(text)

    private fun synthesizeWithSavedKey(text: String): SpeechSynthesisOutcome {
        val settings = preferences.settings.value
        val opts = settings.openAi
        val apiKey = keyStore.getApiKey()
        if (apiKey == null) {
            return SpeechSynthesisOutcome.Fallback(FallbackReason.OPENAI_NO_KEY)
        }
        if (text.isBlank()) return SpeechSynthesisOutcome.UseSystem

        val model = OpenAiTts.normalizeModel(opts.model)
        val voice = OpenAiTts.normalizeVoice(opts.voice)
        cache.getIfPresent(text, voice, model)?.let { return SpeechSynthesisOutcome.Ready(it, fromCache = true) }

        val chunks = SpeechTextSplitter.split(text, OpenAiTts.MAX_INPUT_CHARS)
        if (chunks.isEmpty()) return SpeechSynthesisOutcome.UseSystem
        return try {
            val speed = TtsVoiceLogic.clampRate(settings.speechRate)
            val out = ByteArrayOutputStream()
            for (chunk in chunks) {
                out.write(synthesizeWithRetry(OpenAiSpeechClient.Request(apiKey, model, voice, chunk, speed)))
            }
            val file = cache.put(text, voice, model, out.toByteArray())
            SpeechSynthesisOutcome.Ready(file, fromCache = false)
        } catch (e: OpenAiSpeechClient.HttpException) {
            android.util.Log.w(TAG, "TTS request failed: HTTP ${e.code} ${e.errorSummary}")
            SpeechSynthesisOutcome.Fallback(TtsFailures.openAiHttp(e.code, e.errorSummary))
        } catch (e: IOException) {
            android.util.Log.w(TAG, "TTS request failed: ${e.javaClass.simpleName}")
            SpeechSynthesisOutcome.Fallback(
                TtsFailures.transport(e, FallbackReason.OPENAI_TIMEOUT) ?: FallbackReason.OPENAI_FAILED
            )
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            SpeechSynthesisOutcome.Fallback(FallbackReason.OPENAI_FAILED)
        } catch (e: Exception) {
            android.util.Log.w(TAG, "TTS unexpected error: ${e.javaClass.simpleName}")
            SpeechSynthesisOutcome.Fallback(FallbackReason.OPENAI_FAILED)
        }
    }

    /** One request; HTTP 429 rate limits (not out-of-credit) are retried with [RateLimitRetry] backoff. */
    private fun synthesizeWithRetry(request: OpenAiSpeechClient.Request): ByteArray {
        var retry = 0
        while (true) {
            try {
                return client.synthesize(request)
            } catch (e: OpenAiSpeechClient.HttpException) {
                val rateLimited = TtsFailures.openAiHttp(e.code, e.errorSummary) == FallbackReason.OPENAI_RATE_LIMITED
                val delay = if (rateLimited) RateLimitRetry.delayMs(retry, e.retryAfterSeconds) else null
                if (delay == null) throw e
                android.util.Log.i(TAG, "Rate limited; retry ${retry + 1} in ${delay}ms")
                sleep(delay)
                retry++
            }
        }
    }

    private companion object {
        const val TAG = "OpenAiSpeech"
    }
}
