package io.github.yedidyatob.telegramnarrator.data.repository

import android.util.Log
import io.github.yedidyatob.telegramnarrator.data.tdlib.TdGateway
import io.github.yedidyatob.telegramnarrator.data.tdlib.TdLibException
import io.github.yedidyatob.telegramnarrator.domain.model.Chat
import io.github.yedidyatob.telegramnarrator.domain.model.Message
import io.github.yedidyatob.telegramnarrator.domain.model.MessageContentType
import io.github.yedidyatob.telegramnarrator.domain.repository.ChatRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.drinkless.tdlib.TdApi

/**
 * Chats and messages from TDLib (#13): the chat list is kept as immutable [ChatSnapshot]s in [ChatCache], fed by
 * the lossless update stream of [TdGateway]; the cache is emptied when the TDLib client is closed.
 */
class TdLibChatRepository(
    private val client: TdGateway,
    private val userCache: TdLibUserCache,
    // Always on in release builds; debug builds can turn it off in Voice settings (Telegram API ToS 1.4)
    private val markAsReadSetting: () -> Boolean,
    private val repositoryScope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) : ChatRepository {

    companion object {
        /** How long a voice note may take to download before it is skipped. */
        const val VOICE_DOWNLOAD_TIMEOUT_MS = 30_000L
    }

    private val cache = ChatCache()
    private val _unreadChats = MutableStateFlow<List<Chat>>(emptyList())

    override val markAsReadEnabled: Boolean get() = markAsReadSetting()

    init {
        repositoryScope.launch {
            client.updates.collect { update ->
                when (val result = cache.apply(update)) {
                    ChatCache.Result.Changed -> refreshUnreadList()
                    is ChatCache.Result.Unknown -> repositoryScope.launch { getOrFetchChat(result.chatId) }
                    ChatCache.Result.Ignored -> Unit
                }
            }
        }
        // A state flow, so the current state is seen even if it was reached before this repository existed
        repositoryScope.launch {
            client.authorizationState.filterNotNull().collect { state ->
                when (state) {
                    is TdApi.AuthorizationStateReady -> try {
                        loadChats()
                    } catch (e: Exception) {
                        // Home retries (and shows the error) on its own
                    }
                    is TdApi.AuthorizationStateClosed -> {
                        // A new client (new session / account) starts without the old chats
                        cache.clear()
                        refreshUnreadList()
                    }
                    else -> Unit
                }
            }
        }
    }

    private fun refreshUnreadList() {
        _unreadChats.value = cache.unreadChats()
    }

    override fun getUnreadChats(): Flow<List<Chat>> = _unreadChats.asStateFlow()

    override suspend fun getChat(chatId: Long): Chat? = getOrFetchChat(chatId)?.toDomain()

    private suspend fun getOrFetchChat(chatId: Long): ChatSnapshot? {
        cache[chatId]?.let { return it }
        return try {
            fetchChat(chatId)
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun fetchChat(chatId: Long): ChatSnapshot =
        cache.put(client.send(TdApi.GetChat(chatId))).also { refreshUnreadList() }

    override suspend fun getChatMessages(chatId: Long, limit: Int): List<Message> {
        val tdChat = getOrFetchChat(chatId) ?: return emptyList()

        // The oldest unread messages first: a chat with more unread messages than the cap is read
        // from its first unread message forward, the rest is left for the next run
        val unread = UnreadHistoryPager.collectOldestUnread(
            unreadCount = tdChat.unreadCount,
            lastReadId = tdChat.lastReadInboxMessageId,
            maxResults = minOf(limit, ChatRepository.MAX_UNREAD_MESSAGES),
            idOf = { it.id },
            isOutgoing = { it.isOutgoing }
        ) { fromMessageId, pageSize ->
            client.send<TdApi.Messages>(
                TdApi.GetChatHistory(chatId, fromMessageId, 0, pageSize, false)
            ).messages.toList()
        }

        return unread.map { toDomainMessage(it) }
    }

    private suspend fun toDomainMessage(tdMessage: TdApi.Message): Message {
        val content = tdMessage.content
        var voiceFileId: Int? = null
        // text is only the text / caption; the content type tells the player what to announce
        // for media without a caption (and unsupported content has no text at all)
        val (contentType, text) = when (content) {
            is TdApi.MessageText -> MessageContentType.TEXT to content.text.text
            is TdApi.MessagePhoto -> MessageContentType.PHOTO to content.caption.text
            is TdApi.MessageVideo -> MessageContentType.VIDEO to content.caption.text
            is TdApi.MessageVoiceNote -> {
                voiceFileId = content.voiceNote.voice.id
                MessageContentType.VOICE_NOTE to content.caption.text
            }
            is TdApi.MessageVideoNote -> MessageContentType.VIDEO_NOTE to ""
            is TdApi.MessageSticker -> MessageContentType.STICKER to ""
            is TdApi.MessageAnimation -> MessageContentType.ANIMATION to content.caption.text
            is TdApi.MessageAudio -> MessageContentType.AUDIO to content.caption.text
            is TdApi.MessageDocument -> MessageContentType.DOCUMENT to content.caption.text
            else -> MessageContentType.UNSUPPORTED to ""
        }
        
        val senderName = when (val s = tdMessage.senderId) {
            is TdApi.MessageSenderUser -> userCache.getUserName(s.userId)
            is TdApi.MessageSenderChat -> getChat(s.chatId)?.title
            else -> null
        }

        return Message(
            id = tdMessage.id,
            chatId = tdMessage.chatId,
            senderName = senderName,
            text = text,
            timestamp = tdMessage.date.toLong(),
            isOutgoing = tdMessage.isOutgoing,
            voiceNoteFileId = voiceFileId,
            contentType = contentType
        )
    }

    override suspend fun markChatAsRead(chatId: Long, messageIds: List<Long>) {
        if (!markAsReadEnabled) {
            Log.d("ChatRepository", "Mark as read skipped (turned off in debug settings): $chatId")
            return
        }
        if (messageIds.isEmpty()) {
            // ViewMessages with an empty id list does nothing
            Log.d("ChatRepository", "Mark as read skipped (no messages): $chatId")
            return
        }
        Log.d("ChatRepository", "Marking ${messageIds.size} messages as read in chat: $chatId ids=$messageIds")
        try {
            // TDLib quirk: OpenChat before ViewMessages so the chat is treated as "being viewed".
            // forceRead=true should mark without an open chat, but some channel/supergroup paths
            // only update lastReadInboxMessageId reliably after OpenChat. Best-effort; failure
            // must not block ViewMessages.
            try {
                client.send<TdApi.Ok>(TdApi.OpenChat(chatId))
            } catch (openError: Exception) {
                Log.w("ChatRepository", "OpenChat failed for $chatId (continuing with ViewMessages)", openError)
            }
            // forceRead=true: mark even if the user is not actively staring at the chat UI
            client.send<TdApi.Ok>(TdApi.ViewMessages(chatId, messageIds.toLongArray(), null, true))
            // Refresh local unread count immediately. UpdateChatReadInbox usually follows, but
            // after pause the UI should not wait on that race — otherwise the badge stays stale
            // and a new Play All reloads the same messages from lastReadInboxMessageId.
            try {
                val refreshed = fetchChat(chatId)
                Log.d(
                    "ChatRepository",
                    "After mark-read chat=$chatId unread=${refreshed.unreadCount} lastRead=${refreshed.lastReadInboxMessageId}"
                )
            } catch (refreshError: Exception) {
                Log.w("ChatRepository", "Marked read but could not refresh chat $chatId", refreshError)
            }
        } catch (e: Exception) {
            Log.e("ChatRepository", "Failed to mark chat as read: $chatId", e)
        }
    }

    /**
     * Downloads a voice note and returns its local path, or null if it cannot be downloaded in
     * [VOICE_DOWNLOAD_TIMEOUT_MS]. Waits for TDLib's UpdateFile instead of polling.
     */
    override suspend fun getVoiceFilePath(fileId: Int): String? = try {
        withTimeoutOrNull(VOICE_DOWNLOAD_TIMEOUT_MS) {
            coroutineScope {
                // "Not active" only means stopped once TDLib has accepted the download request
                val requested = java.util.concurrent.atomic.AtomicBoolean(false)
                // Subscribed before the download starts, so the completing UpdateFile cannot be missed
                val finished = async(start = CoroutineStart.UNDISPATCHED) {
                    client.updates.filterIsInstance<TdApi.UpdateFile>()
                        .first {
                            it.file.id == fileId &&
                                (it.file.local?.isDownloadingCompleted == true || (requested.get() && it.file.local.isFinished()))
                        }
                        .file
                }
                val started = client.send(TdApi.DownloadFile(fileId, 1, 0, 0, false))
                requested.set(true)
                val file = if (started.local.isFinished()) started.also { finished.cancel() } else finished.await()
                file.local?.takeIf { it.isDownloadingCompleted }?.path
            }
        }
    } catch (e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        Log.e("ChatRepository", "Failed to get voice file path", e)
        null
    }

    /** Downloaded, or stopped without being downloaded (failed / cancelled). */
    private fun TdApi.LocalFile?.isFinished(): Boolean =
        this != null && (isDownloadingCompleted || !isDownloadingActive)

    override suspend fun loadChats() {
        try {
            Log.d("ChatRepository", "Requesting LoadChats(Main, 100)...")
            client.send<TdApi.Ok>(TdApi.LoadChats(TdApi.ChatListMain(), 100))
            Log.d("ChatRepository", "LoadChats request sent successfully")
        } catch (e: TdLibException) {
            // 404: every chat of the list is already loaded - not an error
            if (e.code == 404) return
            Log.e("ChatRepository", "LoadChats failed", e)
            throw e
        }
    }
}
