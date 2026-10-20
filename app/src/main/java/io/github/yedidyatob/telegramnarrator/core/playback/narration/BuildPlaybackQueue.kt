package io.github.yedidyatob.telegramnarrator.core.playback.narration

import io.github.yedidyatob.telegramnarrator.domain.audio.ChatTitleSpeech
import io.github.yedidyatob.telegramnarrator.domain.audio.PlaybackItem
import io.github.yedidyatob.telegramnarrator.domain.audio.SponsoredSlotPlacement
import io.github.yedidyatob.telegramnarrator.domain.cleaning.ChannelRulesEngine
import io.github.yedidyatob.telegramnarrator.domain.repository.ChatRepository

/** The items of one reading run and the titles / photos of its chats. */
data class PlaybackRun(val items: List<PlaybackItem>, val chats: Map<Long, RunChat>)

data class RunChat(val title: String, val photoFileId: Int?)

/**
 * Use case (#12): builds the reading queue for [invoke]'s chats, in order. Per chat: its unread incoming
 * messages (oldest first) through the per-channel cleaning rules, a boundary ding and the spoken title, the
 * messages, the official sponsored slot, a pause; an end cue after the last chat.
 */
class BuildPlaybackQueue(
    private val chatRepository: ChatRepository,
    private val rules: () -> ChannelRulesEngine,
    private val unknownChatTitle: () -> String
) {
    suspend operator fun invoke(chatIds: LongArray): PlaybackRun {
        val runItems = mutableListOf<PlaybackItem>()
        val chats = LinkedHashMap<Long, RunChat>()
        chatIds.forEach { chatId ->
            val chat = chatRepository.getChat(chatId)
            val title = chat?.title ?: unknownChatTitle()
            chats[chatId] = RunChat(title, chat?.photoFileId)
            val messages = chatRepository.getChatMessages(chatId)
            val decisions = rules().evaluate(chatId, chat?.title, messages)
            if (decisions.isNotEmpty()) {
                val silent = decisions.all { it.dropped }
                // Boundary ding, then the chat title spoken on its own (no "New chat" words)
                val chatItems = ChatTitleSpeech.chatOpening(
                    chatId,
                    rawTitle = chat?.title,
                    displayTitle = title,
                    silent = silent
                ) + decisions.map { decision ->
                    val msg = decision.message
                    PlaybackItem.MessageItem(
                        msg.senderName, decision.text, msg.id, chatId, msg.voiceNoteFileId, msg.contentType,
                        dropped = decision.dropped
                    )
                }
                // Official sponsored message (channels / bot chats) after the last unread message, before the
                // next chat's ding. Channel cleaning rules never see it.
                runItems.addAll(SponsoredSlotPlacement.withSlot(chatItems, chatId, title, silent))
                runItems.add(PlaybackItem.Silence(1000))
            }
        }
        runItems.add(PlaybackItem.Outro)
        return PlaybackRun(runItems, chats)
    }
}
