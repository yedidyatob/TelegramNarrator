package com.example.telegramnarrator.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.telegramnarrator.domain.model.Chat
import com.example.telegramnarrator.domain.repository.AuthRepository
import com.example.telegramnarrator.domain.repository.ChatRepository
import com.example.telegramnarrator.domain.audio.PlaybackManager
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
    val shouldMarkAsRead: StateFlow<Boolean> = playbackManager.shouldMarkAsRead
    val playbackSpeed: StateFlow<Float> = playbackManager.playbackSpeed

    init {
        refresh()
        viewModelScope.launch {
            unreadChats.collect { chats ->
                playbackManager.initializeSelection(chats.map { it.id })
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                authRepository.logOut()
            } catch (e: Exception) {
                // We'll ignore logout errors for now, as typical error is that client is already closing
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
                // Wait a bit to show the spinner if it's too fast
                kotlinx.coroutines.delay(500)
            } catch (e: Exception) {
                // Handle error
            } finally {
                _isLoading.value = false
            }
        }
    }

    private val _selectedChat = MutableStateFlow<Chat?>(null)
    val selectedChat: StateFlow<Chat?> = _selectedChat.asStateFlow()

    private val _selectedChatMessages = MutableStateFlow<List<com.example.telegramnarrator.domain.model.Message>?>(null)
    val selectedChatMessages: StateFlow<List<com.example.telegramnarrator.domain.model.Message>?> = _selectedChatMessages.asStateFlow()
    
    fun selectChat(chat: Chat?) {
        _selectedChat.value = chat
        if (chat == null) {
            _selectedChatMessages.value = null
            return
        }
        viewModelScope.launch {
            try {
                val limit = minOf(chat.unreadCount, 50).coerceAtLeast(1)
                val msgs = chatRepository.getChatMessages(chat.id, limit)
                _selectedChatMessages.value = msgs.reversed()
            } catch (e: Exception) {
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

    fun setShouldMarkAsRead(enabled: Boolean) {
        playbackManager.setShouldMarkAsRead(enabled)
    }

    fun cyclePlaybackSpeed() {
        val nextMode = when (playbackSpeed.value) {
            1.0f -> 1.25f
            1.25f -> 1.5f
            1.5f -> 2.0f
            else -> 1.0f
        }
        playbackManager.setPlaybackSpeed(nextMode)
    }

    fun markChatAsRead(chatId: Long) {
        viewModelScope.launch {
            try {
                chatRepository.markChatAsRead(chatId)
            } catch (e: Exception) {
                // Handle error
            }
        }
    }
}
