package io.github.yedidyatob.telegramnarrator.domain.repository

import io.github.yedidyatob.telegramnarrator.domain.model.AuthState
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val authState: Flow<AuthState>

    /** Re-sends the TDLib parameters after an [AuthState.Error]. */
    suspend fun retryInitialization()

    suspend fun setPhoneNumber(phoneNumber: String)

    suspend fun checkAuthenticationCode(code: String)

    suspend fun checkAuthenticationPassword(password: String)

    /** "Send again" on the code step (TDLib `resendAuthenticationCode`). */
    suspend fun resendCode()

    /** "Wrong number?": back to the phone step; the next [setPhoneNumber] replaces the pending login. */
    fun changePhoneNumber()

    suspend fun logOut()
}
