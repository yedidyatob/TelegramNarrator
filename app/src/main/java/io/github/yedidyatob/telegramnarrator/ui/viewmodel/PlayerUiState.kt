package io.github.yedidyatob.telegramnarrator.ui.viewmodel

import io.github.yedidyatob.telegramnarrator.domain.audio.NowPlaying
import io.github.yedidyatob.telegramnarrator.domain.sponsored.SponsoredAd
import io.github.yedidyatob.telegramnarrator.domain.tts.SpeechProvider

/** Which voice the Player's engine indicator names. */
enum class EngineLabel { SYSTEM, EDGE, GEMINI, ORIGINAL_AUDIO }

/** @param isFallback the system voice is speaking because the chosen online engine failed for this item */
data class EngineIndicator(val label: EngineLabel, val isFallback: Boolean = false)

/** Everything the Player screen and the mini player show. */
data class PlayerUiState(
    val isActive: Boolean = false,
    val isPaused: Boolean = false,
    /** The current item's audio is still being fetched / synthesized. */
    val isPreparing: Boolean = false,
    val nowPlaying: NowPlaying? = null,
    /** The sponsored message being read, when the current item is the chat's ad. */
    val sponsoredAd: SponsoredAd? = null,
    val engine: EngineIndicator? = null,
    /** The service's status line, shown while nothing is known yet (e.g. "Preparing your unread messages…"). */
    val status: String? = null
) {
    val canGoPrevious: Boolean
        get() = isActive && (nowPlaying?.content is NowPlaying.Content.Message || nowPlaying?.content is NowPlaying.Content.Sponsored)

    companion object {
        fun from(
            isPlaying: Boolean,
            isPaused: Boolean,
            isPreparing: Boolean,
            nowPlaying: NowPlaying?,
            playingAd: SponsoredAd?,
            selectedEngine: SpeechProvider,
            status: String?
        ): PlayerUiState {
            if (!isPlaying) return PlayerUiState(status = status)
            val ad = playingAd?.takeIf {
                nowPlaying?.content is NowPlaying.Content.Sponsored && it.chatId == nowPlaying.chatId
            }
            return PlayerUiState(
                isActive = true,
                isPaused = isPaused,
                // A paused item isn't being prepared
                isPreparing = isPreparing && !isPaused,
                nowPlaying = nowPlaying,
                sponsoredAd = ad,
                engine = engineIndicator(nowPlaying, selectedEngine),
                status = status
            )
        }

        fun engineIndicator(nowPlaying: NowPlaying?, selected: SpeechProvider): EngineIndicator? {
            nowPlaying ?: return null
            val content = nowPlaying.content
            if (content is NowPlaying.Content.Message && content.isVoiceNote) return EngineIndicator(EngineLabel.ORIGINAL_AUDIO)
            val active = nowPlaying.engine ?: selected
            return EngineIndicator(
                label = when (active) {
                    SpeechProvider.SYSTEM -> EngineLabel.SYSTEM
                    SpeechProvider.EDGE -> EngineLabel.EDGE
                    SpeechProvider.GEMINI -> EngineLabel.GEMINI
                },
                isFallback = active == SpeechProvider.SYSTEM && selected != SpeechProvider.SYSTEM
            )
        }
    }
}
