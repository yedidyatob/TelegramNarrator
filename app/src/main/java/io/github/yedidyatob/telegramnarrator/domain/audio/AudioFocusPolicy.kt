package io.github.yedidyatob.telegramnarrator.domain.audio

/**
 * Decides what the player does when another app takes or returns audio focus. Pure Kotlin (no Android
 * types) so it is unit-tested on the JVM; [io.github.yedidyatob.telegramnarrator.core.audio.AudioFocusController]
 * translates the real AudioManager callbacks into [FocusChange].
 *
 * We read speech, so when somebody else talks (navigation prompt, phone call, assistant) we pause instead
 * of ducking - two voices at once is unintelligible - and resume afterwards, but only for temporary losses
 * and only if the user did not pause in the meantime.
 */
class AudioFocusPolicy {

    enum class FocusChange { GAIN, LOSS, LOSS_TRANSIENT, LOSS_TRANSIENT_CAN_DUCK }

    enum class Action { NONE, PAUSE, RESUME }

    private var resumeOnGain = false

    /** [userPaused] = playback is already paused (by the user or by an earlier focus loss). */
    fun onFocusChange(change: FocusChange, userPaused: Boolean): Action = when (change) {
        FocusChange.LOSS -> {
            // Another app took over for good (music player, ...): never resume on our own
            resumeOnGain = false
            if (userPaused) Action.NONE else Action.PAUSE
        }
        FocusChange.LOSS_TRANSIENT, FocusChange.LOSS_TRANSIENT_CAN_DUCK ->
            if (userPaused) {
                Action.NONE
            } else {
                resumeOnGain = true
                Action.PAUSE
            }
        FocusChange.GAIN -> {
            val resume = resumeOnGain && userPaused
            resumeOnGain = false
            if (resume) Action.RESUME else Action.NONE
        }
    }

    /** The user paused / resumed / stopped explicitly: forget any pending automatic resume. */
    fun onUserAction() {
        resumeOnGain = false
    }

    /** Headphones unplugged / Bluetooth lost ("becoming noisy"): pause and do not auto-resume. */
    fun onBecomingNoisy() {
        resumeOnGain = false
    }
}
