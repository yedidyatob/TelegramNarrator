package com.example.telegramnarrator.data.repository

import com.example.telegramnarrator.BuildConfig
import com.example.telegramnarrator.data.tdlib.TdLibClient
import com.example.telegramnarrator.domain.model.AuthState
import com.example.telegramnarrator.domain.repository.AuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.stateIn
import org.drinkless.td.libcore.telegram.TdApi
import javax.inject.Inject
import javax.inject.Singleton
import java.io.File

@Singleton
class TdLibAuthRepository @Inject constructor(
    private val client: TdLibClient,
    private val filesDir: File // We'll inject this via a Hilt module
) : AuthRepository {

    private val updates = client.updates
        .stateIn(CoroutineScope(Dispatchers.IO), SharingStarted.Eagerly, null)

    override val authState: Flow<AuthState> = updates
        .filterIsInstance<TdApi.UpdateAuthorizationState>()
        .map { update ->
            handleAuthState(update.authorizationState)
        }
        
    private suspend fun handleAuthState(state: TdApi.AuthorizationState): AuthState {
        when (state) {
            is TdApi.AuthorizationStateWaitTdlibParameters -> {
                val parameters = TdApi.TdlibParameters()
                parameters.databaseDirectory = File(filesDir, "tdlib").absolutePath
                parameters.useMessageDatabase = true
                parameters.useSecretChats = false
                parameters.apiId = BuildConfig.TELEGRAM_API_ID.toIntOrNull() ?: 0
                parameters.apiHash = BuildConfig.TELEGRAM_API_HASH
                parameters.systemLanguageCode = "en"
                parameters.deviceModel = "Android"
                parameters.applicationVersion = "1.0"
                parameters.enableStorageOptimizer = true

                client.send(TdApi.SetTdlibParameters(parameters))
                return AuthState.Unauthenticated
            }
            is TdApi.AuthorizationStateWaitPhoneNumber -> return AuthState.WaitPhoneNumber
            is TdApi.AuthorizationStateWaitCode -> return AuthState.WaitCode
            is TdApi.AuthorizationStateWaitPassword -> return AuthState.WaitPassword
            is TdApi.AuthorizationStateReady -> return AuthState.Authenticated
            else -> return AuthState.Unauthenticated
        }
    }

    override suspend fun setPhoneNumber(phoneNumber: String) {
        client.send(TdApi.SetAuthenticationPhoneNumber(phoneNumber, null))
    }

    override suspend fun checkAuthenticationCode(code: String) {
        client.send(TdApi.CheckAuthenticationCode(code))
    }

    override suspend fun checkAuthenticationPassword(password: String) {
        client.send(TdApi.CheckAuthenticationPassword(password))
    }

    override suspend fun logOut() {
        client.send(TdApi.LogOut())
    }
}
