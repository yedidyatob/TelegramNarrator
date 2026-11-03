package io.github.yedidyatob.telegramnarrator.data.demo

import io.github.yedidyatob.telegramnarrator.data.tdlib.TdLibException
import io.github.yedidyatob.telegramnarrator.domain.auth.PhoneNumberNormalizer
import io.github.yedidyatob.telegramnarrator.domain.connection.ConnectionMonitor
import io.github.yedidyatob.telegramnarrator.domain.connection.ConnectionStatus
import io.github.yedidyatob.telegramnarrator.domain.model.AuthState
import io.github.yedidyatob.telegramnarrator.domain.model.Chat
import io.github.yedidyatob.telegramnarrator.domain.model.Message
import io.github.yedidyatob.telegramnarrator.domain.repository.AuthRepository
import io.github.yedidyatob.telegramnarrator.domain.repository.ChatRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

/**
 * Demo login for Google Play review (no spare SIM for a real Telegram code): this number + code opens sample
 * channels from [DemoData] without touching TDLib or the network. Kept in memory only; logging out (or the
 * process dying) leaves demo mode. +999 is an unassigned country code, so no real account can have it.
 */
object DemoLogin {
    const val PHONE = PhoneNumberNormalizer.DEMO_PHONE
    const val CODE = "12345"

    fun isDemoPhone(input: String): Boolean = PhoneNumberNormalizer.sanitize(input) == PHONE

    fun isDemoCode(input: String): Boolean = input.trim() == CODE
}

/** Whether the demo session is active (app-wide singleton, never persisted). */
class DemoMode {
    val active = MutableStateFlow(false)
    val isActive: Boolean get() = active.value
}

/** The real [AuthRepository], except for the [DemoLogin] number and code. */
class DemoAwareAuthRepository(
    private val real: AuthRepository,
    private val demo: DemoMode,
    private val chats: FakeChatRepository,
) : AuthRepository {
    private val demoCodePending = MutableStateFlow(false)

    override val authState: Flow<AuthState> =
        combine(real.authState, demo.active, demoCodePending) { state, active, pending ->
            when {
                active -> AuthState.Authenticated
                pending -> AuthState.WaitCode
                else -> state
            }
        }

    override suspend fun retryInitialization() = real.retryInitialization()

    override suspend fun setPhoneNumber(phoneNumber: String) {
        if (DemoLogin.isDemoPhone(phoneNumber)) {
            demoCodePending.value = true
        } else {
            real.setPhoneNumber(phoneNumber)
        }
    }

    override suspend fun checkAuthenticationCode(code: String) {
        if (!demoCodePending.value) return real.checkAuthenticationCode(code)
        if (!DemoLogin.isDemoCode(code)) throw TdLibException(400, "PHONE_CODE_INVALID")
        chats.reset()
        demo.active.value = true
        demoCodePending.value = false
    }

    override suspend fun checkAuthenticationPassword(password: String) = real.checkAuthenticationPassword(password)

    override suspend fun logOut() {
        if (demo.isActive) demo.active.value = false else real.logOut()
    }
}

/** The real [ChatRepository], or [fake] while demo mode is active. */
@OptIn(ExperimentalCoroutinesApi::class)
class DemoAwareChatRepository(
    private val real: ChatRepository,
    private val fake: FakeChatRepository,
    private val demo: DemoMode,
) : ChatRepository {
    private val current get() = if (demo.isActive) fake else real

    override fun getUnreadChats(): Flow<List<Chat>> = demo.active.flatMapLatest { if (it) fake.getUnreadChats() else real.getUnreadChats() }

    override suspend fun getChat(chatId: Long): Chat? = current.getChat(chatId)

    override suspend fun getChatMessages(
        chatId: Long,
        limit: Int,
    ): List<Message> = current.getChatMessages(chatId, limit)

    override suspend fun markChatAsRead(
        chatId: Long,
        messageIds: List<Long>,
    ) = current.markChatAsRead(chatId, messageIds)

    override suspend fun getVoiceFilePath(fileId: Int): String? = current.getVoiceFilePath(fileId)

    override suspend fun getFilePath(fileId: Int): String? = current.getFilePath(fileId)

    override suspend fun loadChats() = current.loadChats()

    override val markAsReadEnabled: Boolean get() = current.markAsReadEnabled
}

/** Always "connected" in demo mode (TDLib's state is irrelevant there). */
class DemoAwareConnectionMonitor(
    real: ConnectionMonitor,
    demo: DemoMode,
) : ConnectionMonitor {
    override val status: StateFlow<ConnectionStatus> =
        combine(real.status, demo.active) { status, active -> if (active) ConnectionStatus.READY else status }
            .stateIn(CoroutineScope(SupervisorJob() + Dispatchers.Default), SharingStarted.Eagerly, real.status.value)
}
