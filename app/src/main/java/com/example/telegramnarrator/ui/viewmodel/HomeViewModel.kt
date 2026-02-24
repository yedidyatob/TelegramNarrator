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

    init {
        refresh()
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

    private val _selectedChatMessages = MutableStateFlow<List<com.example.telegramnarrator.domain.model.Message>?>(null)
    val selectedChatMessages: StateFlow<List<com.example.telegramnarrator.domain.model.Message>?> = _selectedChatMessages.asStateFlow()
    
    fun selectChat(chatId: Long?) {
        if (chatId == null) {
            _selectedChatMessages.value = null
            return
        }
        viewModelScope.launch {
            try {
                // Fetch up to 20 unread messages for the scrub UI
                val msgs = chatRepository.getChatMessages(chatId, 20)
                _selectedChatMessages.value = msgs.reversed()
            } catch (e: Exception) {
                _selectedChatMessages.value = emptyList()
            }
        }
    }
}
