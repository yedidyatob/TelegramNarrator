package io.github.yedidyatob.telegramnarrator.data.tdlib

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import org.drinkless.tdlib.TdApi

/**
 * The TDLib connection as the repositories see it (#13). [TdLibClient] is the real one; tests use a fake.
 */
interface TdGateway {
    /**
     * Every update TDLib sends, in order and without loss, to each collector from the moment it starts collecting.
     * State that a late collector must still see is also exposed as [authorizationState] / [connectionState].
     */
    val updates: Flow<TdApi.Object>

    /** The last UpdateAuthorizationState; null until the first one arrives. */
    val authorizationState: StateFlow<TdApi.AuthorizationState?>

    /** The last UpdateConnectionState; null until the first one arrives. */
    val connectionState: StateFlow<TdApi.ConnectionState?>

    /**
     * Sends [function] and suspends until its reply. Cancellable (a late reply is dropped); fails with
     * [TdLibTimeoutException] after [timeoutMs] and with [TdLibException] when TDLib answers with an `Error`.
     */
    suspend fun <T : TdApi.Object> send(function: TdApi.Function<T>, timeoutMs: Long = DEFAULT_TIMEOUT_MS): T

    /** Creates a new TDLib client after the old one was closed (AuthorizationStateClosed). */
    fun recreateClient()

    companion object {
        /** Long enough for opening the database or a slow network; downloads pass their own timeout. */
        const val DEFAULT_TIMEOUT_MS = 60_000L
    }
}

/** A TDLib `Error` reply. The message format is unchanged from the previous plain RuntimeException. */
open class TdLibException(val code: Int, val tdMessage: String?) :
    RuntimeException("TDLib Error: $code - $tdMessage")

/** No reply within the request's timeout. */
class TdLibTimeoutException(function: String, timeoutMs: Long) :
    TdLibException(TIMEOUT_CODE, "$function: no reply within $timeoutMs ms") {
    companion object {
        /** Like HTTP 408; TDLib itself never uses it. */
        const val TIMEOUT_CODE = 408
    }
}
