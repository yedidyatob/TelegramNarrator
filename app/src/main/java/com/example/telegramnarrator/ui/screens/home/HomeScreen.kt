package com.example.telegramnarrator.ui.screens.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.telegramnarrator.domain.model.Chat
import com.example.telegramnarrator.ui.viewmodel.HomeViewModel
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.telegramnarrator.R
import com.example.telegramnarrator.core.labelRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onPlayAll: (List<Chat>) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val chats by viewModel.unreadChats.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val isPaused by viewModel.isPaused.collectAsState()
    val playStatus by viewModel.playStatus.collectAsState()
    val selectedMsgs by viewModel.selectedChatMessages.collectAsState()
    var showVoiceSettings by rememberSaveable { mutableStateOf(false) }

    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    val pullToRefreshState = rememberPullToRefreshState()
    if (pullToRefreshState.isRefreshing) {
        LaunchedEffect(Unit) {
            viewModel.refresh()
        }
    }

    LaunchedEffect(isLoading) {
        if (!isLoading) {
            pullToRefreshState.endRefresh()
        } else {
            // If isLoading becomes true from outside (e.g. init), 
            // we don't necessarily want the pull-to-refresh spinner to show 
            // unless it was triggered by pull. 
            // But if it IS triggered by pull, it will already be in refreshing state.
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = { showVoiceSettings = true }) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = stringResource(R.string.settings_voice_open)
                        )
                    }
                    if (isLoading && !pullToRefreshState.isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp)
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
            if (chats.isNotEmpty() && !isPlaying) {
                ExtendedFloatingActionButton(
                    text = { Text(stringResource(R.string.home_btn_play_all)) },
                    icon = { Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.home_btn_play_all)) },
                    onClick = { onPlayAll(chats) }
                )
            }
        },
        bottomBar = {
            androidx.compose.animation.AnimatedVisibility(
                visible = isPlaying,
                enter = androidx.compose.animation.slideInVertically(initialOffsetY = { it }),
                exit = androidx.compose.animation.slideOutVertically(targetOffsetY = { it })
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    tonalElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding() // edge-to-edge: keep the controls above the system nav bar
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Now Playing",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = playStatus ?: "Initializing...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconButton(onClick = {
                                val actionStr = if (isPaused) "ACTION_RESUME" else "ACTION_PAUSE"
                                context.startService(android.content.Intent(context, com.example.telegramnarrator.core.service.PlaybackService::class.java).apply {
                                    action = actionStr
                                })
                            }) {
                                Icon(
                                    imageVector = if (isPaused) androidx.compose.material.icons.Icons.Default.PlayArrow else androidx.compose.material.icons.Icons.Default.Pause,
                                    contentDescription = "Play/Pause",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            IconButton(onClick = {
                                context.startService(android.content.Intent(context, com.example.telegramnarrator.core.service.PlaybackService::class.java).apply {
                                    action = com.example.telegramnarrator.core.service.PlaybackService.ACTION_SKIP_MSG
                                })
                            }) {
                                Icon(
                                    imageVector = androidx.compose.material.icons.Icons.Default.SkipNext,
                                    contentDescription = "Skip Msg",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            IconButton(onClick = {
                                context.startService(android.content.Intent(context, com.example.telegramnarrator.core.service.PlaybackService::class.java).apply {
                                    action = com.example.telegramnarrator.core.service.PlaybackService.ACTION_STOP
                                })
                            }) {
                                Icon(
                                    imageVector = androidx.compose.material.icons.Icons.Default.Close,
                                    contentDescription = "Stop",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }
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
                // Use a scrollable container even when empty so pull-to-refresh works
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    item {
                        Text(
                            text = stringResource(R.string.home_no_chats),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    items(chats) { chat ->
                        ChatListItem(
                            chat = chat, 
                            onClick = { viewModel.selectChat(chat.id) },
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
            com.example.telegramnarrator.ui.screens.settings.VoiceSettingsSheet(onDismiss = { showVoiceSettings = false })
        }

        val sheetMsgs = selectedMsgs
        if (sheetMsgs != null) {
            ModalBottomSheet(
                onDismissRequest = { viewModel.selectChat(null) }
            ) {
                if (sheetMsgs.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    LazyColumn(contentPadding = PaddingValues(16.dp)) {
                        items(sheetMsgs) { msg ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (msg.isOutgoing) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = msg.senderName ?: "Unknown",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    val displayText = msg.text.ifBlank {
                                        stringResource(msg.contentType.labelRes() ?: R.string.message_unsupported_content)
                                    }
                                    Text(
                                        text = displayText,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
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
fun ChatListItem(chat: Chat, onClick: () -> Unit, onPlayClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = chat.title, 
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Badge {
                        Text(text = chat.unreadCount.toString())
                    }
                    IconButton(onClick = onPlayClick, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.PlayArrow,
                            contentDescription = "Play Only This Chat", 
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}
