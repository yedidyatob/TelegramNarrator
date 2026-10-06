package io.github.yedidyatob.telegramnarrator.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.yedidyatob.telegramnarrator.domain.audio.PlaybackManager
import io.github.yedidyatob.telegramnarrator.domain.model.Chat
import io.github.yedidyatob.telegramnarrator.domain.model.Message
import io.github.yedidyatob.telegramnarrator.domain.repository.AuthRepository
import io.github.yedidyatob.telegramnarrator.domain.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val authRepository: AuthRepository,
    private val playbackManager: PlaybackManager
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    val unreadChats: StateFlow<List<Chat>> = chatRepository.getUnreadChats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isPlaying: StateFlow<Boolean> = playbackManager.isPlaying
    val isPaused: StateFlow<Boolean> = playbackManager.isPaused
    val playStatus: StateFlow<String?> = playbackManager.currentStatus
    val selectedChatIds: StateFlow<Set<Long>> = playbackManager.selectedChatIds
    val currentPlayingChatId: StateFlow<Long?> = playbackManager.currentPlayingChatId

    private val _selectedChat = MutableStateFlow<Chat?>(null)
    val selectedChat: StateFlow<Chat?> = _selectedChat.asStateFlow()

    private val _selectedChatMessages = MutableStateFlow<List<Message>?>(null)
    val selectedChatMessages: StateFlow<List<Message>?> = _selectedChatMessages.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            unreadChats.collect { chats ->
                playbackManager.syncSelectionWithUnreadChats(chats.map { it.id })
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

    fun refresh() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                chatRepository.loadChats()
                kotlinx.coroutines.delay(500)
            } catch (_: Exception) {
                // Surface via empty list / pull-to-refresh ending
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun selectChat(chat: Chat?) {
        _selectedChat.value = chat
        if (chat == null) {
            _selectedChatMessages.value = null
            return
        }
        viewModelScope.launch {
            try {
                // Oldest-first batch from the repository; reverse only for reading convenience in the sheet
                val oldestFirst = chatRepository.getChatMessages(chat.id)
                _selectedChatMessages.value = oldestFirst.asReversed()
            } catch (_: Exception) {
                _selectedChatMessages.value = emptyList()
            }
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
     * Respects [ChatRepository.markAsReadEnabled] (the Voice-settings switch / debug default).
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
