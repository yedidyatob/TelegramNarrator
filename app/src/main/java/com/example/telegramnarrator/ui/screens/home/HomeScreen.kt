package com.example.telegramnarrator.ui.screens.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.telegramnarrator.domain.model.Chat
import com.example.telegramnarrator.ui.viewmodel.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onPlayAll: (List<Chat>) -> Unit,
    onChatClick: (Long) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val chats by viewModel.unreadChats.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Telegram Narrator") })
        },
        floatingActionButton = {
            if (chats.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    text = { Text("Play All") },
                    icon = { Icon(Icons.Default.PlayArrow, contentDescription = "Play All") },
                    onClick = { onPlayAll(chats) }
                )
            }
        }
    ) { innerPadding ->
        if (chats.isEmpty()) {
            Box(
                modifier = Modifier.padding(innerPadding).fillMaxSize(),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                Text("No unread messages")
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(innerPadding),
                contentPadding = PaddingValues(16.dp)
            ) {
                items(chats) { chat ->
                    ChatListItem(chat = chat, onClick = { onChatClick(chat.id) })
                }
            }
        }
    }
}

@Composable
fun ChatListItem(chat: Chat, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = chat.title, style = MaterialTheme.typography.titleMedium)
                Badge {
                    Text(text = chat.unreadCount.toString())
                }
            }
        }
    }
}
