package io.github.yedidyatob.telegramnarrator.ui.screens.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AllInbox
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.yedidyatob.telegramnarrator.R
import io.github.yedidyatob.telegramnarrator.core.labelRes
import io.github.yedidyatob.telegramnarrator.core.service.PlaybackService
import io.github.yedidyatob.telegramnarrator.domain.model.Chat
import io.github.yedidyatob.telegramnarrator.ui.viewmodel.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onPlayAll: (List<Chat>) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val chats by viewModel.unreadChats.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val isPaused by viewModel.isPaused.collectAsStateWithLifecycle()
    val playStatus by viewModel.playStatus.collectAsStateWithLifecycle()
    val preparingAudio by viewModel.isPreparingAudio.collectAsStateWithLifecycle()
    val selectedMsgs by viewModel.selectedChatMessages.collectAsStateWithLifecycle()
    val selectedChat by viewModel.selectedChat.collectAsStateWithLifecycle()
    val selectedIds by viewModel.selectedChatIds.collectAsStateWithLifecycle()
    val playingChatId by viewModel.currentPlayingChatId.collectAsStateWithLifecycle()
    var showVoiceSettings by rememberSaveable { mutableStateOf(false) }

    val context = LocalContext.current
    val pullToRefreshState = rememberPullToRefreshState()
    if (pullToRefreshState.isRefreshing) {
        LaunchedEffect(Unit) { viewModel.refresh() }
    }
    LaunchedEffect(isLoading) {
        if (!isLoading) pullToRefreshState.endRefresh()
    }

    val selectedForPlayback = chats.filter { it.id in selectedIds }
    val fabLabel = when {
        selectedForPlayback.isEmpty() -> stringResource(R.string.home_btn_play_all)
        selectedForPlayback.size == chats.size -> stringResource(R.string.home_btn_play_all)
        else -> stringResource(R.string.home_btn_play_selected, selectedForPlayback.size)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.home_title)) },
                actions = {
                    IconButton(onClick = { showVoiceSettings = true }) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = stringResource(R.string.settings_voice_open)
                        )
                    }
                    if (isLoading && !pullToRefreshState.isRefreshing) {
                        val loadingDescription = stringResource(R.string.home_loading)
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(24.dp)
                                .semantics { contentDescription = loadingDescription }
                        )
                    } else {
                        TextButton(onClick = { viewModel.logout() }) {
                            Text(stringResource(R.string.home_btn_logout))
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (chats.isNotEmpty() && !isPlaying && selectedForPlayback.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    text = { Text(fabLabel) },
                    icon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                    onClick = { onPlayAll(selectedForPlayback) }
                )
            }
        },
        bottomBar = {
            androidx.compose.animation.AnimatedVisibility(
                visible = isPlaying,
                enter = androidx.compose.animation.slideInVertically(initialOffsetY = { it }),
                exit = androidx.compose.animation.slideOutVertically(targetOffsetY = { it })
            ) {
                NowPlayingBar(
                    status = playStatus ?: stringResource(R.string.home_player_starting),
                    isPaused = isPaused,
                    preparingAudio = preparingAudio && !isPaused,
                    onTogglePause = {
                        val action = if (isPaused) PlaybackService.ACTION_RESUME else PlaybackService.ACTION_PAUSE
                        context.startService(
                            android.content.Intent(context, PlaybackService::class.java).apply { this.action = action }
                        )
                    },
                    onSkipMessage = {
                        context.startService(
                            android.content.Intent(context, PlaybackService::class.java).apply {
                                action = PlaybackService.ACTION_SKIP_MSG
                            }
                        )
                    },
                    onSkipChat = {
                        context.startService(
                            android.content.Intent(context, PlaybackService::class.java).apply {
                                action = PlaybackService.ACTION_SKIP_CHAT
                            }
                        )
                    },
                    onStop = {
                        context.startService(
                            android.content.Intent(context, PlaybackService::class.java).apply {
                                action = PlaybackService.ACTION_STOP
                            }
                        )
                    }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .nestedScroll(pullToRefreshState.nestedScrollConnection)
        ) {
            if (chats.isEmpty()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    item {
                        Icon(
                            imageVector = Icons.Default.AllInbox,
                            contentDescription = null,
                            modifier = Modifier.size(72.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.home_empty_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.home_empty_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 88.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { viewModel.selectAll() }) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.home_select_all))
                            }
                            TextButton(onClick = { viewModel.deselectAll() }) {
                                Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.home_deselect_all))
                            }
                        }
                    }
                    items(chats, key = { it.id }) { chat ->
                        ChatListItem(
                            chat = chat,
                            isSelected = chat.id in selectedIds,
                            isPlaying = playingChatId == chat.id,
                            onToggleSelection = { viewModel.toggleChatSelection(chat.id) },
                            onClick = { viewModel.selectChat(chat) },
                            onPlayClick = { onPlayAll(listOf(chat)) }
                        )
                    }
                }
            }

            PullToRefreshContainer(
                state = pullToRefreshState,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }

        if (showVoiceSettings) {
            io.github.yedidyatob.telegramnarrator.ui.screens.settings.VoiceSettingsSheet(
                onDismiss = { showVoiceSettings = false }
            )
        }

        val sheetMsgs = selectedMsgs
        val currentChat = selectedChat
        if (sheetMsgs != null && currentChat != null) {
            ModalBottomSheet(onDismissRequest = { viewModel.selectChat(null) }) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Text(
                        currentChat.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (currentChat.unreadCount > sheetMsgs.size) {
                            stringResource(
                                R.string.home_sheet_showing_partial,
                                sheetMsgs.size,
                                currentChat.unreadCount
                            )
                        } else {
                            stringResource(R.string.home_sheet_showing_all, sheetMsgs.size)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (sheetMsgs.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { viewModel.markSelectedChatAsRead() },
                            modifier = Modifier.semantics {
                                contentDescription = context.getString(R.string.home_mark_chat_read_cd)
                            }
                        ) {
                            Icon(Icons.Default.DoneAll, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.home_mark_chat_read))
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                if (sheetMsgs.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    LazyColumn(contentPadding = PaddingValues(16.dp)) {
                        items(sheetMsgs, key = { it.id }) { msg ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (msg.isOutgoing) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    }
                                )
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = msg.senderName
                                            ?: stringResource(R.string.playback_unknown_sender),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    val displayText = msg.text.ifBlank {
                                        stringResource(
                                            msg.contentType.labelRes()
                                                ?: R.string.message_unsupported_content
                                        )
                                    }
                                    Text(text = displayText, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NowPlayingBar(
    status: String,
    isPaused: Boolean,
    preparingAudio: Boolean,
    onTogglePause: () -> Unit,
    onSkipMessage: () -> Unit,
    onSkipChat: () -> Unit,
    onStop: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        tonalElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (preparingAudio) {
                    // The current message's audio is still being fetched / synthesized
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = stringResource(R.string.home_preparing_audio),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                } else {
                    Text(
                        text = stringResource(R.string.home_now_playing),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onTogglePause) {
                    Icon(
                        imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = stringResource(
                            if (isPaused) R.string.playback_resume else R.string.playback_pause
                        ),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                IconButton(onClick = onSkipMessage) {
                    Icon(
                        imageVector = Icons.Default.FastForward,
                        contentDescription = stringResource(R.string.playback_skip_msg),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                IconButton(onClick = onSkipChat) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = stringResource(R.string.playback_skip_chat),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                IconButton(onClick = onStop) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.home_btn_stop),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

@Composable
fun ChatListItem(
    chat: Chat,
    isSelected: Boolean,
    isPlaying: Boolean,
    onToggleSelection: () -> Unit,
    onClick: () -> Unit,
    onPlayClick: () -> Unit
) {
    val selectDescription = stringResource(R.string.home_select_chat, chat.title)
    val unreadDescription = LocalContext.current.resources
        .getQuantityString(R.plurals.home_unread_messages, chat.unreadCount, chat.unreadCount)

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        border = if (isPlaying) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = if (isPlaying) 8.dp else 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp)
            } else {
                MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .background(
                    MaterialTheme.colorScheme.primary.copy(alpha = if (isPlaying) 0.08f else 0f)
                )
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggleSelection() },
                modifier = Modifier.semantics { contentDescription = selectDescription }
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp)
            ) {
                Text(
                    text = chat.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = unreadDescription,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Badge(modifier = Modifier.semantics { contentDescription = unreadDescription }) {
                Text(text = chat.unreadCount.toString())
            }
            IconButton(onClick = onPlayClick) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = stringResource(R.string.home_play_chat, chat.title),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
