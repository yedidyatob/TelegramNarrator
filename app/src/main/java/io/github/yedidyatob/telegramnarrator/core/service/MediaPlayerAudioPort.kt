package io.github.yedidyatob.telegramnarrator.core.service

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import io.github.yedidyatob.telegramnarrator.R
import io.github.yedidyatob.telegramnarrator.core.playback.narration.AudioPort
import io.github.yedidyatob.telegramnarrator.core.playback.narration.AudioSource
import io.github.yedidyatob.telegramnarrator.core.playback.narration.Ding

/**
 * The single [MediaPlayer] slot of the player: voice notes and synthesized speech (USAGE_MEDIA / SPEECH) and the
 * raw WAV dings (SONIFICATION). Used on the controller's thread (main); local files prepare synchronously.
 */
class MediaPlayerAudioPort(private val context: Context) : AudioPort {
    private var player: MediaPlayer? = null

    override val hasPlayer: Boolean get() = player != null

    override fun play(source: AudioSource, speed: Float, onComplete: () -> Unit, onError: () -> Unit): Boolean {
        release()
        val mp = MediaPlayer()
        return try {
            val contentType = when (source) {
                is AudioSource.FilePath -> AudioAttributes.CONTENT_TYPE_SPEECH
                is AudioSource.Tone -> AudioAttributes.CONTENT_TYPE_SONIFICATION
            }
            mp.setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(contentType).build()
            )
            when (source) {
                is AudioSource.FilePath -> mp.setDataSource(source.path)
                is AudioSource.Tone -> context.resources.openRawResourceFd(rawRes(source.ding)).use { afd ->
                    mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                }
            }
            mp.setOnCompletionListener {
                it.release()
                if (player === it) player = null
                onComplete()
            }
            mp.setOnErrorListener { p, _, _ ->
                p.release()
                if (player === p) player = null
                onError()
                true
            }
            mp.prepare()
            player = mp
            mp.start()
            // After start(): on a prepared/paused player a non-zero speed would itself start playback
            if (speed != 1f) {
                try {
                    mp.playbackParams = mp.playbackParams.setSpeed(speed)
                } catch (e: Exception) {
                    // Speed change unsupported on this device: keep normal speed
                }
            }
            true
        } catch (e: Exception) {
            mp.release()
            if (player === mp) player = null
            false
        }
    }

    override fun pause(): Boolean = try {
        player?.pause()
        player != null
    } catch (e: IllegalStateException) {
        false
    }

    override fun resume(): Boolean = try {
        player?.start()
        player != null
    } catch (e: IllegalStateException) {
        false
    }

    override fun release() {
        val mp = player ?: return
        player = null
        try {
            mp.stop()
        } catch (e: IllegalStateException) {
            // Not started / already released: nothing to stop
        }
        mp.release()
    }

    private fun rawRes(ding: Ding): Int = when (ding) {
        Ding.CHAT_BOUNDARY -> R.raw.chat_boundary_ding
        Ding.END_OF_MESSAGES -> R.raw.end_of_messages_ding
    }
}
