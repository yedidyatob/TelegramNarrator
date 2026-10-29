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
import androidx.media3.session.MediaConstants
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionCommands
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import io.github.yedidyatob.telegramnarrator.MainActivity
import io.github.yedidyatob.telegramnarrator.R
import io.github.yedidyatob.telegramnarrator.core.artwork.ChatArtworkProvider
import io.github.yedidyatob.telegramnarrator.core.diagnostics.AutoLog

/**
 * The Media3 library session around [NarrationPlayer]: notification buttons (stop, play/pause, next message,
 * next chat), the custom "next chat" command, the tap-to-open activity, the controller allow-list and the
 * Android Auto browse tree (#41, see [AutoBrowseTree]) with chat artwork. [library] loads the Home list's unread chats;
 * [onPlayChats] starts the same narration as the app's Play.
 */
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
class PlaybackSession(
    context: Context,
    player: NarrationPlayer,
    private val onNextChat: () -> Unit,
    private val onStop: () -> Unit,
    private val library: () -> ListenableFuture<AutoBrowseTree.Library>,
    private val onPlayChats: (List<Long>) -> Unit
) {
    private val appContext = context.applicationContext
    private val playAllTitle = context.getString(R.string.auto_play_all)
    private val signInTitle = context.getString(R.string.auto_sign_in_on_phone)
    private val signInSubtitle = context.getString(R.string.auto_sign_in_on_phone_detail)
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
                // Opens the Player (#20) in the existing activity (singleTop -> onNewIntent), or cold-starts it
                Intent(context, MainActivity::class.java)
                    .setAction(io.github.yedidyatob.telegramnarrator.ui.navigation.AppRoute.ACTION_OPEN_PLAYER)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
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
            val access = SessionControllerPolicy.access(controller.packageName, ownPackage, platform)
            AutoLog.event(
                "onConnect package=${controller.packageName} uid=${controller.uid} " +
                    "version=${controller.controllerVersion} legacy=${controller.controllerVersion == MediaSession.ControllerInfo.LEGACY_CONTROLLER_VERSION} " +
                    "trustedForMediaControl=${controller.isTrusted} access=$access"
            )
            if (access == SessionControllerPolicy.Access.REJECT) return MediaSession.ConnectionResult.reject()
            val sessionCommands = baseCommandsFor(access).buildUpon()
                .add(SessionCommand(COMMAND_STOP, Bundle.EMPTY))
                .add(SessionCommand(COMMAND_NEXT_CHAT, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .setMediaButtonPreferences(buttons)
                .build()
        }

        override fun onPlayerCommandRequest(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            playerCommand: Int
        ): Int {
            lastCommandFrom = controller.packageName
            if (playerCommand == Player.COMMAND_PLAY_PAUSE || playerCommand == Player.COMMAND_STOP) {
                AutoLog.event("player command=$playerCommand from=${controller.packageName} uid=${controller.uid}")
            }
            return SessionResult.RESULT_SUCCESS
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: MediaLibraryService.LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val allowed = canBrowse(browser)
            AutoLog.event("onGetLibraryRoot package=${browser.packageName} recent=${params?.isRecent} allowed=$allowed")
            if (!allowed) return Futures.immediateFuture(LibraryResult.ofError(SessionError.ERROR_PERMISSION_DENIED))
            return Futures.immediateFuture(LibraryResult.ofItem(rootItem(), rootParams()))
        }

        /**
         * Android Auto's own Play control (media card / mini player / steering wheel) with nothing loaded, and
         * "Hey Google, play Narri": read all unread chats, the app's Play all. Only for this app and
         * Auto, and only for an actual playback request: a car's Bluetooth auto-play on connect (a platform
         * controller) or System UI's resumption card never starts reading on its own.
         */
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            isForPlayback: Boolean
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val allowed = isForPlayback && canBrowse(controller)
            AutoLog.event("onPlaybackResumption package=${controller.packageName} forPlayback=$isForPlayback allowed=$allowed")
            if (!allowed) return Futures.immediateFailedFuture(UnsupportedOperationException("No resumption"))
            return Futures.transform(library(), { lib ->
                val chats = (lib as? AutoBrowseTree.Library.Ready)?.unread
                    ?: throw IllegalStateException("Telegram is not signed in")
                val ids = AutoBrowseTree.chatsToPlay(AutoBrowseTree.PLAY_ALL_ID, chats).orEmpty()
                AutoLog.event("onPlaybackResumption play chats=${ids.size}")
                if (ids.isEmpty()) throw IllegalStateException("Nothing unread")
                onPlayChats(ids)
                MediaSession.MediaItemsWithStartPosition(
                    listOf(MediaItem.Builder().setMediaId(AutoBrowseTree.PLAY_ALL_ID).build()), 0, 0L
                )
            }, Runnable::run)
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val allowed = canBrowse(browser)
            AutoLog.event("onGetItem package=${browser.packageName} id=${AutoBrowseTree.logId(mediaId)} allowed=$allowed")
            if (!allowed) return Futures.immediateFuture(LibraryResult.ofError(SessionError.ERROR_PERMISSION_DENIED))
            if (mediaId == AutoBrowseTree.ROOT_ID) return Futures.immediateFuture(LibraryResult.ofItem(rootItem(), null))
            return Futures.transform(library(), { lib ->
                AutoBrowseTree.rootChildren(lib, signInTitle, playAllTitle).firstOrNull { it.mediaId == mediaId }
                    ?.let { LibraryResult.ofItem(toItem(it), null) }
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
            val allowed = canBrowse(browser)
            AutoLog.event("onGetChildren package=${browser.packageName} parent=${AutoBrowseTree.logId(parentId)} page=$page pageSize=$pageSize allowed=$allowed")
            if (!allowed) return Futures.immediateFuture(LibraryResult.ofError(SessionError.ERROR_PERMISSION_DENIED))
            if (parentId != AutoBrowseTree.ROOT_ID) return Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.of(), params))
            val result = Futures.transform(library(), { lib ->
                val nodes = AutoBrowseTree.rootChildren(lib, signInTitle, playAllTitle)
                val range = AutoBrowseTree.pageRange(nodes.size, page, pageSize)
                AutoLog.event("onGetChildren result library=${lib.logState()} items=${nodes.size} returned=${range.last - range.first + 1}")
                LibraryResult.ofItemList(ImmutableList.copyOf(nodes.slice(range).map(::toItem)), params)
            }, Runnable::run)
            return Futures.catching(result, Throwable::class.java, { e ->
                AutoLog.error("onGetChildren failed", e)
                LibraryResult.ofError<ImmutableList<MediaItem>>(SessionError.ERROR_UNKNOWN)
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
            AutoLog.event(
                "onSetMediaItems package=${controller.packageName} items=${mediaItems.size} " +
                    "id=${item?.mediaId?.let(AutoBrowseTree::logId)} search=${item?.requestMetadata?.searchQuery != null}"
            )
            if (!canBrowse(controller) || item == null) {
                return Futures.immediateFailedFuture(UnsupportedOperationException("Not allowed"))
            }
            return Futures.transform(library(), { lib ->
                // Signed out: nothing to read (the browse root shows "Sign in on your phone")
                val chats = (lib as? AutoBrowseTree.Library.Ready)?.unread
                    ?: throw IllegalStateException("Telegram is not signed in")
                val search = item.requestMetadata.searchQuery
                val ids = if (item.mediaId.isEmpty() || search != null) {
                    AutoBrowseTree.chatsForSearch(search, chats)
                } else {
                    AutoBrowseTree.chatsToPlay(item.mediaId, chats) ?: throw IllegalArgumentException("Unknown media id")
                }
                AutoLog.event("onSetMediaItems play chats=${ids.size}")
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

    /** Auto caches browse lists: tell subscribed browsers whenever the unread list changes. */
    fun notifyUnreadChanged(itemCount: Int) {
        session.notifyChildrenChanged(AutoBrowseTree.ROOT_ID, itemCount, null)
    }

    /** Root extras: playable chats as list rows with their photo as the row icon. */
    private fun rootParams(): MediaLibraryService.LibraryParams = MediaLibraryService.LibraryParams.Builder()
        .setExtras(Bundle().apply {
            putBoolean(CONTENT_STYLE_SUPPORTED, true)
            putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE, MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM)
            putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE, MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM)
        })
        .build()

    private val granted = HashSet<String>()

    /** Package of the controller whose player command is being handled (diagnostics: who paused). */
    var lastCommandFrom: String? = null
        private set

    /** Chat photo (or initials) as a content:// URI Auto can read; granted once per URI. */
    private fun artworkFor(chatId: Long): android.net.Uri = grantOnce(ChatArtworkProvider.uriFor(appContext, chatId))

    private fun grantOnce(uri: android.net.Uri): android.net.Uri {
        if (granted.add(uri.toString())) {
            ChatArtworkProvider.grantToAuto(appContext, uri, SessionControllerPolicy.ANDROID_AUTO_PACKAGES)
        }
        return uri
    }

    private fun canBrowse(controller: MediaSession.ControllerInfo) =
        SessionControllerPolicy.canBrowse(controller.packageName, ownPackage)

    private fun rootItem(): MediaItem = MediaItem.Builder()
        .setMediaId(AutoBrowseTree.ROOT_ID)
        .setMediaMetadata(MediaMetadata.Builder().setIsBrowsable(true).setIsPlayable(false)
            .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED).build())
        .build()

    private fun toItem(node: AutoBrowseTree.Node): MediaItem = MediaItem.Builder()
        .setMediaId(node.mediaId)
        .setMediaMetadata(MediaMetadata.Builder()
            .setTitle(node.title)
            .setSubtitle(if (node.playable) unreadLabel(node.unreadCount) else signInSubtitle)
            .setIsBrowsable(false)
            .setIsPlayable(node.playable)
            .setMediaType(if (node.playable) MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE else MediaMetadata.MEDIA_TYPE_MIXED)
            .apply {
                when {
                    node.chatId != null -> setArtworkUri(artworkFor(node.chatId))
                    node.mediaId == AutoBrowseTree.PLAY_ALL_ID -> setArtworkUri(grantOnce(ChatArtworkProvider.playAllIconUri(appContext)))
                }
            }
            .build())
        .build()

    private val resources = context.resources
    private fun unreadLabel(count: Int) = resources.getQuantityString(R.plurals.auto_unread_count, count, count)

    internal companion object {
        /**
         * Library commands (get root / children / item, search) must be granted explicitly: with only
         * DEFAULT_SESSION_COMMANDS, Media3's legacy MediaBrowserService stub answers Android Auto's getRoot with
         * null and Auto shows "<app> doesn't seem to be working right now" (the #81 / #82 field failure).
         */
        fun baseCommandsFor(access: SessionControllerPolicy.Access): SessionCommands =
            if (access == SessionControllerPolicy.Access.LIBRARY) {
                MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
            } else {
                MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
            }

        /** androidx.media.utils.MediaConstants.BROWSER_SERVICE_EXTRAS_KEY_CONTENT_STYLE_SUPPORTED (legacy key). */
        const val CONTENT_STYLE_SUPPORTED = "android.media.browse.CONTENT_STYLE_SUPPORTED"

        const val COMMAND_NEXT_CHAT = "io.github.yedidyatob.telegramnarrator.NEXT_CHAT"
        const val COMMAND_STOP = "io.github.yedidyatob.telegramnarrator.STOP"
    }
}
