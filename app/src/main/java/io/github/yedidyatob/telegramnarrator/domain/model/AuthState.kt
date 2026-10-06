package io.github.yedidyatob.telegramnarrator.domain.model

sealed class AuthState {
    object Initializing : AuthState()
    object Unauthenticated : AuthState()
    object WaitPhoneNumber : AuthState()
    object WaitCode : AuthState()
    object WaitPassword : AuthState()
    object Authenticated : AuthState()

    /**
     * TDLib could not start. [message] is the technical detail (logs, debugging); the screen shows the
     * localized text for [reason].
     */
    data class Error(val message: String, val reason: StartupFailure = StartupFailure.INIT_FAILED) : AuthState()
}

/** Why the Telegram client could not start (each has its own string resource). */
enum class StartupFailure {
    /** Build without TELEGRAM_API_ID / TELEGRAM_API_HASH (developer builds only). */
    MISSING_CREDENTIALS,
    /** Android Keystore unavailable: the encrypted database cannot be opened right now. */
    SECURE_STORAGE_UNAVAILABLE,
    INIT_FAILED
}
