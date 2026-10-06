package io.github.yedidyatob.telegramnarrator.ui.viewmodel

import io.github.yedidyatob.telegramnarrator.data.tdlib.TdLibException
import io.github.yedidyatob.telegramnarrator.domain.audio.PlaybackManager
import io.github.yedidyatob.telegramnarrator.domain.connection.ConnectionMonitor
import io.github.yedidyatob.telegramnarrator.domain.connection.ConnectionStatus
import io.github.yedidyatob.telegramnarrator.domain.home.ConnectionBanner
import io.github.yedidyatob.telegramnarrator.domain.home.HomeContent
import io.github.yedidyatob.telegramnarrator.domain.model.AuthState
import io.github.yedidyatob.telegramnarrator.domain.model.Chat
import io.github.yedidyatob.telegramnarrator.domain.model.Message
import io.github.yedidyatob.telegramnarrator.domain.repository.AuthRepository
import io.github.yedidyatob.telegramnarrator.domain.repository.ChatRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private class FakeChats : ChatRepository {
        val chats = MutableStateFlow<List<Chat>>(emptyList())
        var loads = 0
        var failWith: Exception? = null
        var hang: CompletableDeferred<Unit>? = null

        override fun getUnreadChats(): Flow<List<Chat>> = chats
        override suspend fun getChat(chatId: Long): Chat? = chats.value.find { it.id == chatId }
        override suspend fun getChatMessages(chatId: Long, limit: Int): List<Message> = emptyList()
        override suspend fun markChatAsRead(chatId: Long, messageIds: List<Long>) = Unit
        override suspend fun getVoiceFilePath(fileId: Int): String? = null
        override suspend fun loadChats() {
            loads++
            hang?.await()
            failWith?.let { throw it }
        }
        override val markAsReadEnabled: Boolean = true
    }

    private class FakeAuth : AuthRepository {
        override val authState: Flow<AuthState> = emptyFlow()
        override suspend fun retryInitialization() = Unit
        override suspend fun setPhoneNumber(phoneNumber: String) = Unit
        override suspend fun checkAuthenticationCode(code: String) = Unit
        override suspend fun checkAuthenticationPassword(password: String) = Unit
        override suspend fun logOut() = Unit
    }

    private class FakeConnection : ConnectionMonitor {
        override val status = MutableStateFlow(ConnectionStatus.READY)
    }

    private val repo = FakeChats()
    private val connection = FakeConnection()
    private val news = Chat(id = 1, title = "News", unreadCount = 2)

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(): HomeViewModel {
        val vm = HomeViewModel(repo, FakeAuth(), PlaybackManager(), connection)
        // uiState / unreadChats are shared WhileSubscribed, like the screen collecting them
        backgroundScope.launch(dispatcher) { vm.uiState.collect {} }
        return vm
    }

    @Test
    fun `loading until the first load finishes, then empty`() = runTest(dispatcher) {
        repo.hang = CompletableDeferred()
        val vm = viewModel()
        runCurrent()
        assertEquals(HomeContent.Loading, vm.uiState.value.content)
        assertTrue(vm.uiState.value.isRefreshing)
        repo.hang!!.complete(Unit)
        advanceUntilIdle()
        assertEquals(HomeContent.Empty, vm.uiState.value.content)
        assertEquals(false, vm.uiState.value.isRefreshing)
    }

    @Test
    fun `chats are shown once they arrive`() = runTest(dispatcher) {
        val vm = viewModel()
        repo.chats.value = listOf(news)
        advanceUntilIdle()
        assertEquals(HomeContent.Chats(listOf(news)), vm.uiState.value.content)
    }

    @Test
    fun `a TDLib error shows the error state and retry recovers`() = runTest(dispatcher) {
        repo.failWith = TdLibException(500, "Request aborted")
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(HomeContent.Error("Request aborted"), vm.uiState.value.content)

        repo.failWith = null
        repo.hang = CompletableDeferred()
        vm.refresh()
        runCurrent()
        // Try again shows loading again, not the old error
        assertEquals(HomeContent.Loading, vm.uiState.value.content)
        repo.hang!!.complete(Unit)
        advanceUntilIdle()
        assertEquals(HomeContent.Empty, vm.uiState.value.content)
    }

    @Test
    fun `a hanging load times out into the error state`() = runTest(dispatcher) {
        repo.hang = CompletableDeferred()
        val vm = viewModel()
        advanceTimeBy(HomeViewModel.LOAD_TIMEOUT_MS + 1)
        runCurrent()
        assertEquals(HomeContent.Error(null), vm.uiState.value.content)
    }

    @Test
    fun `a failed refresh with chats on screen keeps the list and reports once`() = runTest(dispatcher) {
        val vm = viewModel()
        repo.chats.value = listOf(news)
        advanceUntilIdle()
        val errors = mutableListOf<Unit>()
        backgroundScope.launch(dispatcher) { vm.refreshErrors.collect { errors.add(it) } }
        runCurrent()

        repo.failWith = TdLibException(400, "FLOOD_WAIT_3")
        vm.refresh()
        advanceUntilIdle()
        // advanceUntilIdle doesn't run backgroundScope work (the snackbar collector)
        runCurrent()
        assertEquals(HomeContent.Chats(listOf(news)), vm.uiState.value.content)
        assertEquals(1, errors.size)
    }

    @Test
    fun `offline without chats, then reloads by itself when back online`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        val loadsBefore = repo.loads

        connection.status.value = ConnectionStatus.WAITING_FOR_NETWORK
        advanceUntilIdle()
        assertEquals(HomeContent.Offline, vm.uiState.value.content)
        // The full-screen offline state already says it; no banner on top
        assertNull(vm.uiState.value.banner)

        connection.status.value = ConnectionStatus.READY
        advanceUntilIdle()
        assertEquals(loadsBefore + 1, repo.loads)
        assertEquals(HomeContent.Empty, vm.uiState.value.content)
    }

    @Test
    fun `the offline banner over chats waits out short drops`() = runTest(dispatcher) {
        val vm = viewModel()
        repo.chats.value = listOf(news)
        advanceUntilIdle()

        connection.status.value = ConnectionStatus.WAITING_FOR_NETWORK
        advanceTimeBy(HomeViewModel.BANNER_DELAY_MS - 100)
        runCurrent()
        assertNull(vm.uiState.value.banner)
        advanceTimeBy(200)
        runCurrent()
        assertEquals(ConnectionBanner.OFFLINE, vm.uiState.value.banner)

        connection.status.value = ConnectionStatus.READY
        runCurrent()
        assertNull(vm.uiState.value.banner)
    }
}
