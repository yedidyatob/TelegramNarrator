package io.github.yedidyatob.telegramnarrator.core.service

import androidx.media3.common.Player

/** What the Media3 session shows, derived from the narration's PlaybackHost callbacks. */
enum class NarrationPhase { IDLE, PREPARING, PLAYING, PAUSED }

/** Pure mapping between the narration and Media3's Player vocabulary (unit-tested). */
object NarrationTransport {
    enum class Action { RESUME, PAUSE, STOP, NEXT_MESSAGE, NEXT_CHAT, PREVIOUS_MESSAGE }

    /** Phase after a PlaybackHost.onStateChanged. "Preparing" lasts from Play until the queue is built. */
    fun phaseOf(isPlaying: Boolean, isPaused: Boolean, current: NarrationPhase): NarrationPhase = when {
        isPlaying && isPaused -> NarrationPhase.PAUSED
        isPlaying -> NarrationPhase.PLAYING
        current == NarrationPhase.PREPARING -> NarrationPhase.PREPARING
        else -> NarrationPhase.IDLE
    }

    fun playWhenReady(phase: NarrationPhase): Boolean = phase == NarrationPhase.PREPARING || phase == NarrationPhase.PLAYING

    fun playbackState(phase: NarrationPhase): Int = when (phase) {
        NarrationPhase.IDLE -> Player.STATE_IDLE
        NarrationPhase.PREPARING -> Player.STATE_BUFFERING
        NarrationPhase.PLAYING, NarrationPhase.PAUSED -> Player.STATE_READY
    }

    /**
     * Next = next message; previous = previous message (same as the in-app previous button). Next chat is
     * the custom session command on the notification overflow, not a transport seek.
     */
    fun forSeek(seekCommand: Int): Action? = when (seekCommand) {
        Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> Action.NEXT_MESSAGE
        Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> Action.PREVIOUS_MESSAGE
        else -> null
    }
}
