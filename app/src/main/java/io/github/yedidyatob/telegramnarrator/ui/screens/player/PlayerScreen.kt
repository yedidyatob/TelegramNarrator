package io.github.yedidyatob.telegramnarrator.ui.screens.player

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.yedidyatob.telegramnarrator.R
import io.github.yedidyatob.telegramnarrator.ui.text.bidiSafe
import io.github.yedidyatob.telegramnarrator.core.labelRes
import io.github.yedidyatob.telegramnarrator.domain.audio.NowPlaying
import io.github.yedidyatob.telegramnarrator.domain.model.MessageContentType
import io.github.yedidyatob.telegramnarrator.domain.sponsored.SponsoredAd
import io.github.yedidyatob.telegramnarrator.ui.components.ChatAvatar
import io.github.yedidyatob.telegramnarrator.ui.components.SponsoredCard
import io.github.yedidyatob.telegramnarrator.ui.components.SponsoredReportDialog
import io.github.yedidyatob.telegramnarrator.ui.screens.settings.VoiceSettingsSheet
import io.github.yedidyatob.telegramnarrator.ui.text.ContentTextStyle
import io.github.yedidyatob.telegramnarrator.ui.text.ProvideContentDirection
import io.github.yedidyatob.telegramnarrator.ui.viewmodel.PlayerUiState
import io.github.yedidyatob.telegramnarrator.ui.viewmodel.PlayerViewModel
import io.github.yedidyatob.telegramnarrator.ui.viewmodel.SponsoredViewModel
import kotlin.coroutines.cancellation.CancellationException

/** Callbacks of the Player content, so previews / screenshots can render it without a ViewModel. */
data class PlayerActions(
    val onDismiss: () -> Unit = {},
    val onTogglePause: () -> Unit = {},
    val onPrevious: () -> Unit = {},
    val onNext: () -> Unit = {},
    val onNextChat: () -> Unit = {},
    val onStop: () -> Unit = {},
    val onOpenVoiceSettings: () -> Unit = {}
)

/** Callbacks of the sponsored card shown while an ad is read. */
data class PlayerAdActions(
    val onFullyVisible: (SponsoredAd) -> Unit = {},
    val onLinkClicked: (SponsoredAd, Boolean) -> Unit = { _, _ -> },
    val onReport: (SponsoredAd) -> Unit = {},
    val loadFile: suspend (Int) -> String? = { null }
)

/**
 * Full now-playing screen, opened from the mini player. Closes with the ⌄ button, the back gesture (with the
 * predictive-back preview: the screen shrinks and slides down as you swipe) or by itself when playback ends.
 */
