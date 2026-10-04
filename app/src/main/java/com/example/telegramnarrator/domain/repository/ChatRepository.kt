package com.example.telegramnarrator.domain.repository

import com.example.telegramnarrator.domain.model.Chat
import com.example.telegramnarrator.domain.model.Message
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    companion object {
        /** Upper bound for the number of unread messages fetched for a single chat. */
        const val MAX_UNREAD_MESSAGES = 100
    }

    fun getUnreadChats(): Flow<List<Chat>>
    suspend fun getChat(chatId: Long): Chat?

    /**
     * The chat's unread incoming messages, **oldest first** (playback order).
     * Returns at most min([Chat.unreadCount], [limit], [MAX_UNREAD_MESSAGES]) messages. If the chat
     * has more unread messages than that, these are the **oldest** unread ones (starting right after
     * the last read message); the newer ones stay unread for the next run.
     */
    suspend fun getChatMessages(chatId: Long, limit: Int = MAX_UNREAD_MESSAGES): List<Message>
    suspend fun markChatAsRead(chatId: Long)
    suspend fun getVoiceFilePath(fileId: Int): String?
    suspend fun loadChats()
    
    var markAsReadEnabled: Boolean
}
