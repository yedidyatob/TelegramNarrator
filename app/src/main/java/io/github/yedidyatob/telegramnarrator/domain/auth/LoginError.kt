package io.github.yedidyatob.telegramnarrator.domain.auth

/**
 * What went wrong with a login step, mapped from TDLib's error code/message to something a person can act on.
 * The UI shows a string resource per value (never TDLib's raw "PHONE_CODE_INVALID" text).
 */
enum class LoginError {
    PHONE_INVALID,
    PHONE_BANNED,
    CODE_INVALID,
    CODE_EXPIRED,
    PASSWORD_INVALID,
    TOO_MANY_ATTEMPTS,
    NETWORK,
    UNKNOWN;

    companion object {
        /** [code]/[message] of a TDLib `Error` (null when the failure was not a TDLib error). */
        fun classify(code: Int?, message: String?): LoginError {
            val text = message.orEmpty().uppercase()
            return when {
                code == 429 || "FLOOD_WAIT" in text || "TOO MANY REQUESTS" in text -> TOO_MANY_ATTEMPTS
                "PHONE_NUMBER_BANNED" in text -> PHONE_BANNED
                "PHONE_NUMBER_INVALID" in text -> PHONE_INVALID
                "PHONE_CODE_EXPIRED" in text -> CODE_EXPIRED
                "PHONE_CODE_INVALID" in text || "PHONE_CODE_EMPTY" in text -> CODE_INVALID
                "PASSWORD_HASH_INVALID" in text || "PASSWORD_INVALID" in text -> PASSWORD_INVALID
                "NETWORK" in text || "TIMEOUT" in text -> NETWORK
                else -> UNKNOWN
            }
        }
    }
}
