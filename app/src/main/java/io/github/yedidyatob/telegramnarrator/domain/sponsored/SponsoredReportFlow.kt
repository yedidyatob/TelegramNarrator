package io.github.yedidyatob.telegramnarrator.domain.sponsored

/**
 * The "Report" flow of a sponsored message (https://core.telegram.org/api/sponsored-messages#reporting-sponsored-messages):
 * report with an empty option; if Telegram answers with options, the user picks one and the report is repeated
 * with it (possibly several rounds); then a result toast. Pure, unit tested.
 */
object SponsoredReportFlow {
    enum class Toast { REPORTED, ADS_HIDDEN, PREMIUM_REQUIRED, EXPIRED, ERROR }

    /**
     * What the UI does after a report request.
     *
     * @param chooseFrom show this option list (its title + options) and report again with the chosen option
     * @param toast result message to show
     * @param hideAd remove this ad from the screen (reported, or ads hidden)
     * @param hideAllAds sponsored messages were hidden for the user in all chats
     */
    data class Step(
        val chooseFrom: SponsoredReportResult.OptionRequired? = null,
        val toast: Toast? = null,
        val hideAd: Boolean = false,
        val hideAllAds: Boolean = false
    )

    /** The next step for [result]; null means the request failed (network / TDLib error). */
    fun next(result: SponsoredReportResult?): Step = when (result) {
        null -> Step(toast = Toast.ERROR)
        is SponsoredReportResult.OptionRequired ->
            if (result.options.isEmpty()) Step(toast = Toast.ERROR) else Step(chooseFrom = result)
        SponsoredReportResult.Reported -> Step(toast = Toast.REPORTED, hideAd = true)
        SponsoredReportResult.AdsHidden -> Step(toast = Toast.ADS_HIDDEN, hideAd = true, hideAllAds = true)
        SponsoredReportResult.PremiumRequired -> Step(toast = Toast.PREMIUM_REQUIRED)
        SponsoredReportResult.Failed -> Step(toast = Toast.EXPIRED, hideAd = true)
    }
}
