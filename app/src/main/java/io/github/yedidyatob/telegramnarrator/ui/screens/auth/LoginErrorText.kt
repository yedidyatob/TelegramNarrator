package io.github.yedidyatob.telegramnarrator.ui.screens.auth

import androidx.annotation.StringRes
import io.github.yedidyatob.telegramnarrator.R
import io.github.yedidyatob.telegramnarrator.domain.auth.LoginError
import io.github.yedidyatob.telegramnarrator.domain.model.StartupFailure

/** Message shown under a login field for each [LoginError]. */
@StringRes
fun LoginError.messageRes(): Int = when (this) {
    LoginError.PHONE_INVALID -> R.string.login_error_phone_invalid
    LoginError.PHONE_BANNED -> R.string.login_error_phone_banned
    LoginError.CODE_INVALID -> R.string.login_error_code_invalid
    LoginError.CODE_EXPIRED -> R.string.login_error_code_expired
    LoginError.PASSWORD_INVALID -> R.string.login_error_password_invalid
    LoginError.TOO_MANY_ATTEMPTS -> R.string.login_error_too_many_attempts
    LoginError.NETWORK -> R.string.login_error_network
    LoginError.UNKNOWN -> R.string.error_unknown
}

/** Body of the "Could not start Telegram" screen for each [StartupFailure]. */
@StringRes
fun StartupFailure.messageRes(): Int = when (this) {
    StartupFailure.MISSING_CREDENTIALS -> R.string.login_startup_missing_credentials
    StartupFailure.SECURE_STORAGE_UNAVAILABLE -> R.string.login_startup_secure_storage
    StartupFailure.INIT_FAILED -> R.string.login_startup_failed
}
