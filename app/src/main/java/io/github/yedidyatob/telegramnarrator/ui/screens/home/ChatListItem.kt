package io.github.yedidyatob.telegramnarrator.ui.screens.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.yedidyatob.telegramnarrator.R
import io.github.yedidyatob.telegramnarrator.ui.text.bidiSafe
import io.github.yedidyatob.telegramnarrator.domain.model.Chat
import io.github.yedidyatob.telegramnarrator.ui.components.ChatAvatar

/**
 * One unread chat: selection checkbox (for Play selected), avatar, title, unread count and its own Play
 * button. Tapping the row opens the chat's preview sheet. The chat being read is outlined.
 */
@Composable
fun ChatListItem(
    chat: Chat,
    isSelected: Boolean,
    isPlaying: Boolean,
    onToggleSelection: () -> Unit,
    onClick: () -> Unit,
    onPlayClick: () -> Unit,
    loadPhoto: suspend (Int) -> String?
) {
    val selectDescription = stringResource(R.string.home_select_chat, chat.title)
    val unreadDescription = LocalContext.current.resources
        .getQuantityString(R.plurals.home_unread_messages, chat.unreadCount, chat.unreadCount)

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        border = if (isPlaying) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        colors = CardDefaults.cardColors(
            containerColor = when {
                isPlaying -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                isSelected -> MaterialTheme.colorScheme.surfaceContainer
                else -> MaterialTheme.colorScheme.surfaceContainerLow
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggleSelection() },
                modifier = Modifier.semantics { contentDescription = selectDescription }
            )
            ChatAvatar(chatId = chat.id, title = chat.title, photoFileId = chat.photoFileId, loadFile = loadPhoto)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = bidiSafe(chat.title),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                // The unread count is in the badge; the second line only appears for the chat being read
                if (isPlaying) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.GraphicEq,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 4.dp).size(16.dp)
                        )
                        Text(
                            text = stringResource(R.string.home_chat_playing),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1
                        )
                    }
                }
            }
            Badge(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.semantics { contentDescription = unreadDescription }
            ) {
                Text(text = chat.unreadCount.toString())
            }
            IconButton(onClick = onPlayClick) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = stringResource(R.string.home_play_chat, chat.title),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
