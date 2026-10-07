package io.github.yedidyatob.telegramnarrator.ui.screens.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.yedidyatob.telegramnarrator.R
import io.github.yedidyatob.telegramnarrator.ui.text.bidiSafe
import io.github.yedidyatob.telegramnarrator.ui.components.ChatAvatar
import io.github.yedidyatob.telegramnarrator.ui.viewmodel.PlayerUiState

/**
 * Compact now-playing bar at the bottom of Home: chat avatar and title, what is happening, the message
 * progress, play/pause, next message and stop. Tapping it opens the Player screen (skip chat and previous
 * message live there and in the notification).
 */
@Composable
fun MiniPlayer(
    state: PlayerUiState,
    onOpen: () -> Unit,
    onTogglePause: () -> Unit,
    onNextMessage: () -> Unit,
    onStop: () -> Unit,
    loadPhoto: suspend (Int) -> String?,
    modifier: Modifier = Modifier
) {
    val nowPlaying = state.nowPlaying
    val openLabel = stringResource(R.string.player_open)
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
        modifier = modifier
    ) {
        Column(Modifier.navigationBarsPadding()) {
            // Message progress in the chat; indeterminate while the audio is prepared
            Box(Modifier.fillMaxWidth().height(3.dp)) {
                val position = nowPlaying?.messagePosition
                when {
                    state.isPreparing -> LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(3.dp),
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    )
                    position != null -> LinearProgressIndicator(
                        progress = { position.fraction },
                        modifier = Modifier.fillMaxWidth().height(3.dp),
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClickLabel = openLabel, onClick = onOpen)
                    .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)
            ) {
                if (nowPlaying != null) {
                    ChatAvatar(
                        chatId = nowPlaying.chatId,
                        title = nowPlaying.chatTitle,
                        photoFileId = nowPlaying.chatPhotoFileId,
                        loadFile = loadPhoto,
                        size = 40.dp
                    )
                } else {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
                ) {
                    Text(
                        text = bidiSafe(nowPlaying?.chatTitle ?: stringResource(R.string.home_now_playing)),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AnimatedVisibility(visible = state.isPreparing, enter = fadeIn(), exit = fadeOut()) {
                            CircularProgressIndicator(
                                modifier = Modifier.padding(end = 6.dp).size(10.dp),
                                strokeWidth = 1.5.dp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Text(
                            text = miniPlayerSubtitle(state),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                IconButton(onClick = onTogglePause) {
                    Icon(
                        imageVector = if (state.isPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                        contentDescription = stringResource(if (state.isPaused) R.string.playback_resume else R.string.playback_pause)
                    )
                }
                IconButton(onClick = onNextMessage) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = stringResource(R.string.player_next))
                }
                IconButton(onClick = onStop) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.home_btn_stop))
                }
            }
        }
    }
}
