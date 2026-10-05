package com.example.telegramnarrator.data.repository

import com.example.telegramnarrator.BuildConfig
import com.example.telegramnarrator.data.tdlib.TdLibClient
import com.example.telegramnarrator.domain.model.ApiCredentials
import com.example.telegramnarrator.domain.model.AuthState
import com.example.telegramnarrator.domain.repository.AuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import java.io.File

@Singleton
class TdLibAuthRepository @Inject constructor(
    private val client: TdLibClient,
    private val filesDir: File
) : AuthRepository {

    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val _authState = MutableStateFlow<AuthState>(AuthState.Initializing)
    override val authState: Flow<AuthState> = _authState.asStateFlow()

    init {
        repositoryScope.launch {
            client.updates.collect { update ->
                if (update is TdApi.UpdateAuthorizationState) {
                    handleAuthorizationState(update.authorizationState)
                }
            }
        }
    }

    private suspend fun handleAuthorizationState(state: TdApi.AuthorizationState) {
        Log.d("AuthRepository", "New TDLib state: ${state::class.simpleName}")
        
        val newState = when (state) {
            is TdApi.AuthorizationStateWaitTdlibParameters -> {
                val apiId = ApiCredentials.parse(BuildConfig.TELEGRAM_API_ID, BuildConfig.TELEGRAM_API_HASH)
                if (apiId == null) {
                    // Without credentials TDLib never leaves this state: show why instead of spinning forever
                    _authState.value = AuthState.Error(ApiCredentials.MISSING_MESSAGE)
                    return
                }
                var failure: String? = null
                try {
                    client.send<TdApi.Ok>(TdApi.SetTdlibParameters(
                        false,                                          // useTestDc
                        File(filesDir, "tdlib").absolutePath,           // databaseDirectory
                        null,                                           // filesDirectory
                        null,                                           // databaseEncryptionKey
                        true,                                           // useFileDatabase
                        true,                                           // useChatInfoDatabase
                        true,                                           // useMessageDatabase
                        false,                                          // useSecretChats
                        apiId,                                          // apiId
                        BuildConfig.TELEGRAM_API_HASH,                  // apiHash
                        "en",                                           // systemLanguageCode
                        "Android",                                      // deviceModel
                        "",                                             // systemVersion
                        "1.0"                                           // applicationVersion
                    ))
                } catch (e: Exception) {
                    Log.e("AuthRepository", "Failed to set TDLib parameters", e)
                    failure = e.message ?: "Could not initialize Telegram"
                }
                if (failure != null) AuthState.Error(failure) else AuthState.Initializing
            }
            is TdApi.AuthorizationStateWaitPhoneNumber -> AuthState.WaitPhoneNumber
            is TdApi.AuthorizationStateWaitCode -> AuthState.WaitCode
            is TdApi.AuthorizationStateWaitPassword -> AuthState.WaitPassword
            is TdApi.AuthorizationStateReady -> AuthState.Authenticated
            is TdApi.AuthorizationStateLoggingOut -> AuthState.Unauthenticated
            is TdApi.AuthorizationStateClosing -> AuthState.Unauthenticated
            is TdApi.AuthorizationStateClosed -> {
                client.recreateClient()
                AuthState.Initializing
            }
            else -> AuthState.Unauthenticated
        }
        _authState.value = newState
    }

    override suspend fun retryInitialization() {
        _authState.value = AuthState.Initializing
        handleAuthorizationState(TdApi.AuthorizationStateWaitTdlibParameters())
    }

    override suspend fun setPhoneNumber(phoneNumber: String) {
        client.send<TdApi.Ok>(TdApi.SetAuthenticationPhoneNumber(phoneNumber, null))
    }
 
    override suspend fun checkAuthenticationCode(code: String) {
        client.send<TdApi.Ok>(TdApi.CheckAuthenticationCode(code))
    }
 
    override suspend fun checkAuthenticationPassword(password: String) {
        client.send<TdApi.Ok>(TdApi.CheckAuthenticationPassword(password))
    }

    override suspend fun logOut() {
        client.send<TdApi.Ok>(TdApi.LogOut())
    }
}
