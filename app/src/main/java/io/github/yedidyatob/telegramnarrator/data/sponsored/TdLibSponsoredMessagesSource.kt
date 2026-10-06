package io.github.yedidyatob.telegramnarrator.data.sponsored

import android.util.Log
import io.github.yedidyatob.telegramnarrator.data.tdlib.TdLibClient
import io.github.yedidyatob.telegramnarrator.domain.sponsored.SponsoredAd
import io.github.yedidyatob.telegramnarrator.domain.sponsored.SponsoredChatKind
import io.github.yedidyatob.telegramnarrator.domain.sponsored.SponsoredMedia
import io.github.yedidyatob.telegramnarrator.domain.sponsored.SponsoredMessagesSource
import io.github.yedidyatob.telegramnarrator.domain.sponsored.SponsoredReportOption
import io.github.yedidyatob.telegramnarrator.domain.sponsored.SponsoredReportResult
import io.github.yedidyatob.telegramnarrator.domain.sponsored.SponsoredTextSpan
import kotlinx.coroutines.withTimeoutOrNull
import org.drinkless.tdlib.TdApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TDLib 1.8.56 sponsored-message API: `getChatSponsoredMessages`, views through `viewMessages` (there is no
 * `viewSponsoredMessage` in this version), `clickChatSponsoredMessage` and `reportChatSponsoredMessage`.
 */
