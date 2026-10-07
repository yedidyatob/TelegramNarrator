package io.github.yedidyatob.telegramnarrator.domain.home

import io.github.yedidyatob.telegramnarrator.domain.connection.ConnectionStatus
import io.github.yedidyatob.telegramnarrator.domain.model.Chat

/** Progress of loading the chat list from TDLib (LoadChats). */
sealed interface ChatListLoad {
    /** The first load is still running (or has not started). */
    data object Loading : ChatListLoad
    data object Loaded : ChatListLoad
    /** The load failed; [detail] is TDLib's message, if any. */
    data class Failed(val detail: String?) : ChatListLoad
}

/** What the Home screen body shows. */
sealed interface HomeContent {
    data object Loading : HomeContent
    /** No unread chats (muted chats are never listed). */
    data object Empty : HomeContent
    /** No network and nothing to show yet; loads by itself when the network is back. */
    data object Offline : HomeContent
    data class Error(val detail: String?) : HomeContent
    data class Chats(val chats: List<Chat>) : HomeContent
}

/** Thin banner above the chat list while TDLib is not connected. */
enum class ConnectionBanner { OFFLINE, CONNECTING }

object HomeStateMapper {
    /**
     * Chats always win (cached chats stay usable offline, with the banner). With no chats: offline beats
     * everything, then a load that is still running, then a failed load. While TDLib is still connecting /
     * fetching updates an empty list isn't trusted yet ("No unread chats" must not flash during startup).
     */
    fun content(chats: List<Chat>, load: ChatListLoad, connection: ConnectionStatus): HomeContent = when {
        chats.isNotEmpty() -> HomeContent.Chats(chats)
        connection.isOffline -> HomeContent.Offline
        load is ChatListLoad.Loading -> HomeContent.Loading
        load is ChatListLoad.Failed -> HomeContent.Error(load.detail)
        connection.isConnecting -> HomeContent.Loading
        else -> HomeContent.Empty
    }

    /**
     * The banner for [connection]. Offline with no chats has the full-screen offline state instead, so no
     * banner then.
     */
    fun banner(connection: ConnectionStatus, content: HomeContent): ConnectionBanner? = when {
        connection.isOffline -> if (content is HomeContent.Offline) null else ConnectionBanner.OFFLINE
        connection.isConnecting -> ConnectionBanner.CONNECTING
        else -> null
    }
}
