package io.github.yedidyatob.telegramnarrator.core.service

import android.content.Context
import android.os.PowerManager

/**
 * Keeps the CPU awake while narration is actively playing (not while paused or stopped), #25.
 *
 * The media-playback foreground service keeps the process alive and exempts it from Doze / battery-saver network
 * restrictions, and the audio server keeps the CPU awake while sound is actually coming out. Between items it is
 * not: synthesizing the next message over the network (Edge / Gemini), downloading a voice note through TDLib,
 * fetching a sponsored message and the short waits between items run with no audio playing, and with the screen
 * off the CPU can suspend there, so the next message starts late or never (seen with OEM battery optimization).
 * This is the same reason ExoPlayer offers `WAKE_MODE_NETWORK`.
 *
 * A Wi-Fi lock is not taken: requests are short bursts, the foreground service already keeps network access, and
 * Wi-Fi power save only adds a little latency.
 *
 * The lock has a timeout so a missed release can never drain the battery; it is renewed on every item.
 */
class PlaybackWakeLock(context: Context) {
    private val lock: PowerManager.WakeLock =
        (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TelegramNarrator:playback")
            .apply { setReferenceCounted(false) }

    /** Holds the lock while [active], renewing its timeout; releases it otherwise. */
    fun update(active: Boolean) {
        if (active) lock.acquire(TIMEOUT_MS) else if (lock.isHeld) lock.release()
    }

    companion object {
        /** Longer than any single item (long voice notes / messages); renewed when the next item starts. */
        const val TIMEOUT_MS = 15 * 60 * 1000L
    }
}
