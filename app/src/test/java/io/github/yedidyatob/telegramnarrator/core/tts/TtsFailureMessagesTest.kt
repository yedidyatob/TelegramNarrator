package io.github.yedidyatob.telegramnarrator.core.tts

import io.github.yedidyatob.telegramnarrator.R
import io.github.yedidyatob.telegramnarrator.domain.tts.FallbackReason
import io.github.yedidyatob.telegramnarrator.domain.tts.TtsFailures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TtsFailureMessagesTest {
    @Test
    fun `every reason has its own message`() {
        val messages = FallbackReason.values().map { TtsFailureMessages.messageRes(it) }
        assertEquals(FallbackReason.values().size, messages.toSet().size)
    }

    @Test
    fun `specific messages for the OpenAI account cases`() {
        assertEquals(R.string.tts_fallback_openai_invalid_key, TtsFailureMessages.messageRes(FallbackReason.OPENAI_INVALID_KEY))
        assertEquals(R.string.tts_fallback_openai_no_credit, TtsFailureMessages.messageRes(FallbackReason.OPENAI_NO_CREDIT))
        assertEquals(R.string.tts_fallback_openai_rate_limited, TtsFailureMessages.messageRes(FallbackReason.OPENAI_RATE_LIMITED))
        assertEquals(R.string.tts_fallback_no_network, TtsFailureMessages.messageRes(FallbackReason.NO_NETWORK))
        assertEquals(R.string.tts_fallback_edge_throttled, TtsFailureMessages.messageRes(FallbackReason.EDGE_THROTTLED))
    }

    @Test
    fun `help action text exactly when there is a help link`() {
        FallbackReason.values().forEach { reason ->
            if (TtsFailures.helpUrl(reason) != null) assertNotNull(TtsFailureMessages.helpActionRes(reason))
            else assertNull(TtsFailureMessages.helpActionRes(reason))
        }
        assertEquals(R.string.tts_alert_open_billing, TtsFailureMessages.helpActionRes(FallbackReason.OPENAI_NO_CREDIT))
        assertEquals(R.string.tts_alert_open_keys, TtsFailureMessages.helpActionRes(FallbackReason.OPENAI_INVALID_KEY))
    }
}
