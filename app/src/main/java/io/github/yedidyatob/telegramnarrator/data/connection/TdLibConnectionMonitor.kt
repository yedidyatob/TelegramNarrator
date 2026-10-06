package io.github.yedidyatob.telegramnarrator.data.connection

import io.github.yedidyatob.telegramnarrator.data.tdlib.TdLibClient
import io.github.yedidyatob.telegramnarrator.domain.connection.ConnectionMonitor
import io.github.yedidyatob.telegramnarrator.domain.connection.ConnectionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.drinkless.tdlib.TdApi
import javax.inject.Inject
import javax.inject.Singleton

/** [ConnectionMonitor] backed by TDLib's UpdateConnectionState. */
@Singleton
class TdLibConnectionMonitor @Inject constructor(client: TdLibClient) : ConnectionMonitor {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val status: StateFlow<ConnectionStatus> = client.connectionState
        .map(::toStatus)
        .stateIn(scope, SharingStarted.Eagerly, toStatus(client.connectionState.value))

    companion object {
        fun toStatus(state: TdApi.ConnectionState?): ConnectionStatus = when (state) {
            is TdApi.ConnectionStateWaitingForNetwork -> ConnectionStatus.WAITING_FOR_NETWORK
            is TdApi.ConnectionStateConnectingToProxy -> ConnectionStatus.CONNECTING_TO_PROXY
            is TdApi.ConnectionStateConnecting -> ConnectionStatus.CONNECTING
            is TdApi.ConnectionStateUpdating -> ConnectionStatus.UPDATING
            // Ready, or nothing reported yet
            else -> ConnectionStatus.READY
        }
    }
}
