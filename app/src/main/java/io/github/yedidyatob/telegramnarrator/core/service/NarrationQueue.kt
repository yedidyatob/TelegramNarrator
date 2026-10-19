package io.github.yedidyatob.telegramnarrator.core.service

/**
 * The run's chats as Media3's playlist (pure, unit-tested): one queue entry per chat, in reading order, so
 * Android Auto's queue ("Up next") lists every chat of a Play all. The narration still works per message;
 * next / previous stay "next / previous message" ([NarrationTransport]); picking a queue entry jumps to that
 * chat ([chatsFrom]).
 */
object NarrationQueue {
    data class Entry(val chatId: Long, val title: String) {
        val mediaId: String get() = AutoBrowseTree.chatId(chatId)
    }

    /** Entries for [chatIds]; titles from [titles] (unknown yet: [fallbackTitle]). */
    fun entries(chatIds: List<Long>, titles: Map<Long, String>, fallbackTitle: String): List<Entry> =
        chatIds.distinct().map { Entry(it, titles[it]?.takeIf(String::isNotBlank) ?: fallbackTitle) }

    /** Index of the chat being read; before the first chat starts (or for an unknown chat) the first entry. */
    fun currentIndex(entries: List<Entry>, currentChatId: Long?): Int =
        entries.indexOfFirst { it.chatId == currentChatId }.coerceAtLeast(0)

    /** Selecting entry [index] (Auto's queue): read that chat and the ones after it. Null when out of range. */
    fun chatsFrom(entries: List<Entry>, index: Int): List<Long>? =
        if (index in entries.indices) entries.drop(index).map { it.chatId } else null
}
