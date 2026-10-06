package io.github.yedidyatob.telegramnarrator.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.yedidyatob.telegramnarrator.domain.sponsored.SponsoredAd
import io.github.yedidyatob.telegramnarrator.domain.sponsored.SponsoredChatKind
import io.github.yedidyatob.telegramnarrator.domain.sponsored.SponsoredTextSpan
import io.github.yedidyatob.telegramnarrator.ui.theme.TelegramNarratorTheme

internal val previewChannelAd = SponsoredAd(
    chatId = 1L,
    messageId = 100L,
    title = "Daily Tech Digest",
    text = "The 5 most important tech stories of the day, in 3 minutes. Join 200,000 readers.",
    textSpans = listOf(SponsoredTextSpan(4, 25, SponsoredTextSpan.Style.BOLD)),
    buttonText = "VIEW CHANNEL",
    url = "https://t.me/SecretAdTestChannel",
    sponsorInfo = "Advertiser: Example Media Ltd.",
    canBeReported = true,
    accentColorId = 5
)

internal val previewHebrewBotAd = SponsoredAd(
    chatId = 2L,
    messageId = 200L,
    chatKind = SponsoredChatKind.BOT,
    title = "בוט מזג האוויר",
    text = "תחזית מדויקת לכל עיר בישראל, ישירות בטלגרם.",
    buttonText = "פתיחת הבוט",
    url = "https://example.com/weather",
    accentColorId = 3,
    canBeReported = false
)

@Composable
private fun PreviewFrame(darkTheme: Boolean = false, content: @Composable () -> Unit) {
    TelegramNarratorTheme(darkTheme = darkTheme, dynamicColor = false) {
        Surface { Column(Modifier.padding(12.dp)) { content() } }
    }
}

@Composable
private fun PreviewCard(ad: SponsoredAd) {
    SponsoredCard(ad = ad, onFullyVisible = {}, onLinkClicked = { _, _ -> }, onReport = {}, loadFile = { null })
}

@Preview(name = "Sponsored (channel)", showBackground = true, widthDp = 380)
@Composable
private fun SponsoredChannelPreview() = PreviewFrame { PreviewCard(previewChannelAd) }

@Preview(name = "Recommended", showBackground = true, widthDp = 380)
@Composable
private fun SponsoredRecommendedPreview() = PreviewFrame {
    PreviewCard(previewChannelAd.copy(isRecommended = true, accentColorId = 2, buttonText = "OPEN"))
}

@Preview(name = "Ad (bot chat, Hebrew)", showBackground = true, widthDp = 380, locale = "he")
@Composable
private fun SponsoredBotHebrewPreview() = PreviewFrame { PreviewCard(previewHebrewBotAd) }

@Preview(name = "Sponsored (dark)", showBackground = true, widthDp = 380, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SponsoredDarkPreview() = PreviewFrame(darkTheme = true) { PreviewCard(previewChannelAd) }
