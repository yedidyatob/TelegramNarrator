package io.github.yedidyatob.telegramnarrator.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import io.github.yedidyatob.telegramnarrator.R
import io.github.yedidyatob.telegramnarrator.domain.sponsored.SponsoredAd
import io.github.yedidyatob.telegramnarrator.domain.sponsored.SponsoredChatKind
import io.github.yedidyatob.telegramnarrator.domain.sponsored.SponsoredLinkPolicy
import io.github.yedidyatob.telegramnarrator.domain.sponsored.SponsoredReportOption
import io.github.yedidyatob.telegramnarrator.domain.sponsored.SponsoredTextSpan
import io.github.yedidyatob.telegramnarrator.ui.text.ContentTextStyle
import io.github.yedidyatob.telegramnarrator.ui.text.ProvideContentDirection

/**
 * Card of an official Telegram sponsored message (Telegram API ToS 3.3, https://core.telegram.org/api/sponsored-messages):
 * "Sponsored" / "Recommended" (or "Ad" in bot chats) label in the ad's accent color, sponsor photo, title, text,
 * media (only once downloaded) and the action button. The ⋮ menu has Sponsor info (when present), About these
 * ads and Report (when the ad can be reported).
 *
 * [onFullyVisible] runs when the label, title and text are entirely on screen while the app is in the
 * foreground (the button and media do not count). [onLinkClicked] runs before the link is opened (button,
 * title, sponsor photo, or photo / GIF media with isMediaClick = true).
 */
@Composable
fun SponsoredCard(
    ad: SponsoredAd,
    onFullyVisible: (SponsoredAd) -> Unit,
    onLinkClicked: (SponsoredAd, isMediaClick: Boolean) -> Unit,
    onReport: (SponsoredAd) -> Unit,
    loadFile: suspend (Int) -> String?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val accent = sponsoredAccentColor(ad.accentColorId, MaterialTheme.colorScheme.primary)
    var menuOpen by remember { mutableStateOf(false) }
    var showSponsorInfo by rememberSaveable(ad.viewKey) { mutableStateOf(false) }
    var confirmMediaClick by rememberSaveable(ad.viewKey) { mutableStateOf<Boolean?>(null) }

    // Full text on screen while resumed -> one view report (the repository dedupes per ad per fetch)
    var textFullyVisible by remember(ad.viewKey) { mutableStateOf(false) }
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val resumed = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    LaunchedEffect(ad.viewKey, textFullyVisible, resumed) {
        if (textFullyVisible && resumed) onFullyVisible(ad)
    }

    val openLink: (Boolean) -> Unit = { isMediaClick ->
        if (ad.url.isNotBlank()) {
            if (SponsoredLinkPolicy.requiresConfirmation(ad.url)) {
                confirmMediaClick = isMediaClick
            } else {
                onLinkClicked(ad, isMediaClick)
                openSponsoredUrl(context, ad.url) { uriHandler.openUri(it) }
            }
        }
    }

    val sponsorPhoto = rememberTdImage(ad.sponsorPhotoFileId, loadFile)
    val mediaImage = rememberTdImage(ad.media?.imageFileId, loadFile)

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.10f).compositeOverSurface())
    ) {
        Column(Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                // Label, sponsor photo, title and text start on the ad's side (right for a Hebrew ad even on an
                // English device); the ⋮ menu stays at the UI's end like every other overflow menu.
                ProvideContentDirection(ad.title, ad.text) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .onGloballyPositioned { textFullyVisible = it.isFullyVisible() }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (sponsorPhoto != null) {
                                Image(
                                    bitmap = sponsorPhoto,
                                    contentDescription = stringResource(R.string.sponsored_sponsor_photo),
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .clickable { openLink(false) }
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Column {
                                Text(
                                    text = stringResource(sponsoredLabelRes(ad)),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = accent
                                )
                                if (ad.title.isNotBlank()) {
                                    Text(
                                        text = ad.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = accent,
                                        modifier = Modifier.clickable { openLink(false) }
                                    )
                                }
                            }
                        }
                        if (ad.text.isNotBlank()) {
                            Spacer(Modifier.padding(top = 4.dp))
                            Text(
                                text = sponsoredText(ad),
                                style = MaterialTheme.typography.bodyMedium.merge(ContentTextStyle),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
                Column {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.sponsored_menu))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        if (ad.hasSponsorInfo) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.sponsored_menu_info)) },
                                onClick = { menuOpen = false; showSponsorInfo = true }
                            )
                        }
                        val aboutUrl = stringResource(R.string.sponsored_about_url)
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.sponsored_menu_about)) },
                            onClick = {
                                menuOpen = false
                                openUriSafely(context) { uriHandler.openUri(aboutUrl) }
                            }
                        )
                        if (ad.canBeReported) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.sponsored_menu_report)) },
                                onClick = { menuOpen = false; onReport(ad) }
                            )
                        }
                    }
                }
            }
            val media = ad.media
            if (media != null && mediaImage != null) {
                Spacer(Modifier.padding(top = 8.dp))
                Image(
                    bitmap = mediaImage,
                    contentDescription = stringResource(R.string.sponsored_media),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .fillMaxWidth()
                        .heightIn(max = 180.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .then(if (media.opensLinkOnClick) Modifier.clickable { openLink(true) } else Modifier)
                )
            }
            if (ad.buttonText.isNotBlank() && ad.url.isNotBlank()) {
                Spacer(Modifier.padding(top = 8.dp))
                FilledTonalButton(
                    onClick = { openLink(false) },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = accent.copy(alpha = 0.18f).compositeOverSurface(),
                        contentColor = accent
                    ),
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .fillMaxWidth()
                ) {
                    Text(ad.buttonText)
                }
            }
        }
    }

    if (showSponsorInfo) {
        AlertDialog(
            onDismissRequest = { showSponsorInfo = false },
            title = { Text(stringResource(R.string.sponsored_menu_info)) },
            text = {
                Text(
                    ad.sponsorInfoText,
                    style = LocalTextStyle.current.merge(ContentTextStyle),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = { showSponsorInfo = false }) { Text(stringResource(R.string.sponsored_close)) }
            }
        )
    }

    val pendingMediaClick = confirmMediaClick
    if (pendingMediaClick != null) {
        AlertDialog(
            onDismissRequest = { confirmMediaClick = null },
            title = { Text(stringResource(R.string.sponsored_open_link_title)) },
            text = { Text(stringResource(R.string.sponsored_open_link_body, ad.url)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmMediaClick = null
                    onLinkClicked(ad, pendingMediaClick)
                    openSponsoredUrl(context, ad.url) { uriHandler.openUri(it) }
                }) { Text(stringResource(R.string.sponsored_open_link)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmMediaClick = null }) { Text(stringResource(R.string.sponsored_cancel)) }
            }
        )
    }
}

