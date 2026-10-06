package io.github.yedidyatob.telegramnarrator.domain.sponsored

import io.github.yedidyatob.telegramnarrator.domain.sponsored.SponsoredReportFlow.Step
import io.github.yedidyatob.telegramnarrator.domain.sponsored.SponsoredReportFlow.Toast
import org.junit.Assert.assertEquals
import org.junit.Test

class SponsoredReportFlowTest {
    @Test
    fun `options are shown for the user to choose`() {
        val options = SponsoredReportResult.OptionRequired("Report", listOf(SponsoredReportOption(byteArrayOf(1), "Spam")))
        assertEquals(Step(chooseFrom = options), SponsoredReportFlow.next(options))
    }

    @Test
    fun `an empty option list is an error`() {
        assertEquals(
            Step(toast = Toast.ERROR),
            SponsoredReportFlow.next(SponsoredReportResult.OptionRequired("Report", emptyList()))
        )
    }

    @Test
    fun `results end the flow with a toast`() {
        assertEquals(Step(toast = Toast.REPORTED, hideAd = true), SponsoredReportFlow.next(SponsoredReportResult.Reported))
        assertEquals(
            Step(toast = Toast.ADS_HIDDEN, hideAd = true, hideAllAds = true),
            SponsoredReportFlow.next(SponsoredReportResult.AdsHidden)
        )
        assertEquals(Step(toast = Toast.PREMIUM_REQUIRED), SponsoredReportFlow.next(SponsoredReportResult.PremiumRequired))
        assertEquals(Step(toast = Toast.EXPIRED, hideAd = true), SponsoredReportFlow.next(SponsoredReportResult.Failed))
        assertEquals(Step(toast = Toast.ERROR), SponsoredReportFlow.next(null))
    }

    @Test
    fun `report options compare by content`() {
        assertEquals(SponsoredReportOption(byteArrayOf(1, 2), "A"), SponsoredReportOption(byteArrayOf(1, 2), "A"))
    }
}
