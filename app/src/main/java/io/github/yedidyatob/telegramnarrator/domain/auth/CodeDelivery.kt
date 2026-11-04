package io.github.yedidyatob.telegramnarrator.domain.auth

/** Where Telegram sent the login code (TDLib `AuthenticationCodeType`), for the code screen. */
enum class CodeDelivery {
    TELEGRAM_APP,
    SMS,
    SMS_WORD,
    SMS_PHRASE,
    CALL,
    MISSED_CALL,
    FRAGMENT,

    /** Firebase / Play Integrity verification: this app cannot complete it, the user has to ask for another way. */
    FIREBASE,
    UNKNOWN,
    ;

    /** The code is a word or phrase, not digits. */
    val isText: Boolean get() = this == SMS_WORD || this == SMS_PHRASE

    companion object {
        /** TDLib class simple name ("AuthenticationCodeTypeSms") -> delivery; null input = no next type. */
        fun fromTdType(simpleName: String?): CodeDelivery? =
            when (simpleName) {
                null -> null
                "AuthenticationCodeTypeTelegramMessage" -> TELEGRAM_APP
                "AuthenticationCodeTypeSms" -> SMS
                "AuthenticationCodeTypeSmsWord" -> SMS_WORD
                "AuthenticationCodeTypeSmsPhrase" -> SMS_PHRASE
                "AuthenticationCodeTypeCall" -> CALL
                "AuthenticationCodeTypeFlashCall", "AuthenticationCodeTypeMissedCall" -> MISSED_CALL
                "AuthenticationCodeTypeFragment" -> FRAGMENT
                "AuthenticationCodeTypeFirebaseAndroid", "AuthenticationCodeTypeFirebaseIos" -> FIREBASE
                else -> UNKNOWN
            }
    }
}

/** What the "Send again" button offers, from TDLib's `next_type`. */
enum class ResendKind { SMS, CALL, AGAIN }

object CodeResend {
    fun kindOf(next: CodeDelivery?): ResendKind =
        when (next) {
            CodeDelivery.SMS, CodeDelivery.SMS_WORD, CodeDelivery.SMS_PHRASE -> ResendKind.SMS
            CodeDelivery.CALL, CodeDelivery.MISSED_CALL -> ResendKind.CALL
            else -> ResendKind.AGAIN
        }

    /** Whole seconds until "Send again" is enabled ([timeoutSec] from codeInfo, [elapsedMs] since it arrived). */
    fun secondsLeft(
        timeoutSec: Int,
        elapsedMs: Long,
    ): Int = ((timeoutSec * 1000L - elapsedMs.coerceAtLeast(0) + 999) / 1000).toInt().coerceAtLeast(0)

    /** TDLib's phone number ("15551234567") with a leading "+", for display. */
    fun displayNumber(phone: String): String = if (phone.isBlank() || phone.startsWith("+")) phone else "+$phone"
}
