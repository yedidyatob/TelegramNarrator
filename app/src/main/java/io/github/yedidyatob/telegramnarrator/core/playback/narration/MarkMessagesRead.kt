package io.github.yedidyatob.telegramnarrator.core.playback.narration

import io.github.yedidyatob.telegramnarrator.domain.audio.ReadCheckpointer
import io.github.yedidyatob.telegramnarrator.domain.repository.ChatRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Use case (#12): sends a read checkpoint to Telegram. Runs in [scope], which outlives the playback (the final
 * flush when the service stops must still be sent).
 */
class MarkMessagesRead(private val chatRepository: ChatRepository, private val scope: CoroutineScope) : ReadMarkerPort {
    override fun markRead(batch: ReadCheckpointer.Batch) {
        scope.launch { chatRepository.markChatAsRead(batch.chatId, batch.messageIds) }
    }
}
