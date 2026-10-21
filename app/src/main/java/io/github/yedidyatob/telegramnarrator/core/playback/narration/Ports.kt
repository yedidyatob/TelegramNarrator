package io.github.yedidyatob.telegramnarrator.core.playback.narration

import io.github.yedidyatob.telegramnarrator.domain.audio.PlaybackItem
import io.github.yedidyatob.telegramnarrator.domain.audio.ReadCheckpointer
import io.github.yedidyatob.telegramnarrator.domain.sponsored.SponsoredAd
import io.github.yedidyatob.telegramnarrator.domain.tts.FallbackReason
import io.github.yedidyatob.telegramnarrator.domain.tts.SpeechProvider
import io.github.yedidyatob.telegramnarrator.domain.tts.SpeechSynthesisOutcome
import java.io.File

// Ports of NarrationController (#12): the Android side (TextToSpeech, MediaPlayer, TDLib, notifications) is
// behind these interfaces, so the playback logic is plain Kotlin and unit tested with fakes. Callbacks may
// arrive on any thread; the controller moves them onto its own single thread.

/** Speaking text: the system engine and the network engines (Gemini / Edge). */
interface SpeechPort {
    /** The engine the user selected. */
    val provider: SpeechProvider

    /** System TTS speech rate (1.0 = normal), for estimating the position of an engine without word ranges. */
    val speechRate: Float

    /**
     * Snapshot of every voice setting (engine, voice, rate, ...). A paused system-TTS message only resumes
     * mid-text while this is unchanged; otherwise it restarts.
     */
    fun settingsKey(): Any

    /** Suspends until the system engine is initialized. */
    suspend fun awaitReady()

    /**
     * System TTS. [onDone] (true = spoken to the end) runs once, unless [stop] is called first. The language /
     * voice is picked from [languageSource] (the whole message, also when only its rest is spoken). [onRange]
     * gets the offset into [text] of each word as it starts, if the engine reports word ranges.
     */
    fun speak(
        text: String,
        languageSource: String,
        /** The chat being read: its detected language picks the voice (#89); null for non-chat speech. */
        chatId: Long?,
        onStart: () -> Unit,
        onRange: (start: Int) -> Unit,
        onDone: (completed: Boolean) -> Unit
    )

    /** Stops the system engine; the pending [speak] callback is dropped. */
    fun stop()

    /** Network synthesis into the disk cache (blocking; the controller calls it on its I/O dispatcher). */
    fun synthesize(provider: SpeechProvider, text: String, chatId: Long?): SpeechSynthesisOutcome

    /** The chat's language (detected, else the device language), for spoken phrases like the sponsored cue. */
    fun chatLanguage(chatId: Long): String

    /** Drops an unplayable cached file so it is fetched again next time. */
    fun discard(file: File)
}

/** Short cues played through the audio slot. */
enum class Ding { CHAT_BOUNDARY, END_OF_MESSAGES }

sealed interface AudioSource {
    data class FilePath(val path: String) : AudioSource
    data class Tone(val ding: Ding) : AudioSource
}

/** The single media-player slot (voice notes, synthesized speech, dings). */
interface AudioPort {
    /**
     * Releases whatever played before and starts [source] at [speed]. Returns false when it could not be
     * started (no callback then); otherwise exactly one of [onComplete] / [onError] runs later unless
     * [release] is called first.
     */
    fun play(source: AudioSource, speed: Float, onComplete: () -> Unit, onError: () -> Unit): Boolean

    /** Pauses the playing file in place; false when nothing could be paused. */
    fun pause(): Boolean

    /** Continues a file paused with [pause]; false when that is not possible. */
    fun resume(): Boolean

    /** Stops and releases the current file (its callbacks never run). */
    fun release()

    /** Whether a file is loaded (playing or paused). */
    val hasPlayer: Boolean
}

/** Downloads a voice note; null when it cannot be downloaded. */
fun interface VoiceNotePort {
    suspend fun voiceFilePath(fileId: Int): String?
}

/** Marks played messages as read in Telegram ([MarkMessagesRead]); must not be cancelled with playback. */
fun interface ReadMarkerPort {
    fun markRead(batch: ReadCheckpointer.Batch)
}

/** Official sponsored messages (Telegram API ToS 3.3). */
interface SponsoredPort {
    /** Warms the chat's ads cache when the chat opens. */
    fun prefetch(chatId: Long)
    suspend fun adFor(chatId: Long): SponsoredAd?
    fun reportViewed(ad: SponsoredAd)
}

/** Tells the user about network-voice failures (one message per episode, #63). */
interface TtsFailurePort {
    fun onFailure(reason: FallbackReason)
    fun onPrefetchFailure(reason: FallbackReason)
    fun onNetworkSuccess()
}

/** What the service does for the controller: notification, media session, focus, noisy receiver, stopping. */
interface PlaybackHost {
    /** A new item starts: update the status line / notification. */
    fun onItemStarted(item: PlaybackItem)

    /** A sponsored message is about to be read: "Sponsored · title" status. */
    fun onSponsoredStarted(ad: SponsoredAd, chatName: String)

    /** Playing / paused changed: media session state and notification. */
    fun onStateChanged(isPlaying: Boolean, isPaused: Boolean)

    /** Playback ended (stop, end of the queue, error): leave the foreground and stop the service. */
    fun onStopped()

    fun requestAudioFocus()
    fun abandonAudioFocus()
    fun registerNoisyReceiver()
    fun unregisterNoisyReceiver()

}
