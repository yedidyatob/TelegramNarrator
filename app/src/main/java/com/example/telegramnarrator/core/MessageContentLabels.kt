package com.example.telegramnarrator.core

import androidx.annotation.StringRes
import com.example.telegramnarrator.R
import com.example.telegramnarrator.domain.model.MessageContentType

/**
 * Short localized label announced for a message that has no text of its own,
 * or null when nothing should be said (plain text, unsupported content).
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
