package io.github.yedidyatob.telegramnarrator.ui.screens.home

import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.yedidyatob.telegramnarrator.R
import io.github.yedidyatob.telegramnarrator.domain.home.ConnectionBanner
import io.github.yedidyatob.telegramnarrator.domain.home.HomeContent
import io.github.yedidyatob.telegramnarrator.domain.model.Chat
import io.github.yedidyatob.telegramnarrator.ui.components.SponsoredCard
import io.github.yedidyatob.telegramnarrator.ui.components.SponsoredReportDialog
import io.github.yedidyatob.telegramnarrator.ui.screens.player.MiniPlayer
import io.github.yedidyatob.telegramnarrator.ui.screens.settings.VoiceSettingsSheet
import io.github.yedidyatob.telegramnarrator.ui.viewmodel.HomeViewModel
import io.github.yedidyatob.telegramnarrator.ui.viewmodel.PlayerViewModel
import io.github.yedidyatob.telegramnarrator.ui.viewmodel.SponsoredViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onPlayAll: (List<Chat>) -> Unit,
    onOpenPlayer: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
    sponsoredViewModel: SponsoredViewModel = hiltViewModel(),
    playerViewModel: PlayerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val chats by viewModel.unreadChats.collectAsStateWithLifecycle()
    val player by playerViewModel.state.collectAsStateWithLifecycle()
    val selectedMsgs by viewModel.selectedChatMessages.collectAsStateWithLifecycle()
    val selectedChat by viewModel.selectedChat.collectAsStateWithLifecycle()
    val selectedIds by viewModel.selectedChatIds.collectAsStateWithLifecycle()
    val playingChatId by viewModel.currentPlayingChatId.collectAsStateWithLifecycle()
    var showVoiceSettings by rememberSaveable { mutableStateOf(false) }
    var showLogoutConfirm by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    // Official Telegram sponsored messages (Telegram API ToS 3.3)
    val playingAd by sponsoredViewModel.playingAd.collectAsStateWithLifecycle()
    val previewAd by sponsoredViewModel.previewAd.collectAsStateWithLifecycle()
    val reportDialog by sponsoredViewModel.reportDialog.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val snackbarHostState = remember { SnackbarHostState() }
    val refreshFailed = stringResource(R.string.home_refresh_failed)
    val retryLabel = stringResource(R.string.home_retry)
    LaunchedEffect(Unit) {
        sponsoredViewModel.toasts.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }
    LaunchedEffect(Unit) {
        viewModel.refreshErrors.collect {
            val result = snackbarHostState.showSnackbar(refreshFailed, actionLabel = retryLabel)
            if (result == SnackbarResult.ActionPerformed) viewModel.refresh()
        }
    }
    // Opening a chat's preview sheet "opens" the chat: fetch its sponsored message (channels / bots only)
    LaunchedEffect(selectedChat?.id) { sponsoredViewModel.loadPreviewAd(selectedChat?.id) }
    val pullToRefreshState = rememberPullToRefreshState()
    if (pullToRefreshState.isRefreshing) {
        LaunchedEffect(Unit) { viewModel.refresh() }
    }
    LaunchedEffect(uiState.isRefreshing) {
        if (!uiState.isRefreshing) pullToRefreshState.endRefresh()
    }

    val selectedForPlayback = chats.filter { it.id in selectedIds }
    val fabLabel = when {
        selectedForPlayback.size == chats.size -> stringResource(R.string.home_btn_play_all)
        else -> stringResource(R.string.home_btn_play_selected, selectedForPlayback.size)
    }
    val content = uiState.content

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(stringResource(R.string.home_title)) },
                    actions = {
                        IconButton(onClick = { showVoiceSettings = true }) {
                            Icon(Icons.Rounded.Tune, contentDescription = stringResource(R.string.settings_voice_open))
                        }
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.home_more_options))
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                val privacyUrl = stringResource(R.string.privacy_policy_url)
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.login_privacy_policy)) },
                                    onClick = {
                                        menuOpen = false
                                        try {
                                            uriHandler.openUri(privacyUrl)
                                        } catch (e: ActivityNotFoundException) {
                                            Toast.makeText(context, R.string.link_open_failed, Toast.LENGTH_SHORT).show()
                                        } catch (e: IllegalArgumentException) {
                                            Toast.makeText(context, R.string.link_open_failed, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.home_btn_logout)) },
                                    onClick = {
                                        menuOpen = false
                                        showLogoutConfirm = true
                                    }
                                )
                            }
                        }
                    }
                )
                // A background refresh with chats on screen: a thin bar rather than replacing the list
                val backgroundRefresh = uiState.isRefreshing && content is HomeContent.Chats && !pullToRefreshState.isRefreshing
                Box(Modifier.fillMaxWidth().height(2.dp)) {
                    if (backgroundRefresh) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
                }
                AnimatedVisibility(
                    visible = uiState.banner != null,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    // Keep showing the last banner while it animates out
                    var lastBanner by remember { mutableStateOf(ConnectionBanner.CONNECTING) }
                    uiState.banner?.let { lastBanner = it }
                    ConnectionBannerBar(lastBanner)
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (content is HomeContent.Chats && !player.isActive && selectedForPlayback.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    text = { Text(fabLabel) },
                    icon = { Icon(Icons.Rounded.PlayArrow, contentDescription = null) },
                    onClick = { onPlayAll(selectedForPlayback) }
                )
            }
        },
        bottomBar = {
            Column {
                // Sponsored message of the chat that was just played: from when it is spoken until the next chat
                playingAd?.let { ad ->
                    SponsoredCard(
                        ad = ad,
                        onFullyVisible = sponsoredViewModel::onFullyVisible,
                        onLinkClicked = sponsoredViewModel::onLinkClicked,
                        onReport = sponsoredViewModel::startReport,
                        loadFile = sponsoredViewModel::localFile,
                        modifier = Modifier
                            .then(if (player.isActive) Modifier else Modifier.navigationBarsPadding())
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                AnimatedVisibility(
                    visible = player.isActive,
                    enter = slideInVertically(initialOffsetY = { it }),
                    exit = slideOutVertically(targetOffsetY = { it })
                ) {
                    MiniPlayer(
                        state = player,
                        onOpen = onOpenPlayer,
                        onTogglePause = playerViewModel::togglePause,
                        onNextMessage = playerViewModel::nextMessage,
                        onStop = playerViewModel::stop,
                        loadPhoto = playerViewModel::chatPhotoPath
                    )
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
            AnimatedContent(
                targetState = content,
                contentKey = { it::class },
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "home-content"
            ) { state ->
                when (state) {
                    HomeContent.Loading -> HomeLoadingSkeleton()
                    HomeContent.Empty -> HomeEmptyState(onRefresh = viewModel::refresh)
                    HomeContent.Offline -> HomeOfflineState()
                    is HomeContent.Error -> HomeErrorState(detail = state.detail, onRetry = viewModel::refresh)
                    is HomeContent.Chats -> ChatList(
                        chats = state.chats,
                        selectedIds = selectedIds,
                        playingChatId = playingChatId,
                        onToggle = viewModel::toggleChatSelection,
                        onSelectAll = viewModel::selectAll,
                        onDeselectAll = viewModel::deselectAll,
                        onOpen = viewModel::selectChat,
                        onPlay = { onPlayAll(listOf(it)) },
                        loadPhoto = viewModel::chatPhotoPath
                    )
                }
            }

            PullToRefreshContainer(
                state = pullToRefreshState,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }

        if (showVoiceSettings) {
            VoiceSettingsSheet(onDismiss = { showVoiceSettings = false })
        }

        if (showLogoutConfirm) {
            AlertDialog(
                onDismissRequest = { showLogoutConfirm = false },
                title = { Text(stringResource(R.string.home_logout_title)) },
                text = { Text(stringResource(R.string.home_logout_body)) },
                confirmButton = {
                    TextButton(onClick = {
                        showLogoutConfirm = false
                        viewModel.logout()
                    }) { Text(stringResource(R.string.home_btn_logout)) }
                },
                dismissButton = {
                    TextButton(onClick = { showLogoutConfirm = false }) { Text(stringResource(R.string.sponsored_cancel)) }
                }
            )
        }

        selectedChat?.let { chat ->
            ChatPreviewSheet(
                chat = chat,
                messages = selectedMsgs,
                ad = previewAd?.takeIf { it.chatId == chat.id },
                onDismiss = { viewModel.selectChat(null) },
                onPlay = {
                    viewModel.selectChat(null)
                    onPlayAll(listOf(chat))
                },
                onMarkAsRead = viewModel::markSelectedChatAsRead,
                loadPhoto = viewModel::chatPhotoPath,
                onAdFullyVisible = sponsoredViewModel::onFullyVisible,
                onAdLinkClicked = sponsoredViewModel::onLinkClicked,
                onAdReport = sponsoredViewModel::startReport,
                loadAdFile = sponsoredViewModel::localFile
            )
        }

        reportDialog?.let { dialog ->
            SponsoredReportDialog(
                title = dialog.title,
                options = dialog.options,
                onChoose = sponsoredViewModel::chooseReportOption,
                onDismiss = sponsoredViewModel::dismissReport
            )
        }
    }
}

/** The unread chats with the selection header. */
@Composable
internal fun ChatList(
    chats: List<Chat>,
    selectedIds: Set<Long>,
    playingChatId: Long?,
    onToggle: (Long) -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onOpen: (Chat) -> Unit,
    onPlay: (Chat) -> Unit,
    loadPhoto: suspend (Int) -> String?
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 96.dp)
    ) {
        item(key = "header") {
            val selectedCount = chats.count { it.id in selectedIds }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 8.dp, top = 4.dp)
            ) {
                Text(
                    text = stringResource(R.string.home_selected_count, selectedCount, chats.size),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                if (selectedCount < chats.size) {
                    TextButton(onClick = onSelectAll) { Text(stringResource(R.string.home_select_all)) }
                } else {
                    TextButton(onClick = onDeselectAll) { Text(stringResource(R.string.home_deselect_all)) }
                }
            }
        }
        items(chats, key = { it.id }) { chat ->
            ChatListItem(
                chat = chat,
                isSelected = chat.id in selectedIds,
                isPlaying = playingChatId == chat.id,
                onToggleSelection = { onToggle(chat.id) },
                onClick = { onOpen(chat) },
                onPlayClick = { onPlay(chat) },
                loadPhoto = loadPhoto
            )
        }
    }
}
