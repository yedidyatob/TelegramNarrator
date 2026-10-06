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
    suspend fun logOut()
}
