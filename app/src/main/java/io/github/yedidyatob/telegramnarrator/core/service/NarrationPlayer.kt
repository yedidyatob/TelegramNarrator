package io.github.yedidyatob.telegramnarrator.core.service

import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * A Media3 [Player] façade over the narration (#12 phase 2). It holds no playback logic: Media3 surfaces
 * (notification, lock screen, Bluetooth / headset buttons, later Android Auto) read its state and send
 * transport commands, and [commands] forwards them to the NarrationController.
 *
 * The playlist is the run's chats ([NarrationQueue]: one entry per chat, current = the chat being read), so
 * Android Auto's queue shows every chat. Each entry is a "live", dynamic, seekable item: its length is
 * unknown, and next / previous mean "next / previous message", not playlist moves. Selecting a queue entry
 * ([Commands.jumpToQueueIndex]) jumps to that chat.
 */
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
class NarrationPlayer(
    private val commands: Commands,
    private val appName: String
) : SimpleBasePlayer(Looper.getMainLooper()) {

    /** Transport commands from Media3, already mapped to narration actions by [NarrationTransport]. */
    interface Commands {
        fun perform(action: NarrationTransport.Action)

        /** Auto's queue: read the chat at [index] of the queue and the ones after it. */
        fun jumpToQueueIndex(index: Int)
    }

    private var queue: List<NarrationQueue.Entry> = emptyList()
    private var currentChatId: Long? = null

    /** The run's chats and the one being read (main thread). */
    fun setQueue(entries: List<NarrationQueue.Entry>, current: Long? = currentChatId) {
        if (entries == queue && current == currentChatId) return
        queue = entries
        currentChatId = current
        invalidateState()
    }

    fun setCurrentChat(chatId: Long?) = setQueue(queue, chatId)

    private var phase = NarrationPhase.IDLE
    private var title = ""
    private var artwork: android.net.Uri? = null

    /** The current chat's photo / initials (content:// from ChatArtworkProvider) for the notification and Auto. */
    fun setArtwork(uri: android.net.Uri?) {
        if (uri == artwork) return
        artwork = uri
        invalidateState()
    }

    /** Called by the service from its PlaybackHost callbacks (main thread). */
    fun update(phase: NarrationPhase, title: String = this.title) {
        if (phase == this.phase && title == this.title) return
        this.phase = phase
        this.title = title
        invalidateState()
    }

    override fun getState(): State {
        val builder = State.Builder()
            .setAvailableCommands(AVAILABLE_COMMANDS)
            .setPlayWhenReady(NarrationTransport.playWhenReady(phase), Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setPlaybackState(NarrationTransport.playbackState(phase))
        if (phase == NarrationPhase.IDLE) return builder.build()
        val entries = queue.ifEmpty { listOf(NarrationQueue.Entry(PLACEHOLDER_CHAT, "")) }
        val current = NarrationQueue.currentIndex(entries, currentChatId)
        val items = entries.mapIndexed { index, entry ->
            val isCurrent = index == current
            val metadata = MediaMetadata.Builder()
                .setTitle(entry.title.ifEmpty { title.ifEmpty { appName } })
                // The current entry shows the narration's status ("From Dana", "Sponsored"…) under the title
                .setArtist(if (isCurrent && title.isNotEmpty() && title != entry.title) title else appName)
                .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE)
                .setArtworkUri(if (entry.chatId == PLACEHOLDER_CHAT) artwork else artworkFor(entry.chatId))
                .build()
            val id = if (entry.chatId == PLACEHOLDER_CHAT) MEDIA_ID else entry.mediaId
            MediaItemData.Builder(id)
                .setMediaItem(MediaItem.Builder().setMediaId(id).setMediaMetadata(metadata).build())
                .setMediaMetadata(metadata)
                .setLiveConfiguration(MediaItem.LiveConfiguration.UNSET)
                .setIsDynamic(true)
                .setIsSeekable(true)
                .build()
        }
        return builder.setPlaylist(items).setCurrentMediaItemIndex(current).build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> =
        perform(if (playWhenReady) NarrationTransport.Action.RESUME else NarrationTransport.Action.PAUSE)

    override fun handleStop(): ListenableFuture<*> = perform(NarrationTransport.Action.STOP)

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        NarrationTransport.forSeek(seekCommand)?.let { return perform(it) }
        // Auto's queue (skipToQueueItem) and seekTo(index, …): jump to that chat
        if (NarrationTransport.isQueueSelection(seekCommand) && queue.isNotEmpty() &&
            mediaItemIndex != NarrationQueue.currentIndex(queue, currentChatId)
        ) {
            commands.jumpToQueueIndex(mediaItemIndex)
        }
        return Futures.immediateVoidFuture()
    }

    /** Android Auto's setMediaItems + prepare: the library session already started the narration. */
    override fun handleSetMediaItems(mediaItems: MutableList<MediaItem>, startIndex: Int, startPositionMs: Long): ListenableFuture<*> =
        Futures.immediateVoidFuture()

    override fun handlePrepare(): ListenableFuture<*> = Futures.immediateVoidFuture()

    /** Artwork URIs of queue entries other than the current one come from [artworkForChat]. */
    var artworkForChat: (Long) -> android.net.Uri? = { null }

    private fun artworkFor(chatId: Long): android.net.Uri? =
        if (chatId == currentChatId && artwork != null) artwork else artworkForChat(chatId)

    private fun perform(action: NarrationTransport.Action): ListenableFuture<*> {
        commands.perform(action)
        // The narration reports its new state through the service, which calls update()
        return Futures.immediateVoidFuture()
    }

    private companion object {
        const val MEDIA_ID = "narration"
        /** Queue not known yet (the app's Play before the run's chats are resolved). */
        const val PLACEHOLDER_CHAT = Long.MIN_VALUE
        val AVAILABLE_COMMANDS: Player.Commands = Player.Commands.Builder()
            .addAll(
                Player.COMMAND_PLAY_PAUSE,
                Player.COMMAND_STOP,
                Player.COMMAND_PREPARE,
                Player.COMMAND_SET_MEDIA_ITEM,
                Player.COMMAND_CHANGE_MEDIA_ITEMS,
                Player.COMMAND_SEEK_TO_NEXT,
                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_TO_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_DEFAULT_POSITION,
                Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                Player.COMMAND_GET_TIMELINE,
                Player.COMMAND_GET_METADATA
            )
            .build()
    }
}
