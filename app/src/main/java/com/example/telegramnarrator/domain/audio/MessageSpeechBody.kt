package com.example.telegramnarrator.domain.audio

import com.example.telegramnarrator.domain.model.MessageContentType

/**
 * What the player should say for a message body (no sender / "Voice note" labels — those are gone).
 *
 * Media-only messages (photo / video / sticker / ... with no caption) and **symbol-only** rows
 * (e.g. `####`, `****`, `———` — no letters or digits) are **not** spoken. Callers still mark those
 * message ids as read when playback passes them (or batched with the next spoken text in the same
 * chat via [ReadCheckpointer]).
 *
 * Voice notes are handled separately via [PlaybackItem.MessageItem.voiceNoteFileId] and
 * [VoiceNotePlayback] (play the file, or skip silently — never TTS-labelled).
 */
object MessageSpeechBody {
    /**
     * Body text to speak, or null when the message should be skipped without speaking.
     * A non-blank [cleanedText] with at least one letter or digit is spoken regardless of [contentType].
     */
    @Suppress("UNUSED_PARAMETER")
    fun resolve(cleanedText: String, contentType: MessageContentType): String? {
        // contentType is intentional in the API: callers pass PHOTO/VIDEO/etc., but we never
        // invent spoken type labels — only real speakable caption/text is spoken.
        if (cleanedText.isBlank()) return null
        if (isSymbolsOnly(cleanedText)) return null
        return cleanedText
    }

    /**
     * True when [text] has no letter and no digit (only punctuation, symbols, whitespace, etc.).
     * Used so decorative separators are not fed to TTS.
     */
    fun isSymbolsOnly(text: String): Boolean {
        if (text.isEmpty()) return true
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            i += Character.charCount(cp)
            if (Character.isLetter(cp) || Character.isDigit(cp)) return false
        }
        return true
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
