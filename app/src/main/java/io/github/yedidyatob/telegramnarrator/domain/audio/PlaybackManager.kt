package io.github.yedidyatob.telegramnarrator.domain.audio

import io.github.yedidyatob.telegramnarrator.domain.home.ChatSelectionLogic
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-wide playback UI state: playing/paused, status text, the chat currently being narrated,
 * and the multi-select set used by Home / Play All.
 */
@Singleton
class PlaybackManager @Inject constructor() {
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    private val _currentStatus = MutableStateFlow<String?>(null)
    val currentStatus: StateFlow<String?> = _currentStatus.asStateFlow()

    private val _currentPlayingChatId = MutableStateFlow<Long?>(null)
    val currentPlayingChatId: StateFlow<Long?> = _currentPlayingChatId.asStateFlow()

    private val _selectedChatIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedChatIds: StateFlow<Set<Long>> = _selectedChatIds.asStateFlow()

    @Volatile
    private var selectionState = ChatSelectionLogic.State()

    fun setPlaying(playing: Boolean) {
        _isPlaying.value = playing
        if (!playing) {
            _currentStatus.value = null
            _isPaused.value = false
            _currentPlayingChatId.value = null
        }
    }

    fun setPaused(paused: Boolean) {
        _isPaused.value = paused
    }

    fun setStatus(status: String) {
        _currentStatus.value = status
    }

    fun setPlayingChatId(chatId: Long?) {
        _currentPlayingChatId.value = chatId
    }

    /** Keep the selection in sync with the live unread list (auto-select new chats). */
    @Synchronized
    fun syncSelectionWithUnreadChats(unreadIds: List<Long>) {
        selectionState = ChatSelectionLogic.sync(selectionState, unreadIds)
        _selectedChatIds.value = selectionState.selected
    }

    @Synchronized
    fun toggleSelection(chatId: Long) {
        val next = ChatSelectionLogic.toggle(_selectedChatIds.value, chatId)
        selectionState = selectionState.copy(selected = next)
        _selectedChatIds.value = next
    }

    @Synchronized
    fun selectAll(unreadIds: List<Long>) {
        val next = ChatSelectionLogic.selectAll(unreadIds)
        selectionState = selectionState.copy(selected = next)
        _selectedChatIds.value = next
    }

    @Synchronized
    fun deselectAll() {
        val next = ChatSelectionLogic.deselectAll()
        selectionState = selectionState.copy(selected = next)
        _selectedChatIds.value = next
    }
}
