package io.github.yedidyatob.telegramnarrator.core.service

import io.github.yedidyatob.telegramnarrator.domain.model.Chat

/**
 * The Android Auto browse tree (#41), as pure ids (unit-tested): root -> "Play all unread" (first row, with
 * an icon) + one playable node per unread chat, exactly the Home list (same filter, same order, no cap).
 * Auto's own Play control (playback resumption) and voice ("play Telegram Narrator") also play all. Playing a node reads the same narration as
 * the app (titles, dings, sponsored messages), so nothing here plays audio itself.
 */
object AutoBrowseTree {
    const val ROOT_ID = "root"
    const val PLAY_ALL_ID = "play_all"
    /** Single non-playable root child while logged out / Telegram not ready. */
    const val SIGN_IN_ID = "sign_in"
    private const val CHAT_PREFIX = "chat:"

    data class Node(
        val mediaId: String,
        val title: String,
        val unreadCount: Int,
        val playable: Boolean = true,
        /** The chat, for its artwork; null for the sign-in item. */
        val chatId: Long? = null
    )

    /** What the library could load: the unread chats, or nothing because Telegram isn't signed in / ready. */
    sealed interface Library {
        data class Ready(val unread: List<Chat>) : Library
        data object SignedOut : Library

        /** For the diagnostic log: the state and a count, never chat titles. */
        fun logState(): String = when (this) {
            SignedOut -> "SignedOut"
            is Ready -> "Ready(unread=${unread.size})"
        }
    }

    /** A media id for the diagnostic log: the fixed ids as is, a chat node as its numeric id only. */
    fun logId(mediaId: String): String = when {
        mediaId == ROOT_ID || mediaId == PLAY_ALL_ID || mediaId == SIGN_IN_ID -> mediaId
        mediaId.startsWith(CHAT_PREFIX) && mediaId.removePrefix(CHAT_PREFIX).toLongOrNull() != null -> mediaId
        mediaId.isEmpty() -> "<empty>"
        else -> "<other:${mediaId.length}>"
    }

    /** Indices of one page of [size] items; overflow-safe for Media3's unpaged request (pageSize = MAX_VALUE). */
    fun pageRange(size: Int, page: Int, pageSize: Int): IntRange {
        if (page < 0 || pageSize <= 0) return 0 until size
        val from = (page.toLong() * pageSize).coerceAtMost(size.toLong()).toInt()
        val to = (from.toLong() + pageSize).coerceAtMost(size.toLong()).toInt()
        return from until to
    }

    /** Root children for [library]: the sign-in message while signed out, else [rootChildren]. */
    fun rootChildren(library: Library, signInTitle: String, playAllTitle: String): List<Node> = when (library) {
        Library.SignedOut -> listOf(Node(SIGN_IN_ID, signInTitle, 0, playable = false))
        is Library.Ready -> rootChildren(library.unread, playAllTitle)
    }

    fun chatId(chat: Long) = "$CHAT_PREFIX$chat"

    /**
     * Children of the root: "Play all unread" first, then [unread], which is already Home's list (filtered and
     * ordered by the repository). Nothing unread: no rows.
     */
    fun rootChildren(unread: List<Chat>, playAllTitle: String): List<Node> {
        if (unread.isEmpty()) return emptyList()
        val playAll = Node(PLAY_ALL_ID, playAllTitle, unread.sumOf { it.unreadCount })
        return listOf(playAll) + unread.map { Node(chatId(it.id), it.title, it.unreadCount, chatId = it.id) }
    }

    /**
     * Chats to read for a played [mediaId]: all unread for "Play all", one chat for a chat node, null for an
     * unknown / malformed id (rejected).
     */
    fun chatsToPlay(mediaId: String, unread: List<Chat>): List<Long>? = when {
        mediaId == PLAY_ALL_ID -> unread.map { it.id }
        mediaId.startsWith(CHAT_PREFIX) -> mediaId.removePrefix(CHAT_PREFIX).toLongOrNull()?.let { listOf(it) }
        else -> null
    }

    /**
     * Voice search ("play News on Telegram Narrator"): the unread chat whose title best matches [query]
     * (exact, then prefix, then contains, case-insensitive); all unread for a blank query or no match.
     */
    fun chatsForSearch(query: String?, unread: List<Chat>): List<Long> {
        val chats = unread
        val q = query?.trim()?.lowercase().orEmpty()
        if (q.isEmpty()) return chats.map { it.id }
        val match = chats.firstOrNull { it.title.lowercase() == q }
            ?: chats.firstOrNull { it.title.lowercase().startsWith(q) }
            ?: chats.firstOrNull { it.title.lowercase().contains(q) }
        return match?.let { listOf(it.id) } ?: chats.map { it.id }
    }
}
