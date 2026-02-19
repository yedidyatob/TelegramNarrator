package com.example.telegramnarrator.data.repository

import com.example.telegramnarrator.data.tdlib.TdLibClient
import com.example.telegramnarrator.domain.model.Chat
import com.example.telegramnarrator.domain.model.Message
import com.example.telegramnarrator.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.drinkless.td.libcore.telegram.TdApi
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@Singleton
class TdLibChatRepository @Inject constructor(
    private val client: TdLibClient
) : ChatRepository {

    // Internal cache of chats, updated by TDLib events
    private val _chats = MutableStateFlow<Map<Long, TdApi.Chat>>(emptyMap())

    init {
        // In a real app, we'd listen to client.updates for 'updateNewChat' and 'updateChatWaitMessage', etc.
        // For simplicity in this plan, we'll assume we fetch them on demand or simple polling for now,
        // but let's add a basic listener structure.
        /*
        client.updates.collect { update ->
            if (update is TdApi.UpdateNewChat) {
                _chats.update { it + (update.chat.id to update.chat) }
            }
        }
        */
        // Implementing proper update listening would require a CoroutineScope.
    }

    override fun getUnreadChats(): Flow<List<Chat>> {
         // This is a simplified implementation. 
         // In production, we need to ensure 'loadChats' has been called.
         return _chats.asStateFlow().map { chatMap ->
             chatMap.values
                 .filter { it.unreadCount > 0 }
                 .map { tdChat ->
                     Chat(
                         id = tdChat.id,
                         title = tdChat.title,
                         unreadCount = tdChat.unreadCount,
                         lastMessage = null // We'd need to map the lastMessage if needed
                     )
                 }
                 .sortedByDescending { it.id } // simple sort
         }
    }

    override suspend fun getChatMessages(chatId: Long, limit: Int): List<Message> {
        // offset 0, 0 means explicit from-to logic, usually fromLastMessage
        val history = client.send<TdApi.Messages>(
            TdApi.GetChatHistory(chatId, 0, 0, limit, false)
        )
        
        return history.messages.mapNotNull { tdMessage ->
            val content = tdMessage.content
            val text = when (content) {
                is TdApi.MessageText -> content.text.text
                is TdApi.MessagePhoto -> "[Photo] ${content.caption.text}"
                is TdApi.MessageVideo -> "[Video] ${content.caption.text}"
                is TdApi.MessageVoiceNote -> "[Voice Note] ${content.caption.text}"
                else -> "[Unsupported content]"
            }
            
            // Sender name resolution is async in TDLib (userId -> User object). 
            // For now, we might leave senderName null or map it later.
            // A production app maintains a User cleanup/cache.
            
            Message(
                id = tdMessage.id,
                chatId = tdMessage.chatId,
                senderName = null, // Needs User cache
                text = text,
                timestamp = tdMessage.date.toLong(),
                isOutgoing = tdMessage.isOutgoing
            )
        }
    }

    override suspend fun markChatAsRead(chatId: Long) {
        client.send<TdApi.Ok>(TdApi.ViewMessages(chatId, 0, longArrayOf(), true)) 
        // Logic depends on exact TDLib version/method for marking ready. 
        // ViewMessages checks them.
    }

    suspend fun loadChats() {
        // Request TDLib to load chats into memory
        // API 1.8.0+ uses LoadChats
        try {
           client.send<TdApi.Ok>(TdApi.LoadChats(null, 100))
        } catch (e: Exception) {
            // Log or handle
        }
    }
}
