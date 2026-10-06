package io.github.yedidyatob.telegramnarrator.domain.sponsored

/**
 * Per-chat cache of `getChatSponsoredMessages` results. Telegram: "Each time the user opens a channel or a chat
 * with a bot, messages.getSponsoredMessages must be called ... The result must be cached for 5 minutes."
 *
 * Every [put] is a new fetch and gets a new fetch id, which is stamped on its ads ([SponsoredAd.fetchId]).
 * Not thread safe; [SponsoredMessagesRepository] guards it.
 */
class SponsoredAdCache(private val ttlMs: Long = TTL_MS) {
    companion object {
        const val TTL_MS = 5 * 60 * 1000L
    }

    private class Entry(val ads: List<SponsoredAd>, val fetchedAtMs: Long)

    private val entries = HashMap<Long, Entry>()
    private var lastFetchId = 0L

    /** The cached ads of [chatId] if fetched less than [ttlMs] ago, else null (expired entries are dropped). */
    fun get(chatId: Long, nowMs: Long): List<SponsoredAd>? {
        val entry = entries[chatId] ?: return null
        if (nowMs - entry.fetchedAtMs >= ttlMs || nowMs < entry.fetchedAtMs) {
            entries.remove(chatId)
            return null
        }
        return entry.ads
    }

    /** Stores a fresh fetch of [chatId]; returns the ads stamped with their new fetch id. */
    fun put(chatId: Long, ads: List<SponsoredAd>, nowMs: Long): List<SponsoredAd> {
        val fetchId = ++lastFetchId
        val stamped = ads.map { it.copy(chatId = chatId, fetchId = fetchId) }
        entries[chatId] = Entry(stamped, nowMs)
        return stamped
    }

    /** Removes one ad (reported) from the cached list of its chat, keeping the fetch time. */
    fun remove(ad: SponsoredAd) {
        val entry = entries[ad.chatId] ?: return
        entries[ad.chatId] = Entry(entry.ads.filterNot { it.messageId == ad.messageId }, entry.fetchedAtMs)
    }

    fun clear() = entries.clear()
}
