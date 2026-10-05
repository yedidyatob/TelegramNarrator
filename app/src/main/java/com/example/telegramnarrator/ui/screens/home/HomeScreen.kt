package com.example.telegramnarrator.ui.screens.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.ui.draw.scale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.AllInbox
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.text.font.FontWeight
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
    val selectedChat by viewModel.selectedChat.collectAsState()
    val selectedIds by viewModel.selectedChatIds.collectAsState()
    val playingChatId by viewModel.currentPlayingChatId.collectAsState()
    val shouldMarkAsRead by viewModel.shouldMarkAsRead.collectAsState()
    val playbackSpeed by viewModel.playbackSpeed.collectAsState()

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
            Surface(
                color = MaterialTheme.colorScheme.background,
                tonalElevation = 2.dp,
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Unread Chats",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (isLoading && !pullToRefreshState.isRefreshing) {
                         CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                         Row(verticalAlignment = Alignment.CenterVertically) {
                             TextButton(onClick = { viewModel.cyclePlaybackSpeed() }) {
                                 Text("${playbackSpeed}x", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                             }
                             IconButton(onClick = { viewModel.logout() }) {
                                 Icon(Icons.Default.Logout, contentDescription = "Logout", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                             }
                         }
                    }
                }
            }
        },
        floatingActionButton = {
            if (chats.isNotEmpty() && !isPlaying) {
                ExtendedFloatingActionButton(
                    text = { Text(stringResource(R.string.home_btn_play_all)) },
                    icon = { Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.home_btn_play_all)) },
                    onClick = { 
                        val selected = chats.filter { selectedIds.contains(it.id) }
                        if (selected.isNotEmpty()) onPlayAll(selected)
                    }
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
                                    action = com.example.telegramnarrator.core.service.PlaybackService.ACTION_SKIP_CHAT
                                })
                            }) {
                                Icon(
                                    imageVector = androidx.compose.material.icons.Icons.Default.SkipNext,
                                    contentDescription = "Skip Chat",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            IconButton(onClick = {
                                context.startService(android.content.Intent(context, com.example.telegramnarrator.core.service.PlaybackService::class.java).apply {
                                    action = com.example.telegramnarrator.core.service.PlaybackService.ACTION_SKIP_MSG
                                })
                            }) {
                                Icon(
                                    imageVector = androidx.compose.material.icons.Icons.Default.FastForward,
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
                        Icon(
                            imageVector = Icons.Default.AllInbox,
                            contentDescription = null,
                            modifier = Modifier.size(72.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "You're all caught up!",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No unread chats to narrate.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 80.dp) // Space for FAB/BottomBar
                ) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { viewModel.selectAll() }) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Select All")
                            }
                            Spacer(Modifier.width(8.dp))
                            TextButton(onClick = { viewModel.deselectAll() }) {
                                Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Deselect All")
                            }
                        }
                        // Debug Toggle Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Mark as read after playing",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Switch(
                                checked = shouldMarkAsRead,
                                onCheckedChange = { viewModel.setShouldMarkAsRead(it) },
                                modifier = Modifier.scale(0.8f)
                            )
                        }
                    }
                    items(chats, key = { it.id }) { chat ->
                        val currentItem by rememberUpdatedState(chat)
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = {
                                if (it == SwipeToDismissBoxValue.EndToStart || it == SwipeToDismissBoxValue.StartToEnd) {
                                    viewModel.markChatAsRead(currentItem.id)
                                    true
                                } else {
                                    false
                                }
                            }
                        )

                        SwipeToDismissBox(
                            state = dismissState,
                            backgroundContent = {
                                val color = MaterialTheme.colorScheme.primaryContainer
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 16.dp, vertical = 4.dp)
                                        .background(color, shape = CardDefaults.shape)
                                        .padding(horizontal = 20.dp),
                                    contentAlignment = Alignment.CenterEnd
                                ) {
                                    Icon(
                                        Icons.Default.DoneAll,
                                        contentDescription = "Mark as read",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        ) {
                            ChatListItem(
                                chat = chat,
                                isSelected = selectedIds.contains(chat.id),
                                isPlaying = playingChatId == chat.id,
                                onToggleSelection = { viewModel.toggleChatSelection(chat.id) },
                                onClick = { viewModel.selectChat(chat) },
                                onPlayClick = { onPlayAll(listOf(chat)) }
                            )
                        }
                    }
                }
            }

            PullToRefreshContainer(
                state = pullToRefreshState,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }

        val sheetMsgs = selectedMsgs
        val currentChat = selectedChat
        if (sheetMsgs != null && currentChat != null) {
            ModalBottomSheet(
                onDismissRequest = { viewModel.selectChat(null) }
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Text(
                        currentChat.title, 
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        if (currentChat.unreadCount > sheetMsgs.size) "Showing ${sheetMsgs.size} of ${currentChat.unreadCount} unread messages"
                        else "${sheetMsgs.size} unread messages",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
                
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
                                    Text(
                                        text = msg.text,
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
fun ChatListItem(
    chat: Chat, 
    isSelected: Boolean,
    isPlaying: Boolean = false,
    onToggleSelection: () -> Unit,
    onClick: () -> Unit, 
    onPlayClick: () -> Unit
) {
    val borderColor = if (isPlaying) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent
    val backgroundAlpha = if (isPlaying) 0.1f else 0f

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        border = if (isPlaying) androidx.compose.foundation.BorderStroke(2.dp, borderColor) else null,
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
                .background(MaterialTheme.colorScheme.primary.copy(alpha = backgroundAlpha))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggleSelection() }
            )
            
            Column(
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
            ) {
                Text(
                    text = chat.title, 
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Text(
                    text = "${chat.unreadCount} messages",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            IconButton(onClick = onPlayClick, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
