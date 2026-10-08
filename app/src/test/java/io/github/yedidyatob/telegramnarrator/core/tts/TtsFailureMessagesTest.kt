package io.github.yedidyatob.telegramnarrator.core.tts

import io.github.yedidyatob.telegramnarrator.domain.tts.FallbackReason
import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for [TtsFailureMessages] to verify all fallback reasons map to
 * valid string resource IDs.
 */
class TtsFailureMessagesTest {

    @Test
    fun allFallbackReasonsHaveMessageResources() {
        for (reason in FallbackReason.entries) {
            val resId = TtsFailureMessages.messageRes(reason)
            assertTrue("FallbackReason.$reason missing string resource", resId != 0)
        }
    }

    @Test
    fun helpActionRes_returnsResourceForAccountIssues() {
        assertNotNull(TtsFailureMessages.helpActionRes(FallbackReason.GEMINI_INVALID_KEY))
        assertNotNull(TtsFailureMessages.helpActionRes(FallbackReason.GEMINI_NO_CREDIT))
        assertNull(TtsFailureMessages.helpActionRes(FallbackReason.NO_NETWORK))
    }
}
