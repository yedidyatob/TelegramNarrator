package io.github.yedidyatob.telegramnarrator.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.yedidyatob.telegramnarrator.core.playback.PlaybackController
import io.github.yedidyatob.telegramnarrator.data.tts.TtsPreferences
import io.github.yedidyatob.telegramnarrator.domain.audio.PlaybackManager
import io.github.yedidyatob.telegramnarrator.domain.repository.ChatRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** State and commands of the Player screen and the Home mini player. */
@HiltViewModel
class PlayerViewModel @Inject constructor(
    playbackManager: PlaybackManager,
    ttsPreferences: TtsPreferences,
    private val controller: PlaybackController,
    private val chatRepository: ChatRepository
) : ViewModel() {

    private val flags = combine(
        playbackManager.isPlaying,
        playbackManager.isPaused,
        playbackManager.isPreparingAudio
    ) { playing, paused, preparing -> Triple(playing, paused, preparing) }

    val state: StateFlow<PlayerUiState> = combine(
        flags,
        playbackManager.nowPlaying,
        playbackManager.sponsoredAd,
        ttsPreferences.settings.map { it.provider },
        playbackManager.currentStatus
    ) { (playing, paused, preparing), nowPlaying, ad, engine, status ->
        PlayerUiState.from(playing, paused, preparing, nowPlaying, ad, engine, status)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        PlayerUiState.from(
            playbackManager.isPlaying.value,
            playbackManager.isPaused.value,
            playbackManager.isPreparingAudio.value,
            playbackManager.nowPlaying.value,
            playbackManager.sponsoredAd.value,
            ttsPreferences.settings.value.provider,
            playbackManager.currentStatus.value
        )
    )

    fun togglePause() = controller.togglePause(state.value.isPaused)
    fun nextMessage() = controller.nextMessage()
    fun previousMessage() = controller.previousMessage()
    fun nextChat() = controller.nextChat()
    fun stop() = controller.stop()

    suspend fun chatPhotoPath(fileId: Int): String? = chatRepository.getFilePath(fileId)
}
