package io.github.yedidyatob.telegramnarrator.data.tdlib

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

@Singleton
class TdLibClient @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private var client: Client? = null
    private val _updates = MutableSharedFlow<TdApi.Object>(
        replay = 1,
        extraBufferCapacity = 100,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val updates: Flow<TdApi.Object> = _updates

    // Kept here (the first thing created) rather than in a collector of [updates]: TDLib only reports changes,
    // and a late subscriber would miss the current state
    private val _connectionState = MutableStateFlow<TdApi.ConnectionState?>(null)

    /** The last UpdateConnectionState from TDLib; null until the first one arrives. */
    val connectionState: StateFlow<TdApi.ConnectionState?> = _connectionState.asStateFlow()

    init {
        Client.execute(TdApi.SetLogVerbosityLevel(1))
        recreateClient()
    }

    fun recreateClient() {
        Log.d("TdLibClient", "Recreating TDLib client")
        client = Client.create({ `object` ->
            if (`object` is TdApi.UpdateConnectionState) _connectionState.value = `object`.state
            _updates.tryEmit(`object`)
        }, null, null)
    }

    // Suspending function to send a request and wait for a response
    suspend fun <T : TdApi.Object> send(function: TdApi.Function<T>): T = suspendCoroutine { continuation ->
        val currentClient = client
        if (currentClient == null) {
            continuation.resumeWithException(IllegalStateException("TDLib Client not initialized"))
            return@suspendCoroutine
        }
        
        currentClient.send(function) { result ->
            if (result is TdApi.Error) {
                continuation.resumeWithException(TdLibException(result.code, result.message))
            } else {
                @Suppress("UNCHECKED_CAST")
                continuation.resume(result as T)
            }
        }
    }
}

/** A TDLib `Error` reply. The message format is unchanged from the previous plain RuntimeException. */
class TdLibException(val code: Int, val tdMessage: String?) :
    RuntimeException("TDLib Error: $code - $tdMessage")
