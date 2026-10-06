package com.example.telegramnarrator.domain.audio

import com.example.telegramnarrator.domain.home.ChatSelectionLogic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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

    // ---- "Preparing audio…" (current message's audio not ready yet) ------------------------------

    private val audioLoading = DelayedLoading()
    private val audioLoadingLock = Any()
    private val timerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private fun nowMs(): Long = System.nanoTime() / 1_000_000L

    private val _isPreparingAudio = MutableStateFlow(false)

    /**
     * True while the current message's audio is being fetched / synthesized (Edge, OpenAI, a voice note
     * download, or the system engine not having started the utterance yet). Only turns true after
     * [DelayedLoading.DEFAULT_DELAY_MS], so cache hits never flash it.
     */
    val isPreparingAudio: StateFlow<Boolean> = _isPreparingAudio.asStateFlow()

    /** The current item's audio started loading; returns a token for [endAudioLoading]. Replaces any older load. */
    fun beginAudioLoading(): Long {
        val token = synchronized(audioLoadingLock) {
            _isPreparingAudio.value = false
            audioLoading.begin(nowMs())
        }
        timerScope.launch {
            delay(audioLoading.delayMs)
            synchronized(audioLoadingLock) {
                if (audioLoading.isPending(token) && audioLoading.isVisible(nowMs())) _isPreparingAudio.value = true
            }
        }
        return token
    }

    /** The load [token] finished (audio ready, failed, or fell back). Ignored when a newer load replaced it. */
    fun endAudioLoading(token: Long) {
        synchronized(audioLoadingLock) {
            if (audioLoading.end(token)) _isPreparingAudio.value = false
        }
    }

    /** Drops any pending load (next item, skip, pause, stop). */
    fun clearAudioLoading() {
        synchronized(audioLoadingLock) {
            audioLoading.clear()
            _isPreparingAudio.value = false
        }
    }

    fun setPlaying(playing: Boolean) {
        _isPlaying.value = playing
        if (!playing) {
            _currentStatus.value = null
            _isPaused.value = false
            _currentPlayingChatId.value = null
            clearAudioLoading()
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
