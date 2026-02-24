package com.example.telegramnarrator.data.repository

import com.example.telegramnarrator.data.tdlib.TdLibClient
import com.example.telegramnarrator.domain.model.Chat
import com.example.telegramnarrator.domain.model.Message
import com.example.telegramnarrator.domain.repository.ChatRepository
import com.example.telegramnarrator.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.drinkless.tdlib.TdApi
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import android.util.Log
import kotlinx.coroutines.flow.collectLatest

@Singleton
class TdLibChatRepository @Inject constructor(
    private val client: TdLibClient,
    private val userCache: TdLibUserCache
) : ChatRepository {

    // Internal cache of raw TdApi objects
    private val chatCache = java.util.concurrent.ConcurrentHashMap<Long, TdApi.Chat>()
    
    // Public state for UI
    private val _unreadChats = MutableStateFlow<List<Chat>>(emptyList())
    
    override var markAsReadEnabled: Boolean = !BuildConfig.DEBUG

    private val repositoryScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob())

    init {
        repositoryScope.launch {
            client.updates.collect { update ->
                when (val u = update) {
                    is TdApi.UpdateAuthorizationState -> {
                        if (u.authorizationState is TdApi.AuthorizationStateReady) {
                            loadChats()
                        }
                    }
                    is TdApi.UpdateNewChat -> {
                        chatCache[u.chat.id] = u.chat
                        refreshUnreadList()
                    }
                    is TdApi.UpdateChatTitle -> {
                        chatCache[u.chatId]?.let {
                            it.title = u.title
                            refreshUnreadList()
                        } ?: repositoryScope.launch { getOrFetchChat(u.chatId) }
                    }
                    is TdApi.UpdateChatReadInbox -> {
                        chatCache[u.chatId]?.let {
                            it.unreadCount = u.unreadCount
                            refreshUnreadList()
                        } ?: repositoryScope.launch { getOrFetchChat(u.chatId) }
                    }
                    is TdApi.UpdateChatUnreadMentionCount -> {
                        chatCache[u.chatId]?.let {
                            it.unreadMentionCount = u.unreadMentionCount
                            refreshUnreadList()
                        } ?: repositoryScope.launch { getOrFetchChat(u.chatId) }
                    }
                    is TdApi.UpdateChatPosition -> {
                        chatCache[u.chatId]?.let { chat ->
                            val newPositions = chat.positions.toMutableList()
                            val index = newPositions.indexOfFirst { it.list.getConstructor() == u.position.list.getConstructor() }
                            if (index != -1) newPositions[index] = u.position else newPositions.add(u.position)
                            chat.positions = newPositions.toTypedArray()
                            refreshUnreadList()
                        } ?: repositoryScope.launch { getOrFetchChat(u.chatId) }
                    }
                    is TdApi.UpdateChatLastMessage -> {
                        chatCache[u.chatId]?.let { chat ->
                            chat.lastMessage = u.lastMessage
                            chat.positions = u.positions
                            refreshUnreadList()
                        } ?: repositoryScope.launch { getOrFetchChat(u.chatId) }
                    }
                }
            }
        }
    }

    private fun refreshUnreadList() {
        val newList = chatCache.values
            .filter { it.unreadCount > 0 }
            .map { mapChat(it) }
            .sortedByDescending { it.order }
        _unreadChats.value = newList
    }

    override fun getUnreadChats(): Flow<List<Chat>> = _unreadChats.asStateFlow()

    override suspend fun getChat(chatId: Long): Chat? {
        chatCache[chatId]?.let { return mapChat(it) }
        return try {
            val tdChat = client.send<TdApi.Chat>(TdApi.GetChat(chatId))
            chatCache[chatId] = tdChat
            refreshUnreadList()
            mapChat(tdChat)
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun getOrFetchChat(chatId: Long): TdApi.Chat? {
        chatCache[chatId]?.let { return it }
        return try {
            val chat = client.send<TdApi.Chat>(TdApi.GetChat(chatId))
            chatCache[chatId] = chat
            refreshUnreadList()
            chat
        } catch (e: Exception) {
            null
        }
    }

    private fun mapChat(tdChat: TdApi.Chat): Chat {
        val position = tdChat.positions?.find { it.list is TdApi.ChatListMain }
        return Chat(
            id = tdChat.id,
            title = tdChat.title ?: "Unknown",
            unreadCount = tdChat.unreadCount,
            lastMessage = null,
            order = position?.order ?: 0L
        )
    }

    override suspend fun getChatMessages(chatId: Long, limit: Int): List<Message> {
        // offset 0, 0 means explicit from-to logic, usually fromLastMessage
        val history = client.send<TdApi.Messages>(
            TdApi.GetChatHistory(chatId, 0, 0, limit, false)
        )
        
        return history.messages.map { tdMessage ->
            val content = tdMessage.content
            var voiceFileId: Int? = null
            val text = when (content) {
                is TdApi.MessageText -> content.text.text
                is TdApi.MessagePhoto -> content.caption.text
                is TdApi.MessageVideo -> content.caption.text
                is TdApi.MessageVoiceNote -> {
                    voiceFileId = content.voiceNote.voice.id
                    content.caption.text
                }
                else -> "[Unsupported content]"
            }
            
            val senderName = when (val s = tdMessage.senderId) {
                is TdApi.MessageSenderUser -> userCache.getUserName(s.userId)
                is TdApi.MessageSenderChat -> getChat(s.chatId)?.title ?: "Channel"
                else -> "System"
            }
            
            Message(
                id = tdMessage.id,
                chatId = tdMessage.chatId,
                senderName = senderName,
                text = text,
                timestamp = tdMessage.date.toLong(),
                isOutgoing = tdMessage.isOutgoing,
                voiceNoteFileId = voiceFileId
            )
        }
    }

    override suspend fun markChatAsRead(chatId: Long) {
        if (!markAsReadEnabled) {
            Log.d("ChatRepository", "Mark as read skipped (Debug/Disabled): $chatId")
            return
        }
        Log.d("ChatRepository", "Marking chat as read: $chatId")
        try {
            client.send<TdApi.Ok>(TdApi.ViewMessages(chatId, longArrayOf(), null, true))
        } catch (e: Exception) {
            Log.e("ChatRepository", "Failed to mark chat as read: $chatId", e)
        }
    }

    override suspend fun getVoiceFilePath(fileId: Int): String? {
        return try {
            val file = client.send(TdApi.DownloadFile(fileId, 1, 0, 0, true))
            if (file.local?.isDownloadingCompleted == true) {
                file.local.path
            } else {
                // Wait for a reasonable amount of time to allow the file to download
                var downloadedFile = file
                for (i in 0..10) {
                    kotlinx.coroutines.delay(500)
                    downloadedFile = client.send(TdApi.GetFile(fileId))
                    if (downloadedFile.local?.isDownloadingCompleted == true) {
                        return downloadedFile.local.path
                    }
                }
                return null
            }
        } catch (e: Exception) {
            Log.e("ChatRepository", "Failed to get voice file path", e)
            null
        }
    }

    override suspend fun loadChats() {
        try {
            Log.d("ChatRepository", "Requesting LoadChats(Main, 100)...")
            client.send<TdApi.Ok>(TdApi.LoadChats(TdApi.ChatListMain(), 100))
            Log.d("ChatRepository", "LoadChats request sent successfully")
        } catch (e: Exception) {
            Log.e("ChatRepository", "LoadChats failed", e)
        }
    }
}
