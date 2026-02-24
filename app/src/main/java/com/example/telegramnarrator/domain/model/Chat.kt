package com.example.telegramnarrator.domain.model

data class Chat(
    val id: Long,
    val title: String,
    val unreadCount: Int,
    val lastMessage: Message? = null,
    val order: Long = 0L
)
