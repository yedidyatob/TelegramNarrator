package io.github.yedidyatob.telegramnarrator.core.audio

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.media.AudioAttributesCompat
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat
import io.github.yedidyatob.telegramnarrator.domain.audio.AudioFocusPolicy
import io.github.yedidyatob.telegramnarrator.domain.audio.AudioFocusPolicy.FocusChange

/**
 * Requests / abandons audio focus for spoken playback and reports focus changes (always on the main
 * thread) as [FocusChange].
 */
class AudioFocusController(
    context: Context,
    private val onChange: (FocusChange) -> Unit
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var request: AudioFocusRequestCompat? = null

    /** @return true if focus was granted. Calling again while already holding focus is cheap. */
    fun request(): Boolean {
        val req = request ?: AudioFocusRequestCompat.Builder(AudioManagerCompat.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributesCompat.Builder()
                    .setUsage(AudioAttributesCompat.USAGE_MEDIA)
                    .setContentType(AudioAttributesCompat.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setWillPauseWhenDucked(true) // we pause ourselves; no automatic ducking of speech
            .setOnAudioFocusChangeListener({ change ->
                when (change) {
                    AudioManager.AUDIOFOCUS_GAIN -> FocusChange.GAIN
                    AudioManager.AUDIOFOCUS_LOSS -> FocusChange.LOSS
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> FocusChange.LOSS_TRANSIENT
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> FocusChange.LOSS_TRANSIENT_CAN_DUCK
                    else -> null
                }?.let(onChange)
            }, Handler(Looper.getMainLooper()))
            .build()
            .also { request = it }
        return AudioManagerCompat.requestAudioFocus(audioManager, req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    fun abandon() {
        request?.let { AudioManagerCompat.abandonAudioFocusRequest(audioManager, it) }
        request = null
    }
}
