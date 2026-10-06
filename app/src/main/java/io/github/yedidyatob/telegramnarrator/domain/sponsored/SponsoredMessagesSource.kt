package io.github.yedidyatob.telegramnarrator.domain.sponsored

/**
 * Thin access to TDLib's sponsored-message API (implemented by `TdLibSponsoredMessagesSource`), so the caching
 * and view-once logic in [SponsoredMessagesRepository] is unit tested without TDLib.
 */
interface SponsoredMessagesSource {
    /** [SponsoredChatKind] of the chat, or null when TDLib never returns ads for it (groups, private chats). */
    suspend fun chatKind(chatId: Long): SponsoredChatKind?

    /** `getChatSponsoredMessages`, mapped (with [SponsoredAd.fetchId] = 0). */
    suspend fun fetch(chatId: Long, kind: SponsoredChatKind): List<SponsoredAd>

    /** `viewMessages` with the sponsored message id (this TDLib version has no `viewSponsoredMessage`). */
    suspend fun view(chatId: Long, messageId: Long)

    /** `clickChatSponsoredMessage`. */
    suspend fun click(chatId: Long, messageId: Long, isMediaClick: Boolean)

    /** `reportChatSponsoredMessage`; [optionId] is empty for the first request. */
    suspend fun report(chatId: Long, messageId: Long, optionId: ByteArray): SponsoredReportResult

    /** Downloads a TDLib file and returns its local path, or null. */
    suspend fun downloadFile(fileId: Int): String?
}
