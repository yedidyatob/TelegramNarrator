package com.example.telegramnarrator.core

import androidx.annotation.StringRes
import com.example.telegramnarrator.R
import com.example.telegramnarrator.domain.model.MessageContentType

/**
 * Short localized label for a message that has no text of its own (used in the **UI** chat preview).
 * Playback does not speak these placeholders — see [com.example.telegramnarrator.domain.audio.MessageSpeechBody].
 * Returns null for plain text / unsupported content.
 */
@StringRes
fun MessageContentType.labelRes(): Int? = when (this) {
    MessageContentType.PHOTO -> R.string.playback_content_photo
    MessageContentType.VIDEO -> R.string.playback_content_video
    MessageContentType.VOICE_NOTE -> R.string.playback_voice_note
    MessageContentType.VIDEO_NOTE -> R.string.playback_content_video_note
    MessageContentType.STICKER -> R.string.playback_content_sticker
    MessageContentType.ANIMATION -> R.string.playback_content_animation
    MessageContentType.AUDIO -> R.string.playback_content_audio
    MessageContentType.DOCUMENT -> R.string.playback_content_document
    MessageContentType.TEXT, MessageContentType.UNSUPPORTED -> null
}
