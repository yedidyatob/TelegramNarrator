package com.example.telegramnarrator.domain.model

data class Message(
    val id: Long,
    val chatId: Long,
    val senderName: String?,
    val text: String,
    val timestamp: Long,
    val isOutgoing: Boolean,
    val voiceNoteFileId: Int? = null
)