@Composable
fun PlayerScreen(
    onDismiss: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
    sponsoredViewModel: SponsoredViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val reportDialog by sponsoredViewModel.reportDialog.collectAsStateWithLifecycle()
    var showVoiceSettings by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    // Playback ended (end of the queue, Stop, the notification): back to Home
    LaunchedEffect(state.isActive) { if (!state.isActive) onDismiss() }
    LaunchedEffect(Unit) {
        sponsoredViewModel.toasts.collect { android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show() }
    }

    var backProgress by remember { mutableFloatStateOf(0f) }
    PredictiveBackHandler { progress ->
        try {
            progress.collect { backProgress = it.progress }
            onDismiss()
        } catch (e: CancellationException) {
            // Gesture cancelled: spring back
            backProgress = 0f
        }
    }

    PlayerContent(
        state = state,
        actions = PlayerActions(
            onDismiss = onDismiss,
            onTogglePause = viewModel::togglePause,
            onPrevious = viewModel::previousMessage,
            onNext = viewModel::nextMessage,
            onNextChat = viewModel::nextChat,
            onStop = viewModel::stop,
            onOpenVoiceSettings = { showVoiceSettings = true }
        ),
        adActions = PlayerAdActions(
            onFullyVisible = sponsoredViewModel::onFullyVisible,
            onLinkClicked = sponsoredViewModel::onLinkClicked,
            onReport = sponsoredViewModel::startReport,
            loadFile = sponsoredViewModel::localFile
        ),
        loadPhoto = viewModel::chatPhotoPath,
        modifier = Modifier.graphicsLayer {
            // Material predictive back for a full-screen surface: shrink to 90 % and move with the gesture
            val scale = 1f - 0.1f * backProgress
            scaleX = scale
            scaleY = scale
            translationY = backProgress * 64.dp.toPx()
            shape = RoundedCornerShape((32 * backProgress).dp)
            clip = backProgress > 0f
        }
    )

    if (showVoiceSettings) VoiceSettingsSheet(onDismiss = { showVoiceSettings = false })
    reportDialog?.let { dialog ->
        SponsoredReportDialog(
            title = dialog.title,
            options = dialog.options,
            onChoose = sponsoredViewModel::chooseReportOption,
            onDismiss = sponsoredViewModel::dismissReport
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerContent(
    state: PlayerUiState,
    actions: PlayerActions,
    adActions: PlayerAdActions,
    loadPhoto: suspend (Int) -> String?,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.player_title), style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = actions.onDismiss) {
                        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = stringResource(R.string.player_close))
                    }
                },
                actions = {
                    state.engine?.let { engine ->
                        val label = engineText(engine)
                        val a11y = stringResource(R.string.player_engine_a11y, label)
                        AssistChip(
                            onClick = actions.onOpenVoiceSettings,
                            label = { Text(label, maxLines = 1) },
                            leadingIcon = {
                                Icon(
                                    if (engine.label == io.github.yedidyatob.telegramnarrator.ui.viewmodel.EngineLabel.ORIGINAL_AUDIO) Icons.Rounded.GraphicEq else Icons.Rounded.RecordVoiceOver,
                                    contentDescription = null,
                                    modifier = Modifier.size(AssistChipDefaults.IconSize)
                                )
                            },
                            colors = if (engine.isFallback) {
                                AssistChipDefaults.assistChipColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    labelColor = MaterialTheme.colorScheme.onErrorContainer,
                                    leadingIconContentColor = MaterialTheme.colorScheme.onErrorContainer
                                )
                            } else {
                                AssistChipDefaults.assistChipColors()
                            },
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .semantics { contentDescription = a11y }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp)
        ) {
            ChatHeader(state, loadPhoto)
            Spacer(Modifier.height(16.dp))
            NowReadingCard(state, adActions, Modifier.weight(1f).fillMaxWidth())
            Spacer(Modifier.height(16.dp))
            PositionRow(state)
            Spacer(Modifier.height(20.dp))
            TransportControls(state, actions)
            Spacer(Modifier.height(16.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                modifier = Modifier.fillMaxWidth()
            ) {
                FilledTonalButton(onClick = actions.onNextChat, enabled = state.isActive) {
                    Icon(Icons.Rounded.FastForward, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.player_next_chat))
                }
                OutlinedButton(onClick = actions.onStop, enabled = state.isActive) {
                    Icon(Icons.Rounded.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.home_btn_stop))
                }
            }
        }
    }
}

