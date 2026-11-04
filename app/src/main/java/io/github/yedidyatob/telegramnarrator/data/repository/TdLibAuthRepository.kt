package io.github.yedidyatob.telegramnarrator.data.repository

import android.util.Log
import io.github.yedidyatob.telegramnarrator.BuildConfig
import io.github.yedidyatob.telegramnarrator.core.diagnostics.AutoLog
import io.github.yedidyatob.telegramnarrator.data.tdlib.TdGateway
import io.github.yedidyatob.telegramnarrator.data.tdlib.TdLibDatabaseKeyStore
import io.github.yedidyatob.telegramnarrator.data.tdlib.TdLibException
import io.github.yedidyatob.telegramnarrator.domain.auth.CodeDelivery
import io.github.yedidyatob.telegramnarrator.domain.model.ApiCredentials
import io.github.yedidyatob.telegramnarrator.domain.model.AuthState
import io.github.yedidyatob.telegramnarrator.domain.model.StartupFailure
import io.github.yedidyatob.telegramnarrator.domain.model.UnsupportedLoginStep
import io.github.yedidyatob.telegramnarrator.domain.repository.AuthRepository
import io.github.yedidyatob.telegramnarrator.domain.security.DatabaseEncryptionPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TdLibAuthRepository
    @Inject
    constructor(
        private val client: TdGateway,
        private val filesDir: File,
        private val keyStore: TdLibDatabaseKeyStore,
    ) : AuthRepository {
        private companion object {
            const val TAG = "AuthRepository"
            const val TDLIB_DIR = "tdlib"
            const val SECURE_STORAGE_UNAVAILABLE =
                "Secure storage is not available right now, so the Telegram database cannot be opened. Please retry."
        }

        private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        private val _authState = MutableStateFlow<AuthState>(AuthState.Initializing)

        /** "Wrong number?" was tapped: show the phone step although TDLib still waits for the code. */
        private val changingNumber = MutableStateFlow(false)

        override val authState: Flow<AuthState> =
            combine(_authState, changingNumber) { state, changing ->
                val pendingLogin = state is AuthState.WaitCode || state is AuthState.WaitPassword || state is AuthState.Unsupported
                if (changing && pendingLogin) AuthState.WaitPhoneNumber else state
            }

        init {
            repositoryScope.launch {
                // A state flow (#13): the first state (WaitTdlibParameters) is often reported before this collector
                // starts; with the update stream it would be missed and login would stay on "Initializing"
                client.authorizationState.filterNotNull().collect { handleAuthorizationState(it) }
            }
        }

        private suspend fun handleAuthorizationState(state: TdApi.AuthorizationState) {
            Log.d("AuthRepository", "New TDLib state: ${state::class.simpleName}")

            val newState =
                when (state) {
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

                    is TdApi.AuthorizationStateWaitPhoneNumber -> {
                        AuthState.WaitPhoneNumber
                    }

                    is TdApi.AuthorizationStateWaitCode -> {
                        codeState(state.codeInfo)
                    }

                    is TdApi.AuthorizationStateWaitEmailAddress, is TdApi.AuthorizationStateWaitEmailCode -> {
                        AuthState.Unsupported(UnsupportedLoginStep.EMAIL)
                    }

                    is TdApi.AuthorizationStateWaitRegistration -> {
                        AuthState.Unsupported(UnsupportedLoginStep.REGISTRATION)
                    }

                    is TdApi.AuthorizationStateWaitOtherDeviceConfirmation -> {
                        AuthState.Unsupported(UnsupportedLoginStep.OTHER_DEVICE)
                    }

                    is TdApi.AuthorizationStateWaitPassword -> {
                        AuthState.WaitPassword
                    }

                    is TdApi.AuthorizationStateReady -> {
                        AuthState.Authenticated
                    }

                    is TdApi.AuthorizationStateLoggingOut -> {
                        AuthState.Unauthenticated
                    }

                    is TdApi.AuthorizationStateClosing -> {
                        AuthState.Unauthenticated
                    }

                    is TdApi.AuthorizationStateClosed -> {
                        client.recreateClient()
                        AuthState.Initializing
                    }

                    else -> {
                        AuthState.Unauthenticated
                    }
                }
            _authState.value = newState
        }

        private fun codeState(info: TdApi.AuthenticationCodeInfo?): AuthState.WaitCode {
            val type = CodeDelivery.fromTdType(info?.type?.javaClass?.simpleName) ?: CodeDelivery.UNKNOWN
            val next = CodeDelivery.fromTdType(info?.nextType?.javaClass?.simpleName)
            // Code types only: never the phone number or the code
            AutoLog.event("Login code sent: type=$type next=$next timeout=${info?.timeout}")
            return AuthState.WaitCode(info?.phoneNumber.orEmpty(), type, next, info?.timeout ?: 0)
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

            class Failed(
                val message: String,
            ) : OpenResult()
        }

        private suspend fun trySetParameters(
            apiId: Int,
            databaseDir: File,
            key: ByteArray,
        ): OpenResult =
            try {
                client.send<TdApi.Ok>(
                    TdApi.SetTdlibParameters(
                        false, // useTestDc
                        databaseDir.absolutePath, // databaseDirectory
                        null, // filesDirectory
                        key, // databaseEncryptionKey (never logged)
                        true, // useFileDatabase
                        true, // useChatInfoDatabase
                        true, // useMessageDatabase
                        false, // useSecretChats
                        apiId, // apiId
                        BuildConfig.TELEGRAM_API_HASH, // apiHash
                        "en", // systemLanguageCode
                        "Android", // deviceModel
                        "", // systemVersion
                        BuildConfig.VERSION_NAME, // applicationVersion
                    ),
                )
                OpenResult.Ok
            } catch (e: TdLibException) {
                if (DatabaseEncryptionPolicy.isWrongKeyError(e.code, e.tdMessage)) {
                    OpenResult.WrongKey
                } else {
                    Log.e(TAG, "Failed to set TDLib parameters", e)
                    OpenResult.Failed(e.message ?: "Could not initialize Telegram")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set TDLib parameters", e)
                OpenResult.Failed(e.message ?: "Could not initialize Telegram")
            }

        override suspend fun retryInitialization() {
            _authState.value = AuthState.Initializing
            handleAuthorizationState(TdApi.AuthorizationStateWaitTdlibParameters())
        }

        override suspend fun setPhoneNumber(phoneNumber: String) {
            // Default settings (null): no flash / missed calls, no SMS Retriever, no Firebase (this app has none).
            // Also allowed while TDLib waits for a code / password, which replaces that pending login ("Wrong number?").
            client.send<TdApi.Ok>(TdApi.SetAuthenticationPhoneNumber(phoneNumber, null))
            changingNumber.value = false
        }

        override suspend fun resendCode() {
            client.send<TdApi.Ok>(TdApi.ResendAuthenticationCode(TdApi.ResendCodeReasonUserRequest()))
        }

        override fun changePhoneNumber() {
            changingNumber.value = true
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
