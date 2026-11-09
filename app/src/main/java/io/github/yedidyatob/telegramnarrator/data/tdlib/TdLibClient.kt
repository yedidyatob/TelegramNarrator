package io.github.yedidyatob.telegramnarrator.data.tdlib

import android.util.Log
import io.github.yedidyatob.telegramnarrator.core.diagnostics.AutoLog
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** The TDLib [Client] behind [TdGateway] (#13). */
@Singleton
class TdLibClient
    @Inject
    constructor() : TdGateway {
        @Volatile private var client: Client? = null
        private val bus = UpdateBus<TdApi.Object>()
        override val updates: Flow<TdApi.Object> = bus.flow

        // Kept here rather than in a collector of [updates]: TDLib only reports changes, and a late collector would
        // miss the current state (e.g. the first WaitTdlibParameters, leaving login stuck on "Initializing")
        private val _authorizationState = MutableStateFlow<TdApi.AuthorizationState?>(null)
        override val authorizationState: StateFlow<TdApi.AuthorizationState?> = _authorizationState.asStateFlow()

        private val _connectionState = MutableStateFlow<TdApi.ConnectionState?>(null)
        override val connectionState: StateFlow<TdApi.ConnectionState?> = _connectionState.asStateFlow()

        // #131 diagnostics, to be removed after: how many chats TDLib announced, to compare with what the chat
        // repository logs once it subscribes (a chat announced before that is never sent again)
        private val newChats =
            java.util.concurrent.atomic
                .AtomicInteger()

        init {
            Client.execute(TdApi.SetLogVerbosityLevel(1))
            recreateClient()
        }

        override fun recreateClient() {
            Log.d(TAG, "Recreating TDLib client")
            newChats.set(0)
            client =
                Client.create(
                    ::onUpdate,
                    { e -> TdLibErrors.onHandlerException("update handler", e) },
                    { e -> TdLibErrors.onHandlerException("result handler", e) },
                )
        }

        private fun onUpdate(update: TdApi.Object) {
            if (update is TdApi.UpdateNewChat) {
                val n = newChats.incrementAndGet()
                if (n == 1 || n % 50 == 0) AutoLog.event("#131 client UpdateNewChat count=$n")
            }
            if (update is TdApi.UpdateAuthorizationState) {
                AutoLog.event(
                    "#131 client auth=${update.authorizationState.javaClass.simpleName}",
                )
            }
            when (update) {
                is TdApi.UpdateAuthorizationState -> _authorizationState.value = update.authorizationState
                is TdApi.UpdateConnectionState -> _connectionState.value = update.state
            }
            bus.publish(update)
        }

        override suspend fun <T : TdApi.Object> send(
            function: TdApi.Function<T>,
            timeoutMs: Long,
        ): T {
            val currentClient = client ?: throw IllegalStateException("TDLib Client not initialized")
            return TdReplies.await(function.javaClass.simpleName, timeoutMs) { onReply -> currentClient.send(function, onReply) }
        }

        private companion object {
            const val TAG = "TdLibClient"
        }
    }

/** Turns TDLib's callback replies into a cancellable suspending call with a timeout (#13). */
object TdReplies {
    /**
     * Calls [send] with a reply callback and suspends until the reply: an `Error` becomes [TdLibException] (via
     * [TdLibErrors]), no reply within [timeoutMs] a [TdLibTimeoutException]. If the caller is cancelled first, a
     * late reply is dropped.
     */
    suspend fun <T : TdApi.Object> await(
        name: String,
        timeoutMs: Long,
        send: (onReply: (TdApi.Object) -> Unit) -> Unit,
    ): T =
        withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine<T> { continuation ->
                send { result -> deliver(continuation, result) }
            }
        } ?: throw TdLibTimeoutException(name, timeoutMs)

    private fun <T> deliver(
        continuation: CancellableContinuation<T>,
        result: TdApi.Object,
    ) {
        if (!continuation.isActive) return
        if (result is TdApi.Error) {
            continuation.resumeWithException(TdLibErrors.toException(result))
        } else {
            @Suppress("UNCHECKED_CAST")
            continuation.resume(result as T)
        }
    }
}

/**
 * The single place where TDLib failures are turned into exceptions / logged (#13): `Error` replies become
 * [TdLibException]; exceptions thrown inside TDLib's handler callbacks are logged instead of being lost on
 * TDLib's thread.
 */
object TdLibErrors {
    fun toException(error: TdApi.Error): TdLibException = TdLibException(error.code, error.message)

    fun onHandlerException(
        where: String,
        e: Throwable,
    ) {
        Log.e("TdLibClient", "Exception in TDLib $where", e)
    }
}
