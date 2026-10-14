package io.github.yedidyatob.telegramnarrator.core.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import io.github.yedidyatob.telegramnarrator.MainActivity
import io.github.yedidyatob.telegramnarrator.R

/**
 * The Media3 session around [NarrationPlayer]: notification buttons (stop, play/pause, next message,
 * next chat), the custom "next chat" command, the tap-to-open activity and the controller allow-list.
 */
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
class PlaybackSession(
    context: Context,
    player: NarrationPlayer,
    private val onNextChat: () -> Unit,
    private val onStop: () -> Unit
) {
    private val ownPackage = context.packageName

    private val stopButton = CommandButton.Builder(CommandButton.ICON_STOP)
        .setDisplayName(context.getString(R.string.home_btn_stop))
        .setSessionCommand(SessionCommand(COMMAND_STOP, Bundle.EMPTY))
        .setSlots(CommandButton.SLOT_BACK)
        .build()
    private val nextMessageButton = CommandButton.Builder(CommandButton.ICON_NEXT)
        .setDisplayName(context.getString(R.string.playback_skip_msg))
        .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT)
        .setSlots(CommandButton.SLOT_FORWARD)
        .build()
    private val nextChatButton = CommandButton.Builder(CommandButton.ICON_SKIP_FORWARD)
        .setDisplayName(context.getString(R.string.playback_skip_chat))
        .setSessionCommand(SessionCommand(COMMAND_NEXT_CHAT, Bundle.EMPTY))
        .setSlots(CommandButton.SLOT_OVERFLOW)
        .build()
    private val buttons = ImmutableList.of(stopButton, nextMessageButton, nextChatButton)

    val session: MediaSession = MediaSession.Builder(context, player)
        .setId("narration")
        .setSessionActivity(
            PendingIntent.getActivity(
                context, 4,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        )
        .setMediaButtonPreferences(buttons)
        .setCallback(Callback())
        .build()

    fun release() = session.release()

    private inner class Callback : MediaSession.Callback {
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            val platform = controller.controllerVersion == MediaSession.ControllerInfo.LEGACY_CONTROLLER_VERSION ||
                session.isMediaNotificationController(controller)
            if (!SessionControllerPolicy.isTrusted(controller.packageName, ownPackage, platform)) {
                return MediaSession.ConnectionResult.reject()
            }
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(SessionCommand(COMMAND_STOP, Bundle.EMPTY))
                .add(SessionCommand(COMMAND_NEXT_CHAT, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .setMediaButtonPreferences(buttons)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                COMMAND_NEXT_CHAT -> onNextChat()
                COMMAND_STOP -> onStop()
                else -> return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

    private companion object {
        const val COMMAND_NEXT_CHAT = "io.github.yedidyatob.telegramnarrator.NEXT_CHAT"
        const val COMMAND_STOP = "io.github.yedidyatob.telegramnarrator.STOP"
    }
}
