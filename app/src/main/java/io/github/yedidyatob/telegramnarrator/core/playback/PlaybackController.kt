package io.github.yedidyatob.telegramnarrator.core.playback

import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.yedidyatob.telegramnarrator.core.service.PlaybackService
import javax.inject.Inject
import javax.inject.Singleton

/** Sends the playback commands of the Home mini player and the Player screen to [PlaybackService]. */
@Singleton
class PlaybackController @Inject constructor(@ApplicationContext private val context: Context) {

    /** Starts (or restarts) reading the unread messages of [chatIds], in that order. */
    fun playChats(chatIds: List<Long>) {
        val intent = intent(PlaybackService.ACTION_PLAY_ALL).putExtra(PlaybackService.EXTRA_CHAT_IDS, chatIds.toLongArray())
        try {
            context.startForegroundService(intent)
        } catch (e: IllegalStateException) {
            // Android 12+: ForegroundServiceStartNotAllowedException if the app is no longer in the foreground
            // (a tap that raced with leaving the app). Playback is only ever started by the user in the app.
            android.util.Log.w("PlaybackController", "Could not start playback from the background", e)
        }
    }

    fun togglePause(isPaused: Boolean) = send(if (isPaused) PlaybackService.ACTION_RESUME else PlaybackService.ACTION_PAUSE)
    fun nextMessage() = send(PlaybackService.ACTION_SKIP_MSG)
    fun previousMessage() = send(PlaybackService.ACTION_PREVIOUS_MSG)
    fun nextChat() = send(PlaybackService.ACTION_SKIP_CHAT)
    fun stop() = send(PlaybackService.ACTION_STOP)

    /** Player "Up next": read the run's queue entry [queueIndex] and the chats after it (as Auto's queue does). */
    fun jumpToQueueIndex(queueIndex: Int) {
        try {
            context.startService(intent(PlaybackService.ACTION_JUMP_TO_CHAT).putExtra(PlaybackService.EXTRA_QUEUE_INDEX, queueIndex))
        } catch (e: IllegalStateException) {
            // Service gone and app in background: nothing to jump in
        }
    }

    private fun intent(action: String) = Intent(context, PlaybackService::class.java).setAction(action)

    // Commands only make sense while the service runs (it is in the foreground then), so startService is allowed
    private fun send(action: String) {
        try {
            context.startService(intent(action))
        } catch (e: IllegalStateException) {
            // The service already stopped and the app is in the background: nothing to control
        }
    }
}
