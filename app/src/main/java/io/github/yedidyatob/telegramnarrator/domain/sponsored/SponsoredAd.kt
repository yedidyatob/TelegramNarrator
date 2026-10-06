package io.github.yedidyatob.telegramnarrator.domain.sponsored

/**
 * An official Telegram sponsored message (Telegram API Terms of Service 3.3,
 * https://core.telegram.org/api/sponsored-messages), shown after a channel's (or bot chat's) messages.
 *
 * Mapped from TDLib's `sponsoredMessage`. [fetchId] identifies the `getChatSponsoredMessages` call that
 * returned it: a view is reported at most once per ad per fetch ([SponsoredViewTracker]).
 */
data class SponsoredAd(
    val chatId: Long,
    /** Unique in the chat among ordinary and sponsored messages; used for view / click / report. */
    val messageId: Long,
    val fetchId: Long = 0L,
    val chatKind: SponsoredChatKind = SponsoredChatKind.CHANNEL,
    /** Label as "Recommended" instead of "Sponsored" / "Ad". */
    val isRecommended: Boolean = false,
    val canBeReported: Boolean = false,
    val title: String,
    val text: String,
    val textSpans: List<SponsoredTextSpan> = emptyList(),
    val buttonText: String = "",
    /** Opened by the button (and the title, sponsor photo and photo / GIF media). */
    val url: String = "",
    /** Sponsor details for the "Sponsor info" menu item (each may be blank). */
    val sponsorInfo: String = "",
    val additionalInfo: String = "",
    /** TDLib accent color identifier for the label, title and button. */
    val accentColorId: Int = -1,
    val sponsorPhotoFileId: Int? = null,
    val media: SponsoredMedia? = null
) {
    /** Whether the "Sponsor info" menu item is shown. */
    val hasSponsorInfo: Boolean get() = sponsorInfo.isNotBlank() || additionalInfo.isNotBlank()

    /** Text of the "Sponsor info" dialog: the sponsor info and the additional info, one per paragraph. */
    val sponsorInfoText: String
        get() = listOf(sponsorInfo, additionalInfo).filter { it.isNotBlank() }.joinToString("\n\n")

    /** Key of this ad within its fetch (for view-once tracking). */
    val viewKey: String get() = "$chatId:$messageId:$fetchId"
}

/** The chat types TDLib returns sponsored messages for. */
enum class SponsoredChatKind { CHANNEL, BOT }

/**
 * Media of a sponsored message. [imageFileId] is a TDLib file that can be shown as a still image (the photo,
 * or a JPEG thumbnail / cover of a GIF or video); null when there is nothing displayable.
 */
data class SponsoredMedia(
    val kind: Kind,
    val imageFileId: Int?,
    val width: Int = 0,
    val height: Int = 0
) {
    enum class Kind { PHOTO, ANIMATION, VIDEO }

    /**
     * Photos and GIFs (no sound) open the ad link when tapped. A video with sound must open a fullscreen
     * player first, which this app does not have, so a video thumbnail is not clickable.
     */
    val opensLinkOnClick: Boolean get() = kind != Kind.VIDEO
}

/** A formatting entity of the ad text (UTF-16 offsets, like TDLib). */
data class SponsoredTextSpan(val start: Int, val end: Int, val style: Style) {
    enum class Style { BOLD, ITALIC, UNDERLINE, STRIKETHROUGH, MONOSPACE }
}

/** Option offered by `reportChatSponsoredMessage` (TDLib `reportOption`). */
class SponsoredReportOption(val id: ByteArray, val text: String) {
    override fun equals(other: Any?): Boolean =
        other is SponsoredReportOption && id.contentEquals(other.id) && text == other.text

    override fun hashCode(): Int = 31 * id.contentHashCode() + text.hashCode()
    override fun toString(): String = "SponsoredReportOption($text)"
}

/** Result of `reportChatSponsoredMessage` (TDLib `ReportSponsoredResult`). */
sealed interface SponsoredReportResult {
    /** Reported to Telegram's moderators. */
    data object Reported : SponsoredReportResult
    /** Sponsored messages were hidden for the user in all chats. */
    data object AdsHidden : SponsoredReportResult
    /** The user asked to hide ads, but that needs Telegram Premium. */
    data object PremiumRequired : SponsoredReportResult
    /** The ad is too old or was not found. */
    data object Failed : SponsoredReportResult
    /** The user must pick one of [options]; [title] is the title of the option list. */
    data class OptionRequired(val title: String, val options: List<SponsoredReportOption>) : SponsoredReportResult
}
