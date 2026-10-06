package io.github.yedidyatob.telegramnarrator.domain.home

/**
 * Decides which chats appear in the unread list. Pure Kotlin (unit-tested).
 *
 * "Muted" means Telegram's notification mute: [TdApi.ChatNotificationSettings.muteFor] is non-zero
 * (seconds remaining, or a large number for "forever"). Muted chats are skipped so spam channels
 * the user silenced in Telegram are not narrated.
 */
object UnreadChatFilter {

    /** True when Telegram mute is active ([muteFor] != 0). Null settings are treated as unmuted. */
    fun isMuted(muteFor: Int?): Boolean = (muteFor ?: 0) != 0

    fun shouldIncludeInUnreadList(unreadCount: Int, muteFor: Int?): Boolean =
        unreadCount > 0 && !isMuted(muteFor)
}
