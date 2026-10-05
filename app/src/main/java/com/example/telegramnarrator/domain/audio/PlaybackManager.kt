package com.example.telegramnarrator.domain.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

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

    private val _shouldMarkAsRead = MutableStateFlow(true)
    val shouldMarkAsRead: StateFlow<Boolean> = _shouldMarkAsRead.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _selectedChatIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedChatIds: StateFlow<Set<Long>> = _selectedChatIds.asStateFlow()
    
    private var selectionInitialized = false

    fun setPlaying(playing: Boolean) {
        _isPlaying.value = playing
        if (!playing) {
            _currentStatus.value = null
            _isPaused.value = false
            _currentPlayingChatId.value = null
        }
    }

    fun setPlayingChatId(chatId: Long?) {
        _currentPlayingChatId.value = chatId
    }

    fun setShouldMarkAsRead(enabled: Boolean) {
        _shouldMarkAsRead.value = enabled
    }

    fun setPlaybackSpeed(speed: Float) {
        _playbackSpeed.value = speed
    }

    fun setPaused(paused: Boolean) {
        _isPaused.value = paused
    }

    fun setStatus(status: String) {
        _currentStatus.value = status
    }

    fun initializeSelection(chatIds: List<Long>) {
        if (!selectionInitialized && chatIds.isNotEmpty()) {
            _selectedChatIds.value = chatIds.toSet()
            selectionInitialized = true
        }
    }

    fun toggleSelection(chatId: Long) {
        _selectedChatIds.value = if (_selectedChatIds.value.contains(chatId)) {
            _selectedChatIds.value - chatId
        } else {
            _selectedChatIds.value + chatId
        }
    }

    fun selectAll(chatIds: List<Long>) {
        _selectedChatIds.value = chatIds.toSet()
    }

    fun deselectAll() {
        _selectedChatIds.value = emptySet()
    }
}
