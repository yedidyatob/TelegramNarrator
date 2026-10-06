package io.github.yedidyatob.telegramnarrator.domain.repository

import io.github.yedidyatob.telegramnarrator.domain.model.Chat
import io.github.yedidyatob.telegramnarrator.domain.model.Message
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

    /**
     * Marks the given (played) messages of the chat as read; messages newer than the newest of
     * [messageIds] stay unread. No-op if [markAsReadEnabled] is false (possible in debug builds only).
     */
    suspend fun markChatAsRead(chatId: Long, messageIds: List<Long>)
    suspend fun getVoiceFilePath(fileId: Int): String?

    /** Downloads a small TDLib file (e.g. a chat photo) and returns its local path, or null. */
    suspend fun getFilePath(fileId: Int): String? = getVoiceFilePath(fileId)

    /**
     * Asks TDLib to load the main chat list (the chats arrive through [getUnreadChats]). Returns normally when
     * the list is loaded (or was already complete); throws when TDLib reports an error.
     */
    suspend fun loadChats()
    
    /** Always true in release builds; debug builds can turn it off in Voice settings. */
    val markAsReadEnabled: Boolean
}
