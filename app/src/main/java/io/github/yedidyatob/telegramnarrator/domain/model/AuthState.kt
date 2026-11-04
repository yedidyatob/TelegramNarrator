package io.github.yedidyatob.telegramnarrator.domain.model

import io.github.yedidyatob.telegramnarrator.domain.auth.CodeDelivery

sealed class AuthState {
    object Initializing : AuthState()

    object Unauthenticated : AuthState()

    object WaitPhoneNumber : AuthState()

    /** Waiting for the login code; [nextType] is what "Send again" switches to (null: no resend possible). */
    data class WaitCode(
        val phoneNumber: String = "",
        val type: CodeDelivery = CodeDelivery.UNKNOWN,
        val nextType: CodeDelivery? = null,
        val timeoutSec: Int = 0,
    ) : AuthState()

    /** A login step this app does not support (email login, sign-up); the user can go back to the phone step. */
    data class Unsupported(
        val step: UnsupportedLoginStep,
    ) : AuthState()

    object WaitPassword : AuthState()

    object Authenticated : AuthState()

    /**
     * TDLib could not start. [message] is the technical detail (logs, debugging); the screen shows the
     * localized text for [reason].
     */
    data class Error(
        val message: String,
        val reason: StartupFailure = StartupFailure.INIT_FAILED,
    ) : AuthState()
}

/** Why the Telegram client could not start (each has its own string resource). */
enum class StartupFailure {
    /** Build without TELEGRAM_API_ID / TELEGRAM_API_HASH (developer builds only). */
    MISSING_CREDENTIALS,

    /** Android Keystore unavailable: the encrypted database cannot be opened right now. */
    SECURE_STORAGE_UNAVAILABLE,
    INIT_FAILED,
}

enum class UnsupportedLoginStep {
    /** Telegram asks for an email address or an email code. */
    EMAIL,

    /** No account for this number: sign-up is only possible in the official app. */
    REGISTRATION,

    /** QR-code login confirmation on another device (never requested by this app). */
    OTHER_DEVICE,
}
