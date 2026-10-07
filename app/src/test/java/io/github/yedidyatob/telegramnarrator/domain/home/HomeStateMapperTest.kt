package io.github.yedidyatob.telegramnarrator.domain.home

import io.github.yedidyatob.telegramnarrator.domain.connection.ConnectionStatus
import io.github.yedidyatob.telegramnarrator.domain.model.Chat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeStateMapperTest {

    private val chats = listOf(Chat(id = 1, title = "News", unreadCount = 3))

    @Test
    fun `chats are shown whenever there are any, even offline or after an error`() {
        ConnectionStatus.values().forEach { connection ->
            listOf(ChatListLoad.Loading, ChatListLoad.Loaded, ChatListLoad.Failed("x")).forEach { load ->
                assertEquals(HomeContent.Chats(chats), HomeStateMapper.content(chats, load, connection))
            }
        }
    }

    @Test
    fun `no chats while loading is a loading state, not 'no unread chats'`() {
        assertEquals(HomeContent.Loading, HomeStateMapper.content(emptyList(), ChatListLoad.Loading, ConnectionStatus.READY))
    }

    @Test
    fun `loaded without chats is empty`() {
        assertEquals(HomeContent.Empty, HomeStateMapper.content(emptyList(), ChatListLoad.Loaded, ConnectionStatus.READY))
    }

    @Test
    fun `failed load without chats is an error with the detail`() {
        assertEquals(
            HomeContent.Error("FLOOD_WAIT"),
            HomeStateMapper.content(emptyList(), ChatListLoad.Failed("FLOOD_WAIT"), ConnectionStatus.READY)
        )
    }

    @Test
    fun `waiting for network without chats is offline, whatever the load`() {
        listOf(ChatListLoad.Loading, ChatListLoad.Loaded, ChatListLoad.Failed(null)).forEach { load ->
            assertEquals(HomeContent.Offline, HomeStateMapper.content(emptyList(), load, ConnectionStatus.WAITING_FOR_NETWORK))
        }
    }

    @Test
    fun `connecting or updating without chats keeps loading instead of claiming empty`() {
        listOf(ConnectionStatus.CONNECTING, ConnectionStatus.CONNECTING_TO_PROXY, ConnectionStatus.UPDATING).forEach {
            assertEquals(HomeContent.Loading, HomeStateMapper.content(emptyList(), ChatListLoad.Loaded, it))
        }
    }

    @Test
    fun `offline banner over chats, but not over the full offline state`() {
        assertEquals(ConnectionBanner.OFFLINE, HomeStateMapper.banner(ConnectionStatus.WAITING_FOR_NETWORK, HomeContent.Chats(chats)))
        assertNull(HomeStateMapper.banner(ConnectionStatus.WAITING_FOR_NETWORK, HomeContent.Offline))
    }

    @Test
    fun `connecting banner while reconnecting, none when ready`() {
        assertEquals(ConnectionBanner.CONNECTING, HomeStateMapper.banner(ConnectionStatus.CONNECTING, HomeContent.Chats(chats)))
        assertEquals(ConnectionBanner.CONNECTING, HomeStateMapper.banner(ConnectionStatus.UPDATING, HomeContent.Loading))
        assertNull(HomeStateMapper.banner(ConnectionStatus.READY, HomeContent.Chats(chats)))
    }

    @Test
    fun `connection status flags`() {
        assertEquals(listOf(ConnectionStatus.WAITING_FOR_NETWORK), ConnectionStatus.values().filter { it.isOffline })
        assertEquals(
            setOf(ConnectionStatus.CONNECTING, ConnectionStatus.CONNECTING_TO_PROXY, ConnectionStatus.UPDATING),
            ConnectionStatus.values().filter { it.isConnecting }.toSet()
        )
    }
}
