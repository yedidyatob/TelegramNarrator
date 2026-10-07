package io.github.yedidyatob.telegramnarrator.domain.connection

import kotlinx.coroutines.flow.StateFlow

/** TDLib's connection to Telegram (UpdateConnectionState). */
enum class ConnectionStatus {
    /** No network: TDLib waits and reconnects by itself when the network is back. */
    WAITING_FOR_NETWORK,
    CONNECTING_TO_PROXY,
    CONNECTING,
    /** Connected, fetching the updates missed while offline. */
    UPDATING,
    READY;

    val isOffline: Boolean get() = this == WAITING_FOR_NETWORK
    val isConnecting: Boolean get() = this == CONNECTING || this == CONNECTING_TO_PROXY || this == UPDATING
}

interface ConnectionMonitor {
    /** Starts as [ConnectionStatus.READY] until TDLib reports otherwise. */
    val status: StateFlow<ConnectionStatus>
}
