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
 * The narration is one endless "live" item: its length is unknown and next / previous mean "next message"
 * and "next chat", not playlist moves. A live, dynamic, seekable item is what makes Media3 route
 * seekToNext / seekToPrevious to [handleSeek] even though the playlist has a single entry.
 */
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
class NarrationPlayer(
    private val commands: Commands,
    private val appName: String
) : SimpleBasePlayer(Looper.getMainLooper()) {

    /** Transport commands from Media3, already mapped to narration actions by [NarrationTransport]. */
    fun interface Commands {
        fun perform(action: NarrationTransport.Action)
    }

    private var phase = NarrationPhase.IDLE
    private var title = ""

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
        val metadata = MediaMetadata.Builder()
            .setTitle(title.ifEmpty { appName })
            .setArtist(appName)
            .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE)
            .build()
        val item = MediaItemData.Builder(MEDIA_ID)
            .setMediaItem(MediaItem.Builder().setMediaId(MEDIA_ID).setMediaMetadata(metadata).build())
            .setMediaMetadata(metadata)
            .setLiveConfiguration(MediaItem.LiveConfiguration.UNSET)
            .setIsDynamic(true)
            .setIsSeekable(true)
            .build()
        return builder.setPlaylist(listOf(item)).setCurrentMediaItemIndex(0).build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> =
        perform(if (playWhenReady) NarrationTransport.Action.RESUME else NarrationTransport.Action.PAUSE)

    override fun handleStop(): ListenableFuture<*> = perform(NarrationTransport.Action.STOP)

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> =
        NarrationTransport.forSeek(seekCommand)?.let(::perform) ?: Futures.immediateVoidFuture()

    /** Android Auto's setMediaItems + prepare: the library session already started the narration. */
    override fun handleSetMediaItems(mediaItems: MutableList<MediaItem>, startIndex: Int, startPositionMs: Long): ListenableFuture<*> =
        Futures.immediateVoidFuture()

    override fun handlePrepare(): ListenableFuture<*> = Futures.immediateVoidFuture()

    private fun perform(action: NarrationTransport.Action): ListenableFuture<*> {
        commands.perform(action)
        // The narration reports its new state through the service, which calls update()
        return Futures.immediateVoidFuture()
    }

    private companion object {
        const val MEDIA_ID = "narration"
        val AVAILABLE_COMMANDS: Player.Commands = Player.Commands.Builder()
            .addAll(
                Player.COMMAND_PLAY_PAUSE,
                Player.COMMAND_STOP,
                Player.COMMAND_PREPARE,
                Player.COMMAND_SET_MEDIA_ITEM,
                Player.COMMAND_CHANGE_MEDIA_ITEMS,
                Player.COMMAND_SEEK_TO_NEXT,
                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                Player.COMMAND_GET_TIMELINE,
                Player.COMMAND_GET_METADATA
            )
            .build()
    }
}
