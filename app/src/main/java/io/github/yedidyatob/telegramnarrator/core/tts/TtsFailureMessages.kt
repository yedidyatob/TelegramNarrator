package io.github.yedidyatob.telegramnarrator.core.tts

import androidx.annotation.StringRes
import io.github.yedidyatob.telegramnarrator.R
import io.github.yedidyatob.telegramnarrator.domain.tts.FallbackReason
import io.github.yedidyatob.telegramnarrator.domain.tts.TtsFailures

/** The message for each [FallbackReason] (#63). */
object TtsFailureMessages {
    @StringRes
    fun messageRes(reason: FallbackReason): Int = when (reason) {
        FallbackReason.OPENAI_NO_KEY -> R.string.tts_fallback_openai_no_key
        FallbackReason.OPENAI_INVALID_KEY -> R.string.tts_fallback_openai_invalid_key
        FallbackReason.OPENAI_NO_CREDIT -> R.string.tts_fallback_openai_no_credit
        FallbackReason.OPENAI_RATE_LIMITED -> R.string.tts_fallback_openai_rate_limited
        FallbackReason.OPENAI_TOO_LONG -> R.string.tts_fallback_openai_too_long
        FallbackReason.OPENAI_BAD_REQUEST -> R.string.tts_fallback_openai_bad_request
        FallbackReason.OPENAI_SERVER_ERROR -> R.string.tts_fallback_openai_server
        FallbackReason.OPENAI_TIMEOUT -> R.string.tts_fallback_openai_timeout
        FallbackReason.OPENAI_FAILED -> R.string.tts_fallback_openai_failed
        FallbackReason.EDGE_THROTTLED -> R.string.tts_fallback_edge_throttled
        FallbackReason.EDGE_UNAVAILABLE -> R.string.tts_fallback_edge_unavailable
        FallbackReason.EDGE_TIMEOUT -> R.string.tts_fallback_edge_timeout
        FallbackReason.EDGE_FAILED -> R.string.tts_fallback_edge_failed
        FallbackReason.NO_NETWORK -> R.string.tts_fallback_no_network
        FallbackReason.UNPLAYABLE -> R.string.tts_fallback_unplayable
    }

    /** Second line of the notification for reasons with a help page ([TtsFailures.helpUrl]), else null. */
    @StringRes
    fun helpActionRes(reason: FallbackReason): Int? = when (TtsFailures.helpUrl(reason)) {
        null -> null
        else -> if (reason == FallbackReason.OPENAI_NO_CREDIT) R.string.tts_alert_open_billing else R.string.tts_alert_open_keys
    }
}
