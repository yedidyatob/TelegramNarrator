package io.github.yedidyatob.telegramnarrator.data.connection

import io.github.yedidyatob.telegramnarrator.domain.connection.ConnectionStatus
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Test

class TdLibConnectionMonitorTest {

    @Test
    fun `maps every TDLib connection state`() {
        assertEquals(ConnectionStatus.WAITING_FOR_NETWORK, TdLibConnectionMonitor.toStatus(TdApi.ConnectionStateWaitingForNetwork()))
        assertEquals(ConnectionStatus.CONNECTING_TO_PROXY, TdLibConnectionMonitor.toStatus(TdApi.ConnectionStateConnectingToProxy()))
        assertEquals(ConnectionStatus.CONNECTING, TdLibConnectionMonitor.toStatus(TdApi.ConnectionStateConnecting()))
        assertEquals(ConnectionStatus.UPDATING, TdLibConnectionMonitor.toStatus(TdApi.ConnectionStateUpdating()))
        assertEquals(ConnectionStatus.READY, TdLibConnectionMonitor.toStatus(TdApi.ConnectionStateReady()))
    }

    @Test
    fun `nothing reported yet counts as ready`() {
        assertEquals(ConnectionStatus.READY, TdLibConnectionMonitor.toStatus(null))
    }
}
