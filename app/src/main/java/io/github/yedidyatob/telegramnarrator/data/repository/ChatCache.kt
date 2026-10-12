package io.github.yedidyatob.telegramnarrator.data.repository

import io.github.yedidyatob.telegramnarrator.domain.home.UnreadChatFilter
import io.github.yedidyatob.telegramnarrator.domain.model.Chat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.drinkless.tdlib.TdApi

/**
 * Immutable copy of the fields of a `TdApi.Chat` the app uses (#13). TDLib objects are mutable and handed to
 * whoever asked for them; the cache copies them on arrival and never keeps or mutates them.
 */
data class ChatSnapshot(
    val id: Long,
    val title: String?,
    val unreadCount: Int,
    val lastReadInboxMessageId: Long,
    val unreadMentionCount: Int,
    val photoFileId: Int?,
    val muteFor: Int?,
    /** Order in each chat list it is in, by list (see [listKey]); a list with order 0 is left out. */
    val orders: Map<String, Long>
) {
    /** Position in the main list (0 = not in it, e.g. archived or left). */
    val mainOrder: Long get() = orders[MAIN] ?: 0L
    val isArchived: Boolean get() = orders.containsKey(ARCHIVE)

    /** Applies changed positions; order 0 = the chat left that list (archived, left, deleted). */
    fun withPositions(positions: Array<TdApi.ChatPosition>?): ChatSnapshot {
        var result = orders
        positions.orEmpty().forEach { position ->
            val key = listKey(position.list)
            result = if (position.order == 0L) result - key else result + (key to position.order)
        }
        return copy(orders = result)
    }

    fun toDomain(): Chat = Chat(
        id = id,
        title = title ?: "Unknown",
        unreadCount = unreadCount,
        lastMessage = null,
        order = mainOrder,
        photoFileId = photoFileId
    )

    companion object {
        const val MAIN = "main"
        const val ARCHIVE = "archive"

        fun listKey(list: TdApi.ChatList?): String = when (list) {
            is TdApi.ChatListMain -> MAIN
            is TdApi.ChatListArchive -> ARCHIVE
            is TdApi.ChatListFolder -> "folder:${list.chatFolderId}"
            else -> "other"
        }

        fun ordersOf(positions: Array<TdApi.ChatPosition>?): Map<String, Long> =
            positions.orEmpty().filter { it.order != 0L }.associate { listKey(it.list) to it.order }

        fun of(chat: TdApi.Chat): ChatSnapshot = ChatSnapshot(
            id = chat.id,
            title = chat.title,
            unreadCount = chat.unreadCount,
            lastReadInboxMessageId = chat.lastReadInboxMessageId,
            unreadMentionCount = chat.unreadMentionCount,
            photoFileId = chat.photo?.small?.id,
            muteFor = chat.notificationSettings?.muteFor,
            orders = ordersOf(chat.positions)
        )
    }
}

/**
 * The chat cache of [TdLibChatRepository] (#13): an immutable map replaced atomically, so updates from TDLib's
 * stream and replies of GetChat on other threads never race on a shared object, and readers always see a
 * consistent snapshot.
 */
class ChatCache {
    sealed interface Result {
        /** The cache changed. */
        object Changed : Result
        /** The update is about a chat that is not cached yet: fetch it with GetChat. */
        data class Unknown(val chatId: Long) : Result
        /** Not a chat update. */
        object Ignored : Result
    }

    private val chats = MutableStateFlow<Map<Long, ChatSnapshot>>(emptyMap())
    val snapshot: StateFlow<Map<Long, ChatSnapshot>> = chats.asStateFlow()

    operator fun get(chatId: Long): ChatSnapshot? = chats.value[chatId]

    fun put(chat: TdApi.Chat): ChatSnapshot = ChatSnapshot.of(chat).also { snap -> chats.update { it + (snap.id to snap) } }

    /** The client was closed (logout / recreate): the next session starts empty. */
    fun clear() {
        chats.value = emptyMap()
    }

    /** Unread, not muted chats, main-list order first (same rule as before #13). */
    fun unreadChats(): List<Chat> = chats.value.values
        .filter { UnreadChatFilter.shouldIncludeInUnreadList(unreadCount = it.unreadCount, muteFor = it.muteFor) }
        .map { it.toDomain() }
        .sortedByDescending { it.order }

    fun apply(update: TdApi.Object): Result = when (update) {
        is TdApi.UpdateNewChat -> {
            put(update.chat)
            Result.Changed
        }
        is TdApi.UpdateChatTitle -> modify(update.chatId) { it.copy(title = update.title) }
        is TdApi.UpdateChatPhoto -> modify(update.chatId) { it.copy(photoFileId = update.photo?.small?.id) }
        is TdApi.UpdateChatReadInbox -> modify(update.chatId) {
            it.copy(unreadCount = update.unreadCount, lastReadInboxMessageId = update.lastReadInboxMessageId)
        }
        is TdApi.UpdateChatUnreadMentionCount -> modify(update.chatId) { it.copy(unreadMentionCount = update.unreadMentionCount) }
        is TdApi.UpdateChatNotificationSettings -> modify(update.chatId) { it.copy(muteFor = update.notificationSettings?.muteFor) }
        is TdApi.UpdateChatPosition -> modify(update.chatId) { it.withPositions(arrayOf(update.position)) }
        is TdApi.UpdateChatLastMessage -> modify(update.chatId) { it.withPositions(update.positions) }
        else -> Result.Ignored
    }

    private fun modify(chatId: Long, change: (ChatSnapshot) -> ChatSnapshot): Result {
        var found = false
        chats.update { map ->
            val chat = map[chatId]
            found = chat != null
            if (chat == null) map else map + (chatId to change(chat))
        }
        return if (found) Result.Changed else Result.Unknown(chatId)
    }
}
