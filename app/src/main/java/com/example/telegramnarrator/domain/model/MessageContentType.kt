package com.example.telegramnarrator.domain.model

/**
 * Kind of content of a [Message]. [Message.text] only holds the text / caption.
 * Media without a caption is shown with a short label in the UI, but is **not** spoken as a
 * "Photo"/"Video" placeholder during playback (see [com.example.telegramnarrator.domain.audio.MessageSpeechBody]).
 */
enum class MessageContentType {
    TEXT,
    PHOTO,
    VIDEO,
    VOICE_NOTE,
    VIDEO_NOTE,
    STICKER,
    ANIMATION,
    AUDIO,
    DOCUMENT,
    // Anything the app can't handle: it is not read aloud
    UNSUPPORTED
}
