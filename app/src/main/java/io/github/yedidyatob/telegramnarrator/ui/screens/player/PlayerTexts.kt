package io.github.yedidyatob.telegramnarrator.ui.screens.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.text.BidiFormatter
import io.github.yedidyatob.telegramnarrator.R
import io.github.yedidyatob.telegramnarrator.domain.audio.NowPlaying
import io.github.yedidyatob.telegramnarrator.ui.viewmodel.EngineIndicator
import io.github.yedidyatob.telegramnarrator.ui.viewmodel.EngineLabel
import io.github.yedidyatob.telegramnarrator.ui.viewmodel.PlayerUiState

/** A name (sender, chat) inside a UI template keeps its own direction ("From חדשות · 3 of 12"). */
internal fun isolate(name: String): String = BidiFormatter.getInstance().unicodeWrap(name)

/** Second line of the mini player: what is happening now. */
@Composable
internal fun miniPlayerSubtitle(state: PlayerUiState): String {
    if (state.isPreparing) return stringResource(R.string.home_preparing_audio)
    if (state.isPaused) return stringResource(R.string.playback_paused)
    val nowPlaying = state.nowPlaying ?: return state.status ?: stringResource(R.string.home_player_starting)
    return when (val content = nowPlaying.content) {
        is NowPlaying.Content.Message -> {
            val sender = isolate(content.sender ?: stringResource(R.string.playback_unknown_sender))
            val position = nowPlaying.messagePosition
            if (position != null) stringResource(R.string.mini_player_message, sender, position.index, position.count)
            else sender
        }
        NowPlaying.Content.ChatOpening -> chatOpeningText(nowPlaying.messageCount)
        NowPlaying.Content.Sponsored -> stringResource(R.string.sponsored_label)
    }
}

@Composable
internal fun chatOpeningText(messageCount: Int): String =
    if (messageCount > 0) {
        LocalContext.current.resources.getQuantityString(R.plurals.home_unread_messages, messageCount, messageCount)
    } else {
        stringResource(R.string.home_player_starting)
    }

@Composable
internal fun engineText(engine: EngineIndicator): String {
    val name = stringResource(
        when (engine.label) {
            EngineLabel.SYSTEM -> R.string.player_engine_system
            EngineLabel.EDGE -> R.string.player_engine_edge
            EngineLabel.OPENAI -> R.string.player_engine_openai
            EngineLabel.ORIGINAL_AUDIO -> R.string.player_engine_original
        }
    )
    return if (engine.isFallback) stringResource(R.string.player_engine_fallback, name) else name
}
