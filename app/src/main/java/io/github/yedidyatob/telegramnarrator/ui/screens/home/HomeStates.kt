package io.github.yedidyatob.telegramnarrator.ui.screens.home

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.MarkChatRead
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.yedidyatob.telegramnarrator.R
import io.github.yedidyatob.telegramnarrator.domain.home.ConnectionBanner

/** Placeholder rows shaped like chat rows, pulsing while the chat list loads. */
@Composable
fun HomeLoadingSkeleton(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.home_loading_chats)
    val transition = rememberInfiniteTransition(label = "skeleton")
    val pulse by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(850), RepeatMode.Reverse),
        label = "pulse"
    )
    val shade = MaterialTheme.colorScheme.surfaceContainerHighest
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .clearAndSetSemantics { contentDescription = description },
        userScrollEnabled = false
    ) {
        items(count = 7) { index ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .alpha(pulse)
            ) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(shade))
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    // Varying widths look less mechanical
                    Box(Modifier.fillMaxWidth(listOf(0.62f, 0.48f, 0.7f, 0.55f)[index % 4]).height(14.dp).clip(RoundedCornerShape(7.dp)).background(shade))
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth(0.32f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(shade))
                }
                Spacer(Modifier.width(16.dp))
                Box(Modifier.size(24.dp).clip(CircleShape).background(shade))
            }
        }
    }
}

/** No unread chats. */
@Composable
fun HomeEmptyState(onRefresh: () -> Unit, modifier: Modifier = Modifier) {
    HomeStateMessage(
        icon = Icons.Outlined.MarkChatRead,
        title = stringResource(R.string.home_empty_title),
        body = stringResource(R.string.home_empty_subtitle),
        actionLabel = stringResource(R.string.home_refresh),
        onAction = onRefresh,
        primaryAction = false,
        modifier = modifier
    )
}

/** Loading the chat list failed (TDLib error or timeout). */
@Composable
fun HomeErrorState(detail: String?, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    HomeStateMessage(
        icon = Icons.Outlined.ErrorOutline,
        title = stringResource(R.string.home_error_title),
        body = stringResource(R.string.home_error_body),
        detail = detail?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.home_error_detail, it) },
        actionLabel = stringResource(R.string.home_retry),
        onAction = onRetry,
        primaryAction = true,
        iconTint = MaterialTheme.colorScheme.error,
        modifier = modifier
    )
}

/** No network and no chats to show yet; loads by itself when the network is back. */
@Composable
fun HomeOfflineState(modifier: Modifier = Modifier) {
    HomeStateMessage(
        icon = Icons.Outlined.CloudOff,
        title = stringResource(R.string.home_offline_title),
        body = stringResource(R.string.home_offline_body),
        modifier = modifier,
        showProgress = true
    )
}

/**
 * Centered icon, headline, explanation and an optional action. Scrollable (a lazy list) so pull-to-refresh
 * works on it.
 */
@Composable
private fun HomeStateMessage(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    primaryAction: Boolean = false,
    showProgress: Boolean = false,
    iconTint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary
) {
    LazyColumn(modifier = modifier.fillMaxSize()) {
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillParentMaxSize()
                    .padding(horizontal = 32.dp, vertical = 24.dp)
                    .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
            ) {
                Surface(
                    shape = CircleShape,
                    color = iconTint.copy(alpha = 0.12f),
                    modifier = Modifier.size(96.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(44.dp))
                    }
                }
                Spacer(Modifier.height(24.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() }
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 360.dp)
                )
                if (detail != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(max = 360.dp)
                    )
                }
                if (showProgress) {
                    Spacer(Modifier.height(24.dp))
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                }
                if (actionLabel != null && onAction != null) {
                    Spacer(Modifier.height(24.dp))
                    if (primaryAction) {
                        Button(onClick = onAction) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(actionLabel)
                        }
                    } else {
                        OutlinedButton(onClick = onAction) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(actionLabel)
                        }
                    }
                }
            }
        }
    }
}

/** "Waiting for network…" / "Connecting…" strip under the top bar. */
@Composable
fun ConnectionBannerBar(banner: ConnectionBanner, modifier: Modifier = Modifier) {
    val offline = banner == ConnectionBanner.OFFLINE
    val container = if (offline) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
    val content = if (offline) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
    Surface(color = container, contentColor = content, modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
        ) {
            if (offline) {
                Icon(Icons.Rounded.WifiOff, contentDescription = null, modifier = Modifier.size(18.dp))
            } else {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = content)
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(if (offline) R.string.home_banner_offline else R.string.home_banner_connecting),
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

