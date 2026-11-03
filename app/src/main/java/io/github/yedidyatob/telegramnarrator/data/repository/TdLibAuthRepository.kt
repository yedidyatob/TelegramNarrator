package io.github.yedidyatob.telegramnarrator.data.repository

import io.github.yedidyatob.telegramnarrator.domain.model.StartupFailure
import io.github.yedidyatob.telegramnarrator.BuildConfig
import io.github.yedidyatob.telegramnarrator.data.tdlib.TdGateway
import io.github.yedidyatob.telegramnarrator.data.tdlib.TdLibDatabaseKeyStore
import io.github.yedidyatob.telegramnarrator.data.tdlib.TdLibException
import io.github.yedidyatob.telegramnarrator.domain.model.ApiCredentials
import io.github.yedidyatob.telegramnarrator.domain.model.AuthState
import io.github.yedidyatob.telegramnarrator.domain.repository.AuthRepository
import io.github.yedidyatob.telegramnarrator.domain.security.DatabaseEncryptionPolicy
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
    private val client: TdGateway,
    private val filesDir: File,
    private val keyStore: TdLibDatabaseKeyStore
) : AuthRepository {

    private companion object {
        const val TAG = "AuthRepository"
        const val TDLIB_DIR = "tdlib"
        const val SECURE_STORAGE_UNAVAILABLE =
            "Secure storage is not available right now, so the Telegram database cannot be opened. Please retry."
    }

    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val _authState = MutableStateFlow<AuthState>(AuthState.Initializing)
    override val authState: Flow<AuthState> = _authState.asStateFlow()

    init {
        repositoryScope.launch {
            // A state flow (#13): the first state (WaitTdlibParameters) is often reported before this collector
            // starts; with the update stream it would be missed and login would stay on "Initializing"
            client.authorizationState.filterNotNull().collect { handleAuthorizationState(it) }
        }
    }

    private suspend fun handleAuthorizationState(state: TdApi.AuthorizationState) {
        Log.d("AuthRepository", "New TDLib state: ${state::class.simpleName}")
        
        val newState = when (state) {
            is TdApi.AuthorizationStateWaitTdlibParameters -> {
                val apiId = ApiCredentials.parse(BuildConfig.TELEGRAM_API_ID, BuildConfig.TELEGRAM_API_HASH)
                if (apiId == null) {
                    // Without credentials TDLib never leaves this state: show why instead of spinning forever
                    _authState.value = AuthState.Error(ApiCredentials.MISSING_MESSAGE, StartupFailure.MISSING_CREDENTIALS)
                    return
                }
                val failure = openDatabase(apiId)
                when (failure) {
                    null -> AuthState.Initializing
                    SECURE_STORAGE_UNAVAILABLE -> AuthState.Error(failure, StartupFailure.SECURE_STORAGE_UNAVAILABLE)
                    else -> AuthState.Error(failure, StartupFailure.INIT_FAILED)
                }
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

    /**
     * Sends `setTdlibParameters` with the Keystore-protected database key (issue #15). Without that key nothing
     * is opened (#66: never an unencrypted database); the user sees the secure-storage error and can retry.
     * A database the key cannot open (e.g. the Keystore key was lost) is deleted and created again, encrypted;
     * the user logs in again.
     * @return an error message for the login screen, or null on success.
     */
    private suspend fun openDatabase(apiId: Int): String? {
        val databaseDir = File(filesDir, TDLIB_DIR)
        val appKey = keyStore.getOrCreateKey() ?: return SECURE_STORAGE_UNAVAILABLE
        when (val result = trySetParameters(apiId, databaseDir, appKey)) {
            OpenResult.Ok -> return null
            is OpenResult.Failed -> return result.message
            OpenResult.WrongKey -> Unit
        }
        Log.w(TAG, "The key does not open the TDLib database; resetting it (re-login required)")
        databaseDir.deleteRecursively()
        return when (val result = trySetParameters(apiId, databaseDir, appKey)) {
            OpenResult.Ok -> null
            OpenResult.WrongKey -> "Could not open the Telegram database"
            is OpenResult.Failed -> result.message
        }
    }

    private sealed class OpenResult {
        object Ok : OpenResult()
        object WrongKey : OpenResult()
        class Failed(val message: String) : OpenResult()
    }

    private suspend fun trySetParameters(apiId: Int, databaseDir: File, key: ByteArray): OpenResult = try {
        client.send<TdApi.Ok>(TdApi.SetTdlibParameters(
            false,                                          // useTestDc
            databaseDir.absolutePath,                       // databaseDirectory
            null,                                           // filesDirectory
            key,                                            // databaseEncryptionKey (never logged)
            true,                                           // useFileDatabase
            true,                                           // useChatInfoDatabase
            true,                                           // useMessageDatabase
            false,                                          // useSecretChats
            apiId,                                          // apiId
            BuildConfig.TELEGRAM_API_HASH,                  // apiHash
            "en",                                           // systemLanguageCode
            "Android",                                      // deviceModel
            "",                                             // systemVersion
            BuildConfig.VERSION_NAME                        // applicationVersion
        ))
        OpenResult.Ok
    } catch (e: TdLibException) {
        if (DatabaseEncryptionPolicy.isWrongKeyError(e.code, e.tdMessage)) {
            OpenResult.WrongKey
        } else {
            Log.e(TAG, "Failed to set TDLib parameters", e)
            OpenResult.Failed(e.message ?: "Could not initialize Telegram")
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to set TDLib parameters", e)
        OpenResult.Failed(e.message ?: "Could not initialize Telegram")
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
