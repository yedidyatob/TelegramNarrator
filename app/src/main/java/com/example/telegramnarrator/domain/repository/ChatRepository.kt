package com.example.telegramnarrator.domain.repository

import com.example.telegramnarrator.domain.model.Chat
import com.example.telegramnarrator.domain.model.Message
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    fun getUnreadChats(): Flow<List<Chat>>
    suspend fun getChatMessages(chatId: Long, limit: Int = 50): List<Message>
    suspend fun markChatAsRead(chatId: Long)
}
