package io.github.yedidyatob.telegramnarrator.data.edge

import android.os.SystemClock
import io.github.yedidyatob.telegramnarrator.data.tts.TtsPreferences
import io.github.yedidyatob.telegramnarrator.domain.audio.LanguageDetector
import io.github.yedidyatob.telegramnarrator.domain.edge.EdgeTts
import io.github.yedidyatob.telegramnarrator.domain.tts.FallbackReason
import io.github.yedidyatob.telegramnarrator.domain.tts.SpeechProvider
import io.github.yedidyatob.telegramnarrator.domain.tts.SpeechSynthesisOutcome
import io.github.yedidyatob.telegramnarrator.domain.tts.TtsFailures
import io.github.yedidyatob.telegramnarrator.domain.tts.TtsVoiceLogic
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Experimental Edge TTS: resolves a message's MP3 from the disk cache or the Edge WebSocket.
 * After a failure, Edge is skipped (system TTS, no extra toast) for [FAILURE_BACKOFF_MS] so that an offline
 * phone or a blocked endpoint does not add a network timeout before every message.
 */
@Singleton
class EdgeSpeechSynthesizer @Inject constructor(
    private val preferences: TtsPreferences,
    private val cache: EdgeSpeechCache,
    private val client: EdgeTtsClient
) {
    @Volatile private var backoffUntilMs = 0L

    fun isEdgeSelected(): Boolean = preferences.settings.value.provider == SpeechProvider.EDGE

    /** Playback path: honours the selected provider and the failure back-off. */
    fun synthesize(text: String): SpeechSynthesisOutcome {
        if (!isEdgeSelected() || text.isBlank()) return SpeechSynthesisOutcome.UseSystem
        val settings = preferences.settings.value
        val voice = EdgeTts.voiceFor(settings.edge, LanguageDetector.detect(text).language)
        val speed = TtsVoiceLogic.clampRate(settings.speechRate)
        cache.getIfPresent(text, voice)?.let { return SpeechSynthesisOutcome.Ready(it, fromCache = true, playbackSpeed = speed) }
        if (SystemClock.elapsedRealtime() < backoffUntilMs) return SpeechSynthesisOutcome.UseSystem
        return fetch(text, voice, speed)
    }

    /** "Test voice" in settings: always tries the network (no back-off), with exactly [voice]. */
    fun synthesizeForTest(text: String, voice: String): SpeechSynthesisOutcome {
        val normalized = EdgeTts.normalizeVoice(voice)
        val speed = TtsVoiceLogic.clampRate(preferences.settings.value.speechRate)
        cache.getIfPresent(text, normalized)?.let { return SpeechSynthesisOutcome.Ready(it, fromCache = true, playbackSpeed = speed) }
        return fetch(text, normalized, speed)
    }

    /** A cached file that turned out to be unplayable is dropped so the next attempt re-fetches it. */
    fun discard(file: File) = cache.remove(file)

    private fun fetch(text: String, voice: String, speed: Float): SpeechSynthesisOutcome {
        return try {
            val bytes = client.synthesize(text, voice)
            backoffUntilMs = 0L
            SpeechSynthesisOutcome.Ready(cache.put(text, voice, bytes), fromCache = false, playbackSpeed = speed)
        } catch (e: Exception) {
            // Never log the text; the exception message contains only status / protocol info
            android.util.Log.w("EdgeTts", "Edge TTS failed: ${e.javaClass.simpleName}: ${e.message}")
            backoffUntilMs = SystemClock.elapsedRealtime() + FAILURE_BACKOFF_MS
            SpeechSynthesisOutcome.Fallback(reasonFor(e))
        }
    }

    companion object {
        const val FAILURE_BACKOFF_MS = 60_000L

        /** Edge failure → what the user is told (#63): throttled, refused, timeout, offline, or generic. */
        fun reasonFor(e: Throwable): FallbackReason = when (e) {
            is EdgeTtsClient.HandshakeException -> TtsFailures.edgeHandshake(e.code)
            else -> TtsFailures.transport(e, FallbackReason.EDGE_TIMEOUT) ?: FallbackReason.EDGE_FAILED
        }
    }
}
