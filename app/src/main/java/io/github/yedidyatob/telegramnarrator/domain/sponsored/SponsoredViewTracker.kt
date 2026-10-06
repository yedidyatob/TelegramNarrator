package io.github.yedidyatob.telegramnarrator.domain.sponsored

/**
 * Reports a sponsored message as viewed at most once per ad per fetch. Two triggers can fire for the same ad:
 * it was read aloud in full (even with the screen off), and its card's full text was visible on screen.
 */
class SponsoredViewTracker {
    private val reported = HashSet<String>()

    /** True the first time it is called for this ad of this fetch (then the caller reports the view). */
    @Synchronized
    fun markIfNew(ad: SponsoredAd): Boolean = reported.add(ad.viewKey)

    /** Undo [markIfNew] after the view request failed, so a later trigger can try again. */
    @Synchronized
    fun unmark(ad: SponsoredAd) {
        reported.remove(ad.viewKey)
    }

    @Synchronized
    fun wasReported(ad: SponsoredAd): Boolean = ad.viewKey in reported
}
