package io.github.yedidyatob.telegramnarrator.data.demo

import io.github.yedidyatob.telegramnarrator.domain.model.Chat
import io.github.yedidyatob.telegramnarrator.domain.model.Message
import io.github.yedidyatob.telegramnarrator.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory [ChatRepository] over [DemoData]; marking as read only removes messages from local state. */
class FakeChatRepository : ChatRepository {
    private val unread = MutableStateFlow(DemoData.messages())

    fun reset() {
        unread.value = DemoData.messages()
    }

    private fun chatOf(
        index: Int,
        messages: List<Message>,
    ): Chat {
        val id = DemoData.chatId(index)
        return Chat(
            id = id,
            title = DemoData.channels[index].first,
            unreadCount = messages.size,
            lastMessage = messages.lastOrNull(),
            order = (DemoData.channels.size - index).toLong(),
        )
    }

    private fun chatsOf(state: Map<Long, List<Message>>): List<Chat> =
        DemoData.channels.indices
            .map { chatOf(it, state[DemoData.chatId(it)].orEmpty()) }
            .filter { it.unreadCount > 0 }

    override fun getUnreadChats(): Flow<List<Chat>> = unread.map(::chatsOf)

    override suspend fun getChat(chatId: Long): Chat? {
        val index = DemoData.channels.indices.firstOrNull { DemoData.chatId(it) == chatId } ?: return null
        return chatOf(index, unread.value[chatId].orEmpty())
    }

    override suspend fun getChatMessages(
        chatId: Long,
        limit: Int,
    ): List<Message> = unread.value[chatId].orEmpty().take(minOf(limit, ChatRepository.MAX_UNREAD_MESSAGES))

    override suspend fun markChatAsRead(
        chatId: Long,
        messageIds: List<Long>,
    ) {
        val newest = messageIds.maxOrNull() ?: return
        unread.value = unread.value + (chatId to unread.value[chatId].orEmpty().filter { it.id > newest })
    }

    override suspend fun getVoiceFilePath(fileId: Int): String? = null

    override suspend fun loadChats() = Unit

    override val markAsReadEnabled: Boolean = true
}

/** Clearly invented channels and messages (English and Hebrew), the same style as the Play Store screenshots. */
object DemoData {
    val channels: List<Pair<String, List<String>>> =
        listOf(
            "Tech News Daily" to
                listOf(
                    "A new open-source speech engine was released today. It runs fully offline, supports more than " +
                        "thirty languages and gives each of them a natural-sounding voice.",
                    "Phone makers are expected to announce longer software support at this week's conference.",
                    "Tip of the day: you can listen to your channels with the screen off.",
                ),
            "חדשות הבוקר" to
                listOf(
                    "הבוקר נפתחה עונת הרחצה בחופי הארץ. מצילים יהיו בתפקיד משבע בבוקר ועד שבע בערב, " +
                        "ומומלץ לרחוץ רק בחופים מוכרזים ולשמור על הילדים.",
                    "תחזית מזג האוויר: נאה ברוב האזורים, עם עלייה קלה בטמפרטורות לקראת סוף השבוע.",
                ),
            "Recipes & Cooking" to
                listOf(
                    "Today's recipe: a simple tomato soup. Roast the tomatoes with garlic, then blend with stock.",
                    "Reader question: yes, you can freeze the soup for up to three months.",
                ),
            "מועדון הספר" to
                listOf(
                    "המפגש הבא של מועדון הספר יתקיים ביום שלישי בשמונה בערב.",
                ),
        )

    fun chatId(index: Int): Long = -1_000_000_000_001L - index

    /** Unread messages per chat id, oldest first. */
    fun messages(): Map<Long, List<Message>> {
        val start = 1_767_225_600L // 2026-01-01, a fixed sample date
        return channels.indices.associate { index ->
            val chatId = chatId(index)
            chatId to
                channels[index].second.mapIndexed { i, text ->
                    Message(
                        id = (i + 1).toLong(),
                        chatId = chatId,
                        senderName = null,
                        text = text,
                        timestamp = start + index * 3_600L + i * 60L,
                        isOutgoing = false,
                    )
                }
        }
    }
}
