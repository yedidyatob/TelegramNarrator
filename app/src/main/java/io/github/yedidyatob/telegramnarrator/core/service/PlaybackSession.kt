package io.github.yedidyatob.telegramnarrator.core.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import io.github.yedidyatob.telegramnarrator.MainActivity
import io.github.yedidyatob.telegramnarrator.R
import io.github.yedidyatob.telegramnarrator.domain.model.Chat

/**
 * The Media3 library session around [NarrationPlayer]: notification buttons (stop, play/pause, next message,
 * next chat), the custom "next chat" command, the tap-to-open activity, the controller allow-list and the
 * Android Auto browse tree (#41, see [AutoBrowseTree]). [unreadChats] loads the Home list's unread chats;
 * [onPlayChats] starts the same narration as the app's Play.
 */
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
class PlaybackSession(
    context: Context,
    player: NarrationPlayer,
    private val onNextChat: () -> Unit,
    private val onStop: () -> Unit,
    private val unreadChats: () -> ListenableFuture<List<Chat>>,
    private val onPlayChats: (List<Long>) -> Unit
) {
    private val playAllTitle = context.getString(R.string.auto_play_all)
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

    val session: MediaLibrarySession = MediaLibrarySession.Builder(context as MediaLibraryService, player, Callback())
        .setId("narration")
        .setSessionActivity(
            PendingIntent.getActivity(
                context, 4,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        )
        .setMediaButtonPreferences(buttons)
        .build()

    fun release() = session.release()

    private inner class Callback : MediaLibrarySession.Callback {
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

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: MediaLibraryService.LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            if (!canBrowse(browser)) return Futures.immediateFuture(LibraryResult.ofError(SessionError.ERROR_PERMISSION_DENIED))
            return Futures.immediateFuture(LibraryResult.ofItem(rootItem(), params))
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> {
            if (!canBrowse(browser)) return Futures.immediateFuture(LibraryResult.ofError(SessionError.ERROR_PERMISSION_DENIED))
            if (mediaId == AutoBrowseTree.ROOT_ID) return Futures.immediateFuture(LibraryResult.ofItem(rootItem(), null))
            return Futures.transform(unreadChats(), { chats ->
                AutoBrowseTree.rootChildren(chats, playAllTitle).firstOrNull { it.mediaId == mediaId }
                    ?.let { LibraryResult.ofItem(playable(it), null) }
                    ?: LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
            }, Runnable::run)
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: MediaLibraryService.LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            if (!canBrowse(browser)) return Futures.immediateFuture(LibraryResult.ofError(SessionError.ERROR_PERMISSION_DENIED))
            if (parentId != AutoBrowseTree.ROOT_ID) return Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.of(), params))
            return Futures.transform(unreadChats(), { chats ->
                val nodes = AutoBrowseTree.rootChildren(chats, playAllTitle).map(::playable)
                val from = (page * pageSize).coerceAtMost(nodes.size)
                val to = if (pageSize <= 0) nodes.size else (from + pageSize).coerceAtMost(nodes.size)
                LibraryResult.ofItemList(ImmutableList.copyOf(nodes.subList(from, to)), params)
            }, Runnable::run)
        }

        /** Auto plays a node with setMediaItems: start the narration for its chats instead of loading media. */
        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val item = mediaItems.getOrNull(startIndex.coerceAtLeast(0))
            if (!canBrowse(controller) || item == null) {
                return Futures.immediateFailedFuture(UnsupportedOperationException("Not allowed"))
            }
            return Futures.transform(unreadChats(), { chats ->
                val search = item.requestMetadata.searchQuery
                val ids = if (item.mediaId.isEmpty() || search != null) {
                    AutoBrowseTree.chatsForSearch(search, chats)
                } else {
                    AutoBrowseTree.chatsToPlay(item.mediaId, chats) ?: throw IllegalArgumentException("Unknown media id")
                }
                onPlayChats(ids)
                MediaSession.MediaItemsWithStartPosition(mediaItems, startIndex, startPositionMs)
            }, Runnable::run)
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

    private fun canBrowse(controller: MediaSession.ControllerInfo) =
        SessionControllerPolicy.canBrowse(controller.packageName, ownPackage)

    private fun rootItem(): MediaItem = MediaItem.Builder()
        .setMediaId(AutoBrowseTree.ROOT_ID)
        .setMediaMetadata(MediaMetadata.Builder().setIsBrowsable(true).setIsPlayable(false)
            .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED).build())
        .build()

    private fun playable(node: AutoBrowseTree.Node): MediaItem = MediaItem.Builder()
        .setMediaId(node.mediaId)
        .setMediaMetadata(MediaMetadata.Builder()
            .setTitle(node.title)
            .setSubtitle(unreadLabel(node.unreadCount))
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE)
            .build())
        .build()

    private val resources = context.resources
    private fun unreadLabel(count: Int) = resources.getQuantityString(R.plurals.auto_unread_count, count, count)

    private companion object {
        const val COMMAND_NEXT_CHAT = "io.github.yedidyatob.telegramnarrator.NEXT_CHAT"
        const val COMMAND_STOP = "io.github.yedidyatob.telegramnarrator.STOP"
    }
}
