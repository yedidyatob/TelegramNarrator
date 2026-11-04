package io.github.yedidyatob.telegramnarrator.domain.auth

import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeDeliveryTest {
    private fun of(type: TdApi.AuthenticationCodeType) = CodeDelivery.fromTdType(type.javaClass.simpleName)

    @Test
    fun `maps every TDLib code type`() {
        assertEquals(CodeDelivery.TELEGRAM_APP, of(TdApi.AuthenticationCodeTypeTelegramMessage()))
        assertEquals(CodeDelivery.SMS, of(TdApi.AuthenticationCodeTypeSms()))
        assertEquals(CodeDelivery.SMS_WORD, of(TdApi.AuthenticationCodeTypeSmsWord()))
        assertEquals(CodeDelivery.SMS_PHRASE, of(TdApi.AuthenticationCodeTypeSmsPhrase()))
        assertEquals(CodeDelivery.CALL, of(TdApi.AuthenticationCodeTypeCall()))
        assertEquals(CodeDelivery.MISSED_CALL, of(TdApi.AuthenticationCodeTypeFlashCall()))
        assertEquals(CodeDelivery.MISSED_CALL, of(TdApi.AuthenticationCodeTypeMissedCall()))
        assertEquals(CodeDelivery.FRAGMENT, of(TdApi.AuthenticationCodeTypeFragment()))
        assertEquals(CodeDelivery.FIREBASE, of(TdApi.AuthenticationCodeTypeFirebaseAndroid()))
        assertEquals(CodeDelivery.FIREBASE, of(TdApi.AuthenticationCodeTypeFirebaseIos()))
        assertEquals(CodeDelivery.UNKNOWN, CodeDelivery.fromTdType("SomethingNew"))
        assertNull(CodeDelivery.fromTdType(null))
        assertTrue(CodeDelivery.SMS_WORD.isText)
        assertFalse(CodeDelivery.SMS.isText)
    }

    @Test
    fun `resend label follows the next type`() {
        assertEquals(ResendKind.SMS, CodeResend.kindOf(CodeDelivery.SMS))
        assertEquals(ResendKind.SMS, CodeResend.kindOf(CodeDelivery.SMS_PHRASE))
        assertEquals(ResendKind.CALL, CodeResend.kindOf(CodeDelivery.CALL))
        assertEquals(ResendKind.AGAIN, CodeResend.kindOf(CodeDelivery.TELEGRAM_APP))
        assertEquals(ResendKind.AGAIN, CodeResend.kindOf(null))
    }

    @Test
    fun `countdown rounds up and stops at zero`() {
        assertEquals(60, CodeResend.secondsLeft(60, 0))
        assertEquals(60, CodeResend.secondsLeft(60, 1))
        assertEquals(59, CodeResend.secondsLeft(60, 1_000))
        assertEquals(1, CodeResend.secondsLeft(60, 59_001))
        assertEquals(0, CodeResend.secondsLeft(60, 60_000))
        assertEquals(0, CodeResend.secondsLeft(60, 999_999))
        assertEquals(0, CodeResend.secondsLeft(0, 0))
    }

    @Test
    fun `phone number gets a plus`() {
        assertEquals("+15551234567", CodeResend.displayNumber("15551234567"))
        assertEquals("+9990000000", CodeResend.displayNumber("+9990000000"))
        assertEquals("", CodeResend.displayNumber(""))
    }
}
