package io.github.yedidyatob.telegramnarrator.domain.sponsored

/** In-memory [SponsoredMessagesSource] that records every call. */
class FakeSponsoredSource : SponsoredMessagesSource {
    val kinds = HashMap<Long, SponsoredChatKind?>()
    val ads = HashMap<Long, List<SponsoredAd>>()
    val reportResults = ArrayDeque<SponsoredReportResult>()

    var kindCalls = 0
    var fetchCalls = 0
    val views = ArrayList<Pair<Long, Long>>()
    val clicks = ArrayList<Triple<Long, Long, Boolean>>()
    val reports = ArrayList<Triple<Long, Long, ByteArray>>()
    var failFetch = false
    var failView = false
    var failReport = false

    override suspend fun chatKind(chatId: Long): SponsoredChatKind? {
        kindCalls++
        return kinds[chatId]
    }

    override suspend fun fetch(chatId: Long, kind: SponsoredChatKind): List<SponsoredAd> {
        fetchCalls++
        if (failFetch) throw RuntimeException("network")
        return ads[chatId].orEmpty()
    }

    override suspend fun view(chatId: Long, messageId: Long) {
        if (failView) throw RuntimeException("network")
        views += chatId to messageId
    }

    override suspend fun click(chatId: Long, messageId: Long, isMediaClick: Boolean) {
        clicks += Triple(chatId, messageId, isMediaClick)
    }

    override suspend fun report(chatId: Long, messageId: Long, optionId: ByteArray): SponsoredReportResult {
        reports += Triple(chatId, messageId, optionId)
        if (failReport) throw RuntimeException("network")
        return reportResults.removeFirst()
    }

    override suspend fun downloadFile(fileId: Int): String? = "/files/$fileId.jpg"
}

fun ad(chatId: Long = 1L, messageId: Long = 100L, title: String = "Title", text: String = "Text") =
    SponsoredAd(chatId = chatId, messageId = messageId, title = title, text = text, canBeReported = true)
