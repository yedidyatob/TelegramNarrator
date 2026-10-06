package io.github.yedidyatob.telegramnarrator.ui.viewmodel

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.yedidyatob.telegramnarrator.R
import io.github.yedidyatob.telegramnarrator.domain.audio.PlaybackManager
import io.github.yedidyatob.telegramnarrator.domain.sponsored.SponsoredAd
import io.github.yedidyatob.telegramnarrator.domain.sponsored.SponsoredMessagesRepository
import io.github.yedidyatob.telegramnarrator.domain.sponsored.SponsoredReportFlow
import io.github.yedidyatob.telegramnarrator.domain.sponsored.SponsoredReportOption
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Official Telegram sponsored messages on Home (Telegram API ToS 3.3): the card of the chat that was just played,
 * the ad at the bottom of a channel's preview sheet, view / click reporting and the report flow.
 */
@HiltViewModel
class SponsoredViewModel @Inject constructor(
    private val repository: SponsoredMessagesRepository,
    private val playbackManager: PlaybackManager
) : ViewModel() {

    /** Option list of an ongoing report ([SponsoredReportFlow]). */
    data class ReportDialog(val ad: SponsoredAd, val title: String, val options: List<SponsoredReportOption>)

    /** Ad of the chat being / just played (card above the now-playing bar). */
    val playingAd: StateFlow<SponsoredAd?> = playbackManager.sponsoredAd

    private val _previewAd = MutableStateFlow<SponsoredAd?>(null)
    /** Ad shown at the bottom of the open chat preview sheet. */
    val previewAd: StateFlow<SponsoredAd?> = _previewAd.asStateFlow()
    private var previewJob: Job? = null

    private val _reportDialog = MutableStateFlow<ReportDialog?>(null)
    val reportDialog: StateFlow<ReportDialog?> = _reportDialog.asStateFlow()

    private val _toasts = MutableSharedFlow<Int>(extraBufferCapacity = 4)
    /** String resources to show as a toast (report results). */
    val toasts: SharedFlow<Int> = _toasts.asSharedFlow()

    /** The preview sheet of [chatId] opened (null: closed): opening a channel fetches its ads (5-minute cache). */
    fun loadPreviewAd(chatId: Long?) {
        previewJob?.cancel()
        _previewAd.value = null
        if (chatId == null) return
        previewJob = viewModelScope.launch { _previewAd.value = repository.adFor(chatId) }
    }

    /** The card's full text is visible on screen: report the view (at most once per ad per fetch). */
    fun onFullyVisible(ad: SponsoredAd) {
        viewModelScope.launch { repository.reportViewed(ad) }
    }

    /** The ad link is being opened (button, title, sponsor photo, or [isMediaClick] for photo / GIF media). */
    fun onLinkClicked(ad: SponsoredAd, isMediaClick: Boolean) {
        viewModelScope.launch { repository.reportClick(ad, isMediaClick) }
    }

    fun startReport(ad: SponsoredAd) = runReport(ad, null)

    fun chooseReportOption(option: SponsoredReportOption) {
        val dialog = _reportDialog.value ?: return
        _reportDialog.value = null
        runReport(dialog.ad, option)
    }

    fun dismissReport() {
        _reportDialog.value = null
    }

    private fun runReport(ad: SponsoredAd, option: SponsoredReportOption?) {
        viewModelScope.launch {
            val step = SponsoredReportFlow.next(repository.report(ad, option))
            step.chooseFrom?.let { _reportDialog.value = ReportDialog(ad, it.title, it.options) }
            step.toast?.let { _toasts.emit(toastRes(it)) }
            if (step.hideAllAds) {
                playbackManager.clearSponsoredAd()
                _previewAd.value = null
            } else if (step.hideAd) {
                playbackManager.clearSponsoredAd(onlyIf = ad)
                if (_previewAd.value?.messageId == ad.messageId) _previewAd.value = null
            }
        }
    }

    /** Local path of a TDLib file (sponsor photo / ad media), downloading it first. */
    suspend fun localFile(fileId: Int): String? = repository.localFile(fileId)

    companion object {
        @StringRes
        fun toastRes(toast: SponsoredReportFlow.Toast): Int = when (toast) {
            SponsoredReportFlow.Toast.REPORTED -> R.string.sponsored_report_done
            SponsoredReportFlow.Toast.ADS_HIDDEN -> R.string.sponsored_report_ads_hidden
            SponsoredReportFlow.Toast.PREMIUM_REQUIRED -> R.string.sponsored_report_premium
            SponsoredReportFlow.Toast.EXPIRED -> R.string.sponsored_report_expired
            SponsoredReportFlow.Toast.ERROR -> R.string.sponsored_report_error
        }
    }
}
