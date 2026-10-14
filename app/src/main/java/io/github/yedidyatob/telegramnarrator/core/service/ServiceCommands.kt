package io.github.yedidyatob.telegramnarrator.core.service

/** Decisions of [PlaybackService.onStartCommand] that do not need Android (#25). */
object ServiceCommands {
    /**
     * A control command (pause, skip, …) that reaches a service which is not playing — e.g. from a stale
     * notification after the process was killed, which recreates the service with startService — must not leave
     * an idle service behind and must never start playback by itself: the service stops again at once.
     */
    fun stopsIdleService(action: String?, foregroundStarted: Boolean): Boolean =
        action != PlaybackService.ACTION_PLAY_ALL && !foregroundStarted

    /** The wake lock is held only while narration is playing (not paused, not stopped). */
    fun holdsWakeLock(isPlaying: Boolean, isPaused: Boolean): Boolean = isPlaying && !isPaused
}
