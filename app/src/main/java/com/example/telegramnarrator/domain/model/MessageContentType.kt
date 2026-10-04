package com.example.telegramnarrator.domain.model

/**
 * Kind of content of a [Message]. [Message.text] only holds the text / caption, so for media
 * without a caption this tells the player what to announce ("Photo", "Sticker", ...).
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
