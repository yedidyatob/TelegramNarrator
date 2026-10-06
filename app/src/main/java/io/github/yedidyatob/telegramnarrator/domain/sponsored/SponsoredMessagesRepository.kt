package io.github.yedidyatob.telegramnarrator.domain.sponsored

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Official Telegram sponsored messages for channels and bot chats (Telegram API ToS 3.3): a 5-minute per-chat
 * cache ([SponsoredAdCache]), view reporting at most once per ad per fetch ([SponsoredViewTracker]) and the
 * click / report wrappers. Ads never go through the ordinary mark-as-read checkpoint.
 */
@Singleton
class SponsoredMessagesRepository internal constructor(
    private val source: SponsoredMessagesSource,
    private val nowMs: () -> Long
) {
    @Inject
    constructor(source: SponsoredMessagesSource) : this(source, { System.nanoTime() / 1_000_000L })

    private val cache = SponsoredAdCache()
    private val viewTracker = SponsoredViewTracker()
    private val mutex = Mutex()
    // Chat kind never changes: remember it (CHANNEL / BOT, or NONE for chats without ads)
    private val kinds = ConcurrentHashMap<Long, Any>()
    private object NoAds

    /**
     * The sponsored messages of [chatId] (cached for 5 minutes), oldest-placement first. Empty for chats TDLib has
     * no ads for, and on errors (errors are not cached, so the next call retries).
     */
    suspend fun adsFor(chatId: Long): List<SponsoredAd> = mutex.withLock {
        cache.get(chatId, nowMs())?.let { return@withLock it }
        val kind = kindOf(chatId) ?: return@withLock emptyList()
        val fetched = try {
            source.fetch(chatId, kind)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return@withLock emptyList()
        }
        cache.put(chatId, fetched.map { it.copy(chatKind = kind) }, nowMs())
    }

    /** The ad to show after the chat's messages (only one: the app plays a channel's unread posts as one block). */
    suspend fun adFor(chatId: Long): SponsoredAd? = adsFor(chatId).firstOrNull()

    private suspend fun kindOf(chatId: Long): SponsoredChatKind? {
        kinds[chatId]?.let { return it as? SponsoredChatKind }
        val kind = try {
            source.chatKind(chatId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return null // not cached: retry next time
        }
        kinds[chatId] = kind ?: NoAds
        return kind
    }

    /**
     * Reports [ad] as viewed (read aloud in full, or its full text visible on screen). At most once per ad per
     * fetch; returns true if this call sent the report.
     */
    suspend fun reportViewed(ad: SponsoredAd): Boolean {
        if (!viewTracker.markIfNew(ad)) return false
        return try {
            source.view(ad.chatId, ad.messageId)
            true
        } catch (e: CancellationException) {
            viewTracker.unmark(ad)
            throw e
        } catch (e: Exception) {
            viewTracker.unmark(ad)
            false
        }
    }

    fun wasViewReported(ad: SponsoredAd): Boolean = viewTracker.wasReported(ad)

    /** The user opened the ad link (button, title, sponsor photo, or [isMediaClick] for photo / GIF media). */
    suspend fun reportClick(ad: SponsoredAd, isMediaClick: Boolean = false) {
        try {
            source.click(ad.chatId, ad.messageId, isMediaClick)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Best effort: the link is opened anyway
        }
    }

    /**
     * One round of the report flow ([SponsoredReportFlow]); [option] is null for the first request. Null result on
     * errors. A reported / expired ad is dropped from the cache, and "ads hidden" clears the whole cache.
     */
    suspend fun report(ad: SponsoredAd, option: SponsoredReportOption? = null): SponsoredReportResult? {
        val result = try {
            source.report(ad.chatId, ad.messageId, option?.id ?: ByteArray(0))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return null
        }
        val step = SponsoredReportFlow.next(result)
        mutex.withLock {
            if (step.hideAllAds) cache.clear() else if (step.hideAd) cache.remove(ad)
        }
        return result
    }

    /** Local path of a downloaded TDLib file (sponsor photo, ad media), or null. */
    suspend fun localFile(fileId: Int): String? = try {
        source.downloadFile(fileId)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
}