@Singleton
class TdLibSponsoredMessagesSource @Inject constructor(
    private val client: TdLibClient
) : SponsoredMessagesSource {

    private companion object {
        const val TAG = "SponsoredMessages"
        const val DOWNLOAD_TIMEOUT_MS = 30_000L
        /** Largest photo side to download for the card. */
        const val MAX_PHOTO_SIDE = 1280
        /** Sponsor photo: the smallest size at least this big. */
        const val SPONSOR_PHOTO_SIDE = 160
    }

    override suspend fun chatKind(chatId: Long): SponsoredChatKind? {
        val chat = client.send(TdApi.GetChat(chatId))
        return when (val type = chat.type) {
            is TdApi.ChatTypeSupergroup -> if (type.isChannel) SponsoredChatKind.CHANNEL else null
            is TdApi.ChatTypePrivate -> {
                val user = client.send(TdApi.GetUser(type.userId))
                if (user.type is TdApi.UserTypeBot) SponsoredChatKind.BOT else null
            }
            else -> null
        }
    }

    override suspend fun fetch(chatId: Long, kind: SponsoredChatKind): List<SponsoredAd> {
        val result = client.send(TdApi.GetChatSponsoredMessages(chatId))
        Log.d(TAG, "Chat $chatId: ${result.messages.size} sponsored message(s), messagesBetween=${result.messagesBetween}")
        return result.messages.map { map(chatId, kind, it) }
    }

    private fun map(chatId: Long, kind: SponsoredChatKind, message: TdApi.SponsoredMessage): SponsoredAd {
        val (text, media) = when (val content = message.content) {
            is TdApi.MessageText -> content.text to null
            is TdApi.MessagePhoto -> content.caption to SponsoredMedia(
                SponsoredMedia.Kind.PHOTO,
                bestPhotoSize(content.photo)?.photo?.id,
                bestPhotoSize(content.photo)?.width ?: 0,
                bestPhotoSize(content.photo)?.height ?: 0
            )
            is TdApi.MessageAnimation -> content.caption to SponsoredMedia(
                SponsoredMedia.Kind.ANIMATION,
                jpegThumbnail(content.animation.thumbnail),
                content.animation.width,
                content.animation.height
            )
            is TdApi.MessageVideo -> content.caption to SponsoredMedia(
                SponsoredMedia.Kind.VIDEO,
                content.cover?.let { bestPhotoSize(it)?.photo?.id } ?: jpegThumbnail(content.video.thumbnail),
                content.video.width,
                content.video.height
            )
            else -> null to null
        }
        return SponsoredAd(
            chatId = chatId,
            messageId = message.messageId,
            chatKind = kind,
            isRecommended = message.isRecommended,
            canBeReported = message.canBeReported,
            title = message.title.orEmpty(),
            text = text?.text.orEmpty(),
            textSpans = text?.entities.orEmpty().mapNotNull { span(it) },
            buttonText = message.buttonText.orEmpty(),
            url = message.sponsor?.url.orEmpty(),
            sponsorInfo = message.sponsor?.info.orEmpty(),
            additionalInfo = message.additionalInfo.orEmpty(),
            accentColorId = message.accentColorId,
            sponsorPhotoFileId = message.sponsor?.photo?.let { sponsorPhotoSize(it)?.photo?.id },
            media = media
        )
    }

    private fun span(entity: TdApi.TextEntity): SponsoredTextSpan? {
        val style = when (entity.type) {
            is TdApi.TextEntityTypeBold -> SponsoredTextSpan.Style.BOLD
            is TdApi.TextEntityTypeItalic -> SponsoredTextSpan.Style.ITALIC
            is TdApi.TextEntityTypeUnderline -> SponsoredTextSpan.Style.UNDERLINE
            is TdApi.TextEntityTypeStrikethrough -> SponsoredTextSpan.Style.STRIKETHROUGH
            is TdApi.TextEntityTypeCode, is TdApi.TextEntityTypePre -> SponsoredTextSpan.Style.MONOSPACE
            else -> return null
        }
        return SponsoredTextSpan(entity.offset, entity.offset + entity.length, style)
    }

    private fun bestPhotoSize(photo: TdApi.Photo?): TdApi.PhotoSize? {
        val sizes = photo?.sizes?.takeIf { it.isNotEmpty() } ?: return null
        return sizes.filter { maxOf(it.width, it.height) <= MAX_PHOTO_SIDE }.maxByOrNull { it.width * it.height }
            ?: sizes.minByOrNull { it.width * it.height }
    }

    private fun sponsorPhotoSize(photo: TdApi.Photo): TdApi.PhotoSize? {
        val sizes = photo.sizes?.takeIf { it.isNotEmpty() } ?: return null
        return sizes.filter { minOf(it.width, it.height) >= SPONSOR_PHOTO_SIDE }.minByOrNull { it.width * it.height }
            ?: sizes.maxByOrNull { it.width * it.height }
    }

    private fun jpegThumbnail(thumbnail: TdApi.Thumbnail?): Int? =
        thumbnail?.takeIf { it.format is TdApi.ThumbnailFormatJpeg }?.file?.id

    override suspend fun view(chatId: Long, messageId: Long) {
        // Like markChatAsRead: the chat counts as open, then the sponsored message id is "viewed".
        // forceRead=false: this must not mark any ordinary message as read.
        try {
            client.send(TdApi.OpenChat(chatId))
        } catch (e: Exception) {
            Log.w(TAG, "OpenChat failed for $chatId (continuing with ViewMessages)", e)
        }
        client.send(TdApi.ViewMessages(chatId, longArrayOf(messageId), null, false))
        Log.d(TAG, "Reported view of sponsored message $messageId in chat $chatId")
    }

    override suspend fun click(chatId: Long, messageId: Long, isMediaClick: Boolean) {
        client.send(TdApi.ClickChatSponsoredMessage(chatId, messageId, isMediaClick, false))
    }

    override suspend fun report(chatId: Long, messageId: Long, optionId: ByteArray): SponsoredReportResult =
        when (val result = client.send(TdApi.ReportChatSponsoredMessage(chatId, messageId, optionId))) {
            is TdApi.ReportSponsoredResultOk -> SponsoredReportResult.Reported
            is TdApi.ReportSponsoredResultAdsHidden -> SponsoredReportResult.AdsHidden
            is TdApi.ReportSponsoredResultPremiumRequired -> SponsoredReportResult.PremiumRequired
            is TdApi.ReportSponsoredResultFailed -> SponsoredReportResult.Failed
            is TdApi.ReportSponsoredResultOptionRequired -> SponsoredReportResult.OptionRequired(
                result.title.orEmpty(),
                result.options.orEmpty().map { SponsoredReportOption(it.id ?: ByteArray(0), it.text.orEmpty()) }
            )
            else -> SponsoredReportResult.Failed
        }

    override suspend fun downloadFile(fileId: Int): String? = withTimeoutOrNull(DOWNLOAD_TIMEOUT_MS) {
        // synchronous=true: the reply comes once the file is fully downloaded
        val file = client.send(TdApi.DownloadFile(fileId, 1, 0, 0, true))
        file.local?.takeIf { it.isDownloadingCompleted }?.path
    }
}
