package io.github.yedidyatob.telegramnarrator.core.service

import io.github.yedidyatob.telegramnarrator.domain.model.Chat

/**
 * The phone Player's "Up next" (#20): the same queue as Android Auto's ([NarrationQueue]), from the chat being
 * read onward. Chat title, photo and unread count only; never message text (Telegram preview rule).
 */
object UpNext {
    data class Item(
        /** Index in the run's queue; tapping it jumps there, like Auto's seekToMediaItem. */
        val queueIndex: Int,
        val chatId: Long,
        val title: String,
        val photoFileId: Int?,
        val unreadCount: Int,
        val isCurrent: Boolean
    )

    /**
     * [run] is the run's chats in reading order, [currentChatId] the chat being read, [chats] the live unread
     * list. Finished chats (before the current one) are left out, and so are later chats that have nothing
     * unread anymore (read elsewhere); the current chat is always shown.
     */
    fun items(run: List<Long>, currentChatId: Long?, chats: List<Chat>, fallbackTitle: String): List<Item> {
        if (run.isEmpty()) return emptyList()
        val byId = chats.associateBy { it.id }
        val queue = NarrationQueue.entries(run, byId.mapValues { it.value.title }, fallbackTitle)
        val current = NarrationQueue.currentIndex(queue, currentChatId)
        return queue.withIndex().drop(current).mapNotNull { (index, entry) ->
            val chat = byId[entry.chatId]
            val isCurrent = index == current
            if (chat == null && !isCurrent) return@mapNotNull null
            Item(index, entry.chatId, entry.title, chat?.photoFileId, chat?.unreadCount ?: 0, isCurrent)
        }
    }
}
