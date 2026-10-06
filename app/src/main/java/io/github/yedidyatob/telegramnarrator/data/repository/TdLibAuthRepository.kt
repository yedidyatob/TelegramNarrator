package io.github.yedidyatob.telegramnarrator.data.repository

import io.github.yedidyatob.telegramnarrator.BuildConfig
import io.github.yedidyatob.telegramnarrator.data.tdlib.TdLibClient
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
    private val client: TdLibClient,
    private val filesDir: File,
    private val keyStore: TdLibDatabaseKeyStore
) : AuthRepository {

    private companion object {
        const val TAG = "AuthRepository"
        const val TDLIB_DIR = "tdlib"
        /** TDLib's binlog in the database directory (`td_test.binlog` only on the test DC). */
        const val BINLOG_FILE = "td.binlog"
        const val SECURE_STORAGE_UNAVAILABLE =
            "Secure storage is not available right now, so the Telegram database cannot be opened. Please retry."
    }

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
                val failure = openDatabase(apiId)
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

    /**
     * Sends `setTdlibParameters` with the Keystore-protected database key (issue #15), migrating an
     * unencrypted database from older installs and resetting a database whose key was lost.
     * @return an error message for the login screen, or null on success.
     */
    private suspend fun openDatabase(apiId: Int): String? {
        val databaseDir = File(filesDir, TDLIB_DIR)
        val appKey = keyStore.getOrCreateKey()
        val keyAvailable = appKey != null
        val order = DatabaseEncryptionPolicy.openOrder(
            databaseExists = File(databaseDir, BINLOG_FILE).exists(),
            markedEncrypted = keyStore.markedEncrypted,
            appKeyAvailable = keyAvailable
        )
        for (choice in order) {
            when (val result = trySetParameters(apiId, databaseDir, keyFor(choice, appKey))) {
                OpenResult.Ok -> {
                    onDatabaseOpened(choice, appKey)
                    return null
                }
                OpenResult.WrongKey -> Log.w(TAG, "TDLib rejected the database key ($choice)")
                is OpenResult.Failed -> return result.message
            }
        }
        if (!DatabaseEncryptionPolicy.mayResetAfterWrongKey(keyAvailable)) {
            return SECURE_STORAGE_UNAVAILABLE
        }
        // The key that encrypted the database is gone (e.g. Keystore entry lost): the local database
        // cannot be read anymore. Start over with an empty encrypted database; the user logs in again.
        Log.w(TAG, "No key opens the TDLib database; resetting it (re-login required)")
        databaseDir.deleteRecursively()
        val choice = DatabaseEncryptionPolicy.keyAfterReset(keyAvailable)
        return when (val result = trySetParameters(apiId, databaseDir, keyFor(choice, appKey))) {
            OpenResult.Ok -> {
                onDatabaseOpened(choice, appKey)
                null
            }
            OpenResult.WrongKey -> "Could not open the Telegram database"
            is OpenResult.Failed -> result.message
        }
    }

    private suspend fun onDatabaseOpened(choice: DatabaseEncryptionPolicy.KeyChoice, appKey: ByteArray?) {
        if (choice == DatabaseEncryptionPolicy.KeyChoice.APP_KEY) {
            if (!keyStore.markedEncrypted) keyStore.markedEncrypted = true
            return
        }
        if (!DatabaseEncryptionPolicy.needsRekey(choice, appKey != null)) {
            keyStore.markedEncrypted = false
            return
        }
        try {
            client.send<TdApi.Ok>(TdApi.SetDatabaseEncryptionKey(appKey))
            keyStore.markedEncrypted = true
            Log.i(TAG, "TDLib database re-encrypted with the Keystore-protected key")
        } catch (e: Exception) {
            // Still usable with the empty key; the migration is retried at the next start.
            Log.w(TAG, "Could not re-encrypt the TDLib database", e)
            keyStore.markedEncrypted = false
        }
    }

    private fun keyFor(choice: DatabaseEncryptionPolicy.KeyChoice, appKey: ByteArray?): ByteArray? =
        if (choice == DatabaseEncryptionPolicy.KeyChoice.APP_KEY) appKey else null

    private sealed class OpenResult {
        object Ok : OpenResult()
        object WrongKey : OpenResult()
        class Failed(val message: String) : OpenResult()
    }

    private suspend fun trySetParameters(apiId: Int, databaseDir: File, key: ByteArray?): OpenResult = try {
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
