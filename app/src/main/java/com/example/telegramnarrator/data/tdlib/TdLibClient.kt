package com.example.telegramnarrator.data.tdlib

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.drinkless.td.libcore.telegram.Client
import org.drinkless.td.libcore.telegram.TdApi
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
    
    // Flow of all TDLib updates
    val updates: Flow<TdApi.Object> = callbackFlow {
        val handler = Client.ResultHandler { `object` ->
            trySend(`object`)
        }
        
        // precise initialization might differ based on the specific pre-built lib, 
        // but generally it's creating a client with a handler.
        client = Client.create(handler, null, null)
        
        awaitClose {
            // Cleanup if needed, though usually we keep client alive
        }
    }

    // Suspending function to send a request and wait for a response
    suspend fun <T : TdApi.Object> send(function: TdApi.Function): T = suspendCoroutine { continuation ->
        client?.send(function) { result ->
            if (result is TdApi.Error) {
                continuation.resumeWithException(RuntimeException("TDLib Error: ${result.code} - ${result.message}"))
            } else {
                @Suppress("UNCHECKED_CAST")
                continuation.resume(result as T)
            }
        } ?: continuation.resumeWithException(IllegalStateException("TDLib Client not initialized"))
    }
}
