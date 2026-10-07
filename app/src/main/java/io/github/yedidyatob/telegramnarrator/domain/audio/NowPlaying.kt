package io.github.yedidyatob.telegramnarrator.domain.audio

import io.github.yedidyatob.telegramnarrator.domain.model.MessageContentType
import io.github.yedidyatob.telegramnarrator.domain.tts.SpeechProvider

/** 1-based position in a run, e.g. message 3 of 12 or chat 2 of 5. */
data class PlaybackPosition(val index: Int, val count: Int) {
    init {
        require(count > 0) { "count must be positive" }
        require(index in 1..count) { "index $index outside 1..$count" }
    }

    /** 0..1, for progress indicators. */
    val fraction: Float get() = index.toFloat() / count
}

/**
 * What the player is reading right now, for the Player screen and the mini player. Set by PlaybackService
 * as items start; null when nothing plays.
 *
 * @param chatPosition this chat among the chats of the run that are read aloud
 * @param messagePosition the current message among the chat's messages that are read aloud (media-only and
 *   rule-dropped messages are passed silently and not counted); null while the chat opens or an ad plays
 * @param messageCount how many messages of this chat are read aloud (shown while the chat opens)
 * @param engine the engine speaking the current item: the chosen one, or [SpeechProvider.SYSTEM] after a
 *   fallback; null for a voice note (its own audio)
 */
data class NowPlaying(
    val chatId: Long,
    val chatTitle: String,
    val chatPhotoFileId: Int? = null,
    val content: Content,
    val chatPosition: PlaybackPosition? = null,
    val messagePosition: PlaybackPosition? = null,
    val messageCount: Int = 0,
    val engine: SpeechProvider? = null
) {
    /**
     * The current message's sender when it adds something to the chat title: null in channels and private
     * chats (where the sender is the chat itself) and for anything that isn't a message.
     */
    val distinctSender: String?
        get() = (content as? Content.Message)?.sender?.takeIf { it.isNotBlank() && it.trim() != chatTitle.trim() }

    sealed interface Content {
        /** The chat-boundary ding and the spoken chat title. */
        data object ChatOpening : Content

        /** A message: its text as read (after the channel's cleaning rules), or a voice note. */
        data class Message(
            val messageId: Long,
            val sender: String?,
            val text: String,
            val contentType: MessageContentType,
            val isVoiceNote: Boolean
        ) : Content

        /** The chat's official sponsored message (the ad itself is in PlaybackManager.sponsoredAd). */
        data object Sponsored : Content
    }
}
