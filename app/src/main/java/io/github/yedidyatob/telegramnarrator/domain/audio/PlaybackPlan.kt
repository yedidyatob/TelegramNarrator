package io.github.yedidyatob.telegramnarrator.domain.audio

/**
 * Positions of the chats and messages of one run ("message 3 of 12", "chat 2 of 5"), computed once from the
 * queue PlaybackService builds. Only what is actually heard counts: chats that are cued (not silent) and
 * messages that are spoken or played as voice notes. Messages dropped by the channel rules and media-only /
 * symbol-only messages are passed silently, so they don't count.
 */
class PlaybackPlan private constructor(
    private val chats: Map<Long, PlaybackPosition>,
    private val messages: Map<Pair<Long, Long>, PlaybackPosition>,
    private val messageCounts: Map<Long, Int>
) {
    fun chatPosition(chatId: Long): PlaybackPosition? = chats[chatId]

    fun messagePosition(chatId: Long, messageId: Long): PlaybackPosition? = messages[chatId to messageId]

    /** Messages of [chatId] that are read aloud. */
    fun messageCount(chatId: Long): Int = messageCounts[chatId] ?: 0

    companion object {
        val EMPTY = PlaybackPlan(emptyMap(), emptyMap(), emptyMap())

        /** True when [item] is heard (spoken or played), as opposed to passed silently. */
        fun isHeard(item: PlaybackItem.MessageItem): Boolean {
            if (item.dropped) return false
            if (item.voiceNoteFileId != null) return true
            return !MessageSpeechBody.shouldSkipSilently(MessageCleaner.clean(item.text), item.contentType, null)
        }

        fun from(items: List<PlaybackItem>): PlaybackPlan {
            val chatOrder = items.filterIsInstance<PlaybackItem.Intro>().filter { !it.silent }.map { it.chatId }.distinct()
            val chats = chatOrder.mapIndexed { i, id -> id to PlaybackPosition(i + 1, chatOrder.size) }.toMap()
            val heard = items.filterIsInstance<PlaybackItem.MessageItem>().filter(::isHeard).groupBy { it.chatId }
            val messages = HashMap<Pair<Long, Long>, PlaybackPosition>()
            heard.forEach { (chatId, list) ->
                list.forEachIndexed { i, m -> messages[chatId to m.messageId] = PlaybackPosition(i + 1, list.size) }
            }
            return PlaybackPlan(chats, messages, heard.mapValues { it.value.size })
        }
    }
}
