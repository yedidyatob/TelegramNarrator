package io.github.yedidyatob.telegramnarrator.ui.screens.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.yedidyatob.telegramnarrator.R
import io.github.yedidyatob.telegramnarrator.core.labelRes
import io.github.yedidyatob.telegramnarrator.domain.model.Chat
import io.github.yedidyatob.telegramnarrator.domain.model.Message
import io.github.yedidyatob.telegramnarrator.domain.sponsored.SponsoredAd
import io.github.yedidyatob.telegramnarrator.ui.components.ChatAvatar
import io.github.yedidyatob.telegramnarrator.ui.components.SponsoredCard
import io.github.yedidyatob.telegramnarrator.ui.text.ContentTextStyle
import io.github.yedidyatob.telegramnarrator.ui.text.ProvideContentDirection

/**
 * A chat's unread messages (newest first), with Play and Mark as read, and the chat's sponsored message at the
 * bottom. [messages] null = still loading.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatPreviewSheet(
    chat: Chat,
    messages: List<Message>?,
    ad: SponsoredAd?,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onMarkAsRead: () -> Unit,
    loadPhoto: suspend (Int) -> String?,
    onAdFullyVisible: (SponsoredAd) -> Unit,
    onAdLinkClicked: (SponsoredAd, Boolean) -> Unit,
    onAdReport: (SponsoredAd) -> Unit,
    loadAdFile: suspend (Int) -> String?
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChatAvatar(chatId = chat.id, title = chat.title, photoFileId = chat.photoFileId, loadFile = loadPhoto, size = 48.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        chat.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { heading() }
                    )
                    Text(
                        text = when {
                            messages == null -> stringResource(R.string.home_sheet_loading)
                            chat.unreadCount > messages.size -> stringResource(R.string.home_sheet_showing_partial, messages.size, chat.unreadCount)
                            else -> stringResource(R.string.home_sheet_showing_all, messages.size)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            val markDescription = stringResource(R.string.home_mark_chat_read_cd)
            Row {
                Button(onClick = onPlay, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.home_sheet_play))
                }
                Spacer(Modifier.width(12.dp))
                FilledTonalButton(
                    onClick = onMarkAsRead,
                    enabled = !messages.isNullOrEmpty(),
                    modifier = Modifier
                        .weight(1f)
                        .semantics { contentDescription = markDescription }
                ) {
                    Icon(Icons.Rounded.DoneAll, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.home_mark_chat_read))
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        when {
            messages == null -> Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            messages.isEmpty() && ad == null -> Text(
                stringResource(R.string.home_sheet_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(40.dp)
            )
            else -> LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                items(messages, key = { it.id }) { msg ->
                    SheetMessageCard(
                        senderName = msg.senderName ?: stringResource(R.string.playback_unknown_sender),
                        text = msg.text.ifBlank {
                            stringResource(msg.contentType.labelRes() ?: R.string.message_unsupported_content)
                        },
                        isOutgoing = msg.isOutgoing
                    )
                }
                // The channel's / bot's sponsored message, at the bottom of the sheet
                ad?.let {
                    item(key = "sponsored") {
                        SponsoredCard(
                            ad = it,
                            onFullyVisible = onAdFullyVisible,
                            onLinkClicked = onAdLinkClicked,
                            onReport = onAdReport,
                            loadFile = loadAdFile,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * One unread message in the chat's preview sheet. The card follows the message's direction (a Hebrew message
 * is right-aligned, sender name included, even on an English device); each paragraph aligns by its own text.
 */
@Composable
internal fun SheetMessageCard(senderName: String, text: String, isOutgoing: Boolean) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isOutgoing) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            }
        )
    ) {
        ProvideContentDirection(text) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                Text(
                    text = senderName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium.merge(ContentTextStyle),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
