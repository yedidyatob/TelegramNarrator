package io.github.yedidyatob.telegramnarrator.data.repository

import io.github.yedidyatob.telegramnarrator.core.diagnostics.AutoLog
import io.github.yedidyatob.telegramnarrator.domain.home.UnreadChatFilter
import org.drinkless.tdlib.TdApi
import java.util.concurrent.atomic.AtomicInteger

/**
 * #131 diagnostics, to be removed after: why chats whose unread messages predate the login are missing from
 * Home. Writes to the shareable debug log (About → Share debug log). Numeric chat ids, counts and flags only,
 * never titles, message text or phone numbers. At most [BUDGET] lines per process, so the first minutes after
 * a login (the interesting part) are not rolled out of the capped log file.
 */
class ChatListDiagnostics(
    private val sink: (String) -> Unit = AutoLog::event,
) {
    private val left = AtomicInteger(BUDGET)

    fun log(message: String) {
        val n = left.decrementAndGet()
        if (n >= 0) sink("#131 $message")
        if (n == 0) sink("#131 diagnostics budget used up")
    }

    /**
     * Before [update] is applied; [cached] looks up the chat as cached so far. Read state, notification settings,
     * positions and last message are logged only for chats with unread messages, or not cached yet.
     */
    fun update(
        update: TdApi.Object,
        cached: (Long) -> ChatSnapshot?,
    ) {
        fun interesting(chatId: Long): String? {
            val chat = cached(chatId) ?: return " uncached"
            return if (chat.unreadCount > 0) "" else null
        }
        val line =
            when (update) {
                is TdApi.UpdateNewChat -> {
                    update.chat.run {
                        "new $id unread=$unreadCount lastRead=$lastReadInboxMessageId ${mute(notificationSettings)} " +
                            "marked=$isMarkedAsUnread pos=${positions(positions)} lastMsg=${lastMessage != null}"
                    }
                }

                is TdApi.UpdateChatReadInbox -> {
                    (if (update.unreadCount > 0) "" else interesting(update.chatId))
                        ?.let { "read ${update.chatId} unread=${update.unreadCount} lastRead=${update.lastReadInboxMessageId}$it" }
                }

                is TdApi.UpdateChatNotificationSettings -> {
                    interesting(update.chatId)?.let { "notif ${update.chatId} ${mute(update.notificationSettings)}$it" }
                }

                is TdApi.UpdateChatPosition -> {
                    interesting(update.chatId)?.let { "pos ${update.chatId} ${positions(arrayOf(update.position))}$it" }
                }

                is TdApi.UpdateChatLastMessage -> {
                    interesting(update.chatId)?.let {
                        "last ${update.chatId} msg=${update.lastMessage != null} pos=${positions(update.positions)}$it"
                    }
                }

                is TdApi.UpdateChatIsMarkedAsUnread -> {
                    "marked ${update.chatId} ${update.isMarkedAsUnread}"
                }

                else -> {
                    null
                }
            }
        line?.let(::log)
    }

    /** After a LoadChats page ([step] = "page=N") or the final 404 ([step] = "done"). */
    fun loaded(
        step: String,
        chats: Collection<ChatSnapshot>,
    ) = log("LoadChats $step cached=${chats.size} unread=${chats.count { it.unreadCount > 0 }} listed=${chats.count(::listed)}")

    fun fetched(chat: ChatSnapshot) = log("fetched ${chat.id} unread=${chat.unreadCount} mute=${chat.muteFor} listed=${listed(chat)}")

    companion object {
        const val BUDGET = 1500

        private fun listed(chat: ChatSnapshot) = UnreadChatFilter.shouldIncludeInUnreadList(chat.unreadCount, chat.muteFor)

        private fun mute(settings: TdApi.ChatNotificationSettings?) = "mute=${settings?.muteFor} defMute=${settings?.useDefaultMuteFor}"

        private fun positions(positions: Array<TdApi.ChatPosition>?) =
            positions.orEmpty().joinToString(",", "[", "]") { "${ChatSnapshot.listKey(it.list)}:${it.order}" }
    }
}