/** Option list of the report flow ("Report ad" → Telegram's localized options; its title as the dialog title). */
@Composable
fun SponsoredReportDialog(
    title: String,
    options: List<SponsoredReportOption>,
    onChoose: (SponsoredReportOption) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title.ifBlank { stringResource(R.string.sponsored_report_title) }) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(options) { option ->
                    TextButton(onClick = { onChoose(option) }, modifier = Modifier.fillMaxWidth()) {
                        // Telegram localizes the options (they may be Hebrew on an English UI)
                        Text(option.text, style = LocalTextStyle.current.merge(ContentTextStyle), modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.sponsored_cancel)) } }
    )
}

private fun sponsoredLabelRes(ad: SponsoredAd): Int = when {
    ad.isRecommended -> R.string.sponsored_label_recommended
    ad.chatKind == SponsoredChatKind.BOT -> R.string.sponsored_label_ad
    else -> R.string.sponsored_label
}

/** Ad text with its bold / italic / underline / strikethrough / code entities. */
private fun sponsoredText(ad: SponsoredAd): AnnotatedString = buildAnnotatedString {
    append(ad.text)
    ad.textSpans.forEach { span ->
        val start = span.start.coerceIn(0, ad.text.length)
        val end = span.end.coerceIn(start, ad.text.length)
        if (start == end) return@forEach
        val style = when (span.style) {
            SponsoredTextSpan.Style.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
            SponsoredTextSpan.Style.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
            SponsoredTextSpan.Style.UNDERLINE -> SpanStyle(textDecoration = TextDecoration.Underline)
            SponsoredTextSpan.Style.STRIKETHROUGH -> SpanStyle(textDecoration = TextDecoration.LineThrough)
            SponsoredTextSpan.Style.MONOSPACE -> SpanStyle(fontFamily = FontFamily.Monospace)
        }
        addStyle(style, start, end)
    }
}

/** True when the whole layout is inside the window (not clipped by a parent, a scroll container or the screen). */
private fun LayoutCoordinates.isFullyVisible(): Boolean {
    if (!isAttached || size.width == 0 || size.height == 0) return false
    val bounds = boundsInWindow()
    return bounds.width >= size.width - 1f && bounds.height >= size.height - 1f
}

/**
 * TDLib built-in accent colors 0-6 (red, orange, violet, green, cyan, blue, pink). Other ids need the
 * `updateAccentColors` palette, which this app does not track, so they use [fallback].
 */
fun sponsoredAccentColor(accentColorId: Int, fallback: Color): Color = when (accentColorId) {
    0 -> Color(0xFFCC5049)
    1 -> Color(0xFFD67722)
    2 -> Color(0xFF955CDB)
    3 -> Color(0xFF40A920)
    4 -> Color(0xFF309EBA)
    5 -> Color(0xFF368AD1)
    6 -> Color(0xFFC7508B)
    else -> fallback
}

@Composable
private fun Color.compositeOverSurface(): Color {
    val surface = MaterialTheme.colorScheme.surface
    val a = alpha
    return Color(
        red = red * a + surface.red * (1 - a),
        green = green * a + surface.green * (1 - a),
        blue = blue * a + surface.blue * (1 - a),
        alpha = 1f
    )
}

/** Telegram packages tried, in order, for t.me / tg: links (this app is not a full Telegram client). */
private val TELEGRAM_PACKAGES = listOf(
    "org.telegram.messenger",
    "org.telegram.messenger.web",
    "org.telegram.messenger.beta",
    "org.thunderdog.challegram",
    "org.telegram.plus"
)

/** Opens the ad URL: t.me / tg: links in the Telegram app when installed, everything else in the browser. */
private fun openSponsoredUrl(context: Context, rawUrl: String, openInBrowser: (String) -> Unit) {
    val url = SponsoredLinkPolicy.normalized(rawUrl)
    if (SponsoredLinkPolicy.opensInTelegramApp(url)) {
        for (pkg in TELEGRAM_PACKAGES) {
            try {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage(pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                return
            } catch (e: ActivityNotFoundException) {
                // Not installed: try the next one, then any app that handles the link
            }
        }
    }
    openUriSafely(context) { openInBrowser(url) }
}

private fun openUriSafely(context: Context, open: () -> Unit) {
    try {
        open()
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, R.string.link_open_failed, Toast.LENGTH_SHORT).show()
    } catch (e: IllegalArgumentException) {
        Toast.makeText(context, R.string.link_open_failed, Toast.LENGTH_SHORT).show()
    }
}
