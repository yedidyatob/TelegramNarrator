package io.github.yedidyatob.telegramnarrator.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.yedidyatob.telegramnarrator.domain.audio.PlaybackManager
import io.github.yedidyatob.telegramnarrator.domain.model.Chat
import io.github.yedidyatob.telegramnarrator.domain.model.Message
import io.github.yedidyatob.telegramnarrator.domain.repository.AuthRepository
import io.github.yedidyatob.telegramnarrator.domain.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.yedidyatob.telegramnarrator.data.tdlib.TdLibException
import io.github.yedidyatob.telegramnarrator.domain.audio.NowPlaying
import io.github.yedidyatob.telegramnarrator.domain.connection.ConnectionMonitor
import io.github.yedidyatob.telegramnarrator.domain.connection.ConnectionStatus
import io.github.yedidyatob.telegramnarrator.domain.home.ChatListLoad
import io.github.yedidyatob.telegramnarrator.domain.home.ConnectionBanner
import io.github.yedidyatob.telegramnarrator.domain.home.HomeContent
import io.github.yedidyatob.telegramnarrator.domain.home.HomeStateMapper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Everything the Home body needs besides the chat interactions. */
data class HomeUiState(
    val content: HomeContent = HomeContent.Loading,
    val banner: ConnectionBanner? = null,
    /** A load is running (pull-to-refresh indicator / top-bar spinner). */
    val isRefreshing: Boolean = false
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val authRepository: AuthRepository,
    private val playbackManager: PlaybackManager,
    connectionMonitor: ConnectionMonitor
) : ViewModel() {

    companion object {
        /** A LoadChats that hangs this long (e.g. TDLib can't reach Telegram) counts as failed. */
        const val LOAD_TIMEOUT_MS = 20_000L
        /** Connection problems shorter than this (startup, a quick network switch) show no banner. */
        const val BANNER_DELAY_MS = 1_500L
    }

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val load = MutableStateFlow<ChatListLoad>(ChatListLoad.Loading)

    /** One-shot message ids (snackbar) for refresh failures while chats are on screen. */
    private val _refreshErrors = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val refreshErrors: SharedFlow<Unit> = _refreshErrors.asSharedFlow()

    val unreadChats: StateFlow<List<Chat>> = chatRepository.getUnreadChats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val connection: StateFlow<ConnectionStatus> = connectionMonitor.status

    // Not-ready states only count once they last BANNER_DELAY_MS; READY applies at once
    @OptIn(ExperimentalCoroutinesApi::class)
    private val settledConnection: Flow<ConnectionStatus> = connection.transformLatest { status ->
        if (status != ConnectionStatus.READY) delay(BANNER_DELAY_MS)
        emit(status)
    }

    val uiState: StateFlow<HomeUiState> = combine(
        unreadChats, load, connection, settledConnection, _isLoading
    ) { chats, load, connection, settled, loading ->
        // Offline / connecting turn the body into a loading or offline state right away (an empty list is not
        // trusted then); only the banner waits until the problem has lasted a moment
        val content = HomeStateMapper.content(chats, load, connection)
        HomeUiState(
            content = content,
            banner = HomeStateMapper.banner(settled, content),
            isRefreshing = loading
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    val isPlaying: StateFlow<Boolean> = playbackManager.isPlaying
    val isPaused: StateFlow<Boolean> = playbackManager.isPaused
    val playStatus: StateFlow<String?> = playbackManager.currentStatus
    val nowPlaying: StateFlow<NowPlaying?> = playbackManager.nowPlaying
    /** The current message's audio is still being fetched / synthesized (shown after a short delay). */
    val isPreparingAudio: StateFlow<Boolean> = playbackManager.isPreparingAudio
    val selectedChatIds: StateFlow<Set<Long>> = playbackManager.selectedChatIds
    val currentPlayingChatId: StateFlow<Long?> = playbackManager.currentPlayingChatId

    private val _selectedChat = MutableStateFlow<Chat?>(null)
    val selectedChat: StateFlow<Chat?> = _selectedChat.asStateFlow()

    private val _selectedChatMessages = MutableStateFlow<List<Message>?>(null)
    val selectedChatMessages: StateFlow<List<Message>?> = _selectedChatMessages.asStateFlow()

    private var refreshJob: Job? = null

    init {
        refresh()
        viewModelScope.launch {
            unreadChats.collect { chats ->
                playbackManager.syncSelectionWithUnreadChats(chats.map { it.id })
            }
        }
        // Back online (or connected after connecting): load again, so the error / offline state recovers by itself
        viewModelScope.launch {
            var previous = connection.value
            connection.collect { status ->
                if (status == ConnectionStatus.READY && previous != ConnectionStatus.READY) refresh()
                previous = status
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                authRepository.logOut()
            } catch (_: Exception) {
                // Typical when the client is already closing
            } finally {
                _isLoading.value = false
            }
        }
    }

    /** Loads the chat list again (pull to refresh, Try again, reconnect). Runs one load at a time. */
    fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            _isLoading.value = true
            if (load.value is ChatListLoad.Failed) load.value = ChatListLoad.Loading
            try {
                withTimeout(LOAD_TIMEOUT_MS) { chatRepository.loadChats() }
                load.value = ChatListLoad.Loaded
            } catch (e: CancellationException) {
                if (e is TimeoutCancellationException) onLoadFailed(null) else throw e
            } catch (e: Exception) {
                onLoadFailed((e as? TdLibException)?.tdMessage ?: e.message)
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun onLoadFailed(detail: String?) {
        // With chats on screen a failed refresh is only a snackbar; the list stays
        if (unreadChats.value.isNotEmpty()) {
            if (load.value !is ChatListLoad.Failed) load.value = ChatListLoad.Loaded
            _refreshErrors.tryEmit(Unit)
        } else {
            load.value = ChatListLoad.Failed(detail)
        }
    }

    /** Downloads a chat photo for the avatars. */
    suspend fun chatPhotoPath(fileId: Int): String? = chatRepository.getFilePath(fileId)

    fun selectChat(chat: Chat?) {
        _selectedChat.value = chat
        // null = loading (the sheet shows a spinner until the messages arrive)
        _selectedChatMessages.value = null
        if (chat == null) return
        viewModelScope.launch {
            val messages = try {
                // Oldest-first batch from the repository; reverse only for reading convenience in the sheet
                chatRepository.getChatMessages(chat.id).asReversed()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                emptyList()
            }
            // Another chat may have been opened meanwhile
            if (_selectedChat.value?.id == chat.id) _selectedChatMessages.value = messages
        }
    }

    fun toggleChatSelection(chatId: Long) {
        playbackManager.toggleSelection(chatId)
    }

    fun selectAll() {
        playbackManager.selectAll(unreadChats.value.map { it.id })
    }

    fun deselectAll() {
        playbackManager.deselectAll()
    }

    /**
     * Marks the unread messages currently shown in the preview sheet as read in Telegram.
     * Respects [ChatRepository.markAsReadEnabled] (always on in release; debug builds have a switch).
     */
    fun markSelectedChatAsRead() {
        val chat = _selectedChat.value ?: return
        val messages = _selectedChatMessages.value.orEmpty()
        if (messages.isEmpty()) return
        viewModelScope.launch {
            try {
                chatRepository.markChatAsRead(chat.id, messages.map { it.id })
                selectChat(null)
                refresh()
            } catch (_: Exception) {
                // Leave the sheet open; the user can retry
            }
        }
    }

    /** Chats that Play All / the FAB should narrate (intersection of unread × selected). */
    fun selectedChatsForPlayback(): List<Chat> {
        val selected = selectedChatIds.value
        return unreadChats.value.filter { it.id in selected }
    }
}
