package io.github.yedidyatob.telegramnarrator.core.service

import android.content.Context
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import io.github.yedidyatob.telegramnarrator.core.playback.narration.NarrationController

/**
 * The legacy MediaSessionCompat (lock screen, Bluetooth / headset buttons): forwards transport buttons to the
 * [NarrationController] and mirrors its playing / paused state. Next = next message, previous = next chat
 * (unchanged mapping). Media3 is a documented follow-up (#12 phase 2).
 */
class PlaybackMediaSession(context: Context, narration: () -> NarrationController) {
    val session = MediaSessionCompat(context, "PlaybackService").apply {
        setCallback(object : MediaSessionCompat.Callback() {
            override fun onPlay() = narration().userResume()
            override fun onPause() = narration().userPause()
            override fun onStop() = narration().stop()
            override fun onSkipToNext() = narration().skipMessage()
            override fun onSkipToPrevious() = narration().skipChat()
        })
        isActive = true
    }

    fun setState(isPlaying: Boolean, isPaused: Boolean) {
        val state = when {
            !isPlaying -> PlaybackStateCompat.STATE_STOPPED
            isPaused -> PlaybackStateCompat.STATE_PAUSED
            else -> PlaybackStateCompat.STATE_PLAYING
        }
        session.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE or PlaybackStateCompat.ACTION_STOP or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                )
                .setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, if (state == PlaybackStateCompat.STATE_PLAYING) 1f else 0f)
                .build()
        )
    }

    fun release() {
        session.isActive = false
        session.release()
    }
}
