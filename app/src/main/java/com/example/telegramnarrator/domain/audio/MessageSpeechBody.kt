package com.example.telegramnarrator.domain.audio

import com.example.telegramnarrator.domain.model.MessageContentType

/**
 * What the player should say for a message body (not including "Message from ..." / voice-note intros).
 *
 * Media-only messages (photo / video / sticker / ... with no caption) are **not** announced as
 * "Photo" / "Video" placeholders — they are skipped silently. Callers still mark those message ids
 * as read when playback passes them (or batched with the next spoken text in the same chat).
 *
 * Voice notes are handled separately via [PlaybackItem.MessageItem.voiceNoteFileId]; this helper
 * only decides the text/caption path.
 */
object MessageSpeechBody {
    /**
     * Body text to speak, or null when the message should be skipped without speaking.
     * A non-blank [cleanedText] (caption / text) is always spoken regardless of [contentType].
     */
    @Suppress("UNUSED_PARAMETER")
    fun resolve(cleanedText: String, contentType: MessageContentType): String? {
        // contentType is intentional in the API: callers pass PHOTO/VIDEO/etc., but we never
        // invent spoken type labels — only a real caption/text is spoken.
        if (cleanedText.isNotBlank()) return cleanedText
        return null
    }

    /** True when this item has no speakable body and is not a downloadable voice note. */
    fun shouldSkipSilently(
        cleanedText: String,
        contentType: MessageContentType,
        voiceNoteFileId: Int?
    ): Boolean {
        if (voiceNoteFileId != null) return false
        return resolve(cleanedText, contentType) == null
    }
}