@Composable
private fun ChatHeader(state: PlayerUiState, loadPhoto: suspend (Int) -> String?) {
    val nowPlaying = state.nowPlaying
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (nowPlaying != null) {
            ChatAvatar(
                chatId = nowPlaying.chatId,
                title = nowPlaying.chatTitle,
                photoFileId = nowPlaying.chatPhotoFileId,
                loadFile = loadPhoto,
                size = 56.dp
            )
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = bidiSafe(nowPlaying?.chatTitle ?: stringResource(R.string.home_now_playing)),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() }
            )
            nowPlaying?.chatPosition?.let { position ->
                Text(
                    text = stringResource(R.string.player_chat_position, position.index, position.count),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** The message being read (direction-aware), the voice note / chat opening placeholder, or the ad. */
@Composable
private fun NowReadingCard(state: PlayerUiState, adActions: PlayerAdActions, modifier: Modifier) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier
    ) {
        Box {
            val nowPlaying = state.nowPlaying
            val key = when (val content = nowPlaying?.content) {
                is NowPlaying.Content.Message -> "m${nowPlaying.chatId}:${content.messageId}"
                NowPlaying.Content.ChatOpening -> "c${nowPlaying.chatId}"
                NowPlaying.Content.Sponsored -> "s${nowPlaying.chatId}"
                null -> "none"
            }
            AnimatedContent(
                targetState = key,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "now-reading"
            ) { _ ->
                val content = nowPlaying?.content
                when {
                    content is NowPlaying.Content.Message && content.isVoiceNote ->
                        CenteredInfo(Icons.Rounded.GraphicEq, stringResource(R.string.playback_voice_note), senderLine(content))
                    content is NowPlaying.Content.Message -> MessageText(content, nowPlaying.distinctSender)
                    content is NowPlaying.Content.Sponsored && state.sponsoredAd != null -> Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp)
                    ) {
                        SponsoredCard(
                            ad = state.sponsoredAd,
                            onFullyVisible = adActions.onFullyVisible,
                            onLinkClicked = adActions.onLinkClicked,
                            onReport = adActions.onReport,
                            loadFile = adActions.loadFile
                        )
                    }
                    content == NowPlaying.Content.ChatOpening || content == NowPlaying.Content.Sponsored ->
                        // The ding and the chat title are what is heard now; the count is in the position row below
                        CenteredInfo(Icons.Rounded.Forum, stringResource(R.string.player_chat_opening), bidiSafe(nowPlaying.chatTitle))
                    else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(16.dp))
                            Text(
                                state.status ?: stringResource(R.string.home_player_starting),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
            // "Preparing audio…" / "Paused" pill at the bottom of the card
            val pill = when {
                state.isPreparing -> stringResource(R.string.home_preparing_audio)
                state.isPaused -> stringResource(R.string.playback_paused)
                else -> null
            }
            AnimatedVisibility(
                visible = pill != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    shadowElevation = 2.dp
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
                    ) {
                        if (state.isPreparing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.inverseOnSurface
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(pill.orEmpty(), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun senderLine(content: NowPlaying.Content.Message): String =
    content.sender ?: stringResource(R.string.playback_unknown_sender)

@Composable
private fun MessageText(content: NowPlaying.Content.Message, sender: String?) {
    val scroll = rememberScrollState()
    LaunchedEffect(content.messageId) { scroll.scrollTo(0) }
    val mediaLabel = content.contentType.takeIf { it != MessageContentType.TEXT }?.labelRes()
    // The message's own direction: a Hebrew message reads right-to-left even on an English device
    ProvideContentDirection(content.text) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(horizontal = 24.dp, vertical = 20.dp)
                .padding(bottom = 40.dp)
        ) {
            // Groups name the sender; in channels and private chats it is the chat itself (already in the header)
            if (sender != null) {
                Text(
                    text = sender,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (mediaLabel != null) {
                if (sender != null) Spacer(Modifier.height(8.dp))
                Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                    Text(
                        text = stringResource(mediaLabel),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = content.text,
                style = MaterialTheme.typography.bodyLarge.merge(ContentTextStyle).copy(fontSize = 20.sp, lineHeight = 30.sp),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun CenteredInfo(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize().padding(24.dp)
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(88.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(40.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/** "Message 3 of 12" and the progress through the chat. */
@Composable
private fun PositionRow(state: PlayerUiState) {
    val position = state.nowPlaying?.messagePosition
    val text = when {
        position != null -> stringResource(R.string.player_message_position, position.index, position.count)
        state.nowPlaying?.content == NowPlaying.Content.Sponsored -> stringResource(R.string.sponsored_label)
        state.nowPlaying != null -> chatOpeningText(state.nowPlaying.messageCount)
        else -> ""
    }
    Column(Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        val track = MaterialTheme.colorScheme.surfaceContainerHighest
        if (position != null) {
            LinearProgressIndicator(
                progress = { position.fraction },
                trackColor = track,
                modifier = Modifier.fillMaxWidth().height(6.dp).clearAndSetSemantics { }
            )
        } else {
            LinearProgressIndicator(
                progress = { if (state.nowPlaying?.content == NowPlaying.Content.Sponsored) 1f else 0f },
                trackColor = track,
                modifier = Modifier.fillMaxWidth().height(6.dp).clearAndSetSemantics { }
            )
        }
    }
}

/**
 * Previous / play-pause / next message. Media transport controls keep their left-to-right order in RTL
 * layouts too (Material bidirectionality guidance), so they are laid out LTR.
 */
@Composable
private fun TransportControls(state: PlayerUiState, actions: PlayerActions) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(onClick = actions.onPrevious, enabled = state.canGoPrevious, modifier = Modifier.size(64.dp)) {
                Icon(Icons.Rounded.SkipPrevious, contentDescription = stringResource(R.string.player_previous), modifier = Modifier.size(40.dp))
            }
            Box(contentAlignment = Alignment.Center) {
                FilledIconButton(
                    onClick = actions.onTogglePause,
                    enabled = state.isActive,
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(),
                    modifier = Modifier.size(88.dp)
                ) {
                    Icon(
                        imageVector = if (state.isPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                        contentDescription = stringResource(if (state.isPaused) R.string.playback_resume else R.string.playback_pause),
                        modifier = Modifier.size(44.dp)
                    )
                }
                if (state.isPreparing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(100.dp),
                        strokeWidth = 3.dp
                    )
                }
            }
            IconButton(onClick = actions.onNext, enabled = state.isActive, modifier = Modifier.size(64.dp)) {
                Icon(Icons.Rounded.SkipNext, contentDescription = stringResource(R.string.player_next), modifier = Modifier.size(40.dp))
            }
        }
    }
}

