package io.github.yedidyatob.telegramnarrator.data.tdlib

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.drinkless.tdlib.TdApi
import java.util.concurrent.CopyOnWriteArrayList

/** In-memory [TdGateway] for repository tests: updates go through the real [UpdateBus]. */
class FakeTdGateway : TdGateway {
    val bus = UpdateBus<TdApi.Object>()
    override val updates: Flow<TdApi.Object> = bus.flow
    override val authorizationState = MutableStateFlow<TdApi.AuthorizationState?>(null)
    override val connectionState = MutableStateFlow<TdApi.ConnectionState?>(null)

    val sent = CopyOnWriteArrayList<TdApi.Function<*>>()
    var recreated = 0

    /** Reply to a request; throw to fail it. Default: Ok. */
    var reply: suspend (TdApi.Function<*>) -> TdApi.Object = { TdApi.Ok() }

    fun publish(update: TdApi.Object) {
        if (update is TdApi.UpdateAuthorizationState) authorizationState.value = update.authorizationState
        bus.publish(update)
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun <T : TdApi.Object> send(function: TdApi.Function<T>, timeoutMs: Long): T {
        sent += function
        val result = reply(function)
        if (result is TdApi.Error) throw TdLibErrors.toException(result)
        return result as T
    }

    override fun recreateClient() {
        recreated++
    }

    inline fun <reified F : TdApi.Function<*>> sentOf(): List<F> = sent.filterIsInstance<F>()
}

object TdFixtures {
    fun chat(
        id: Long,
        title: String = "Chat $id",
        unread: Int = 1,
        mainOrder: Long = id,
        muteFor: Int = 0
    ): TdApi.Chat = TdApi.Chat().apply {
        this.id = id
        this.title = title
        unreadCount = unread
        lastReadInboxMessageId = 0
        notificationSettings = TdApi.ChatNotificationSettings().apply { this.muteFor = muteFor }
        positions = if (mainOrder == 0L) emptyArray() else arrayOf(position(TdApi.ChatListMain(), mainOrder))
    }

    fun position(list: TdApi.ChatList, order: Long) = TdApi.ChatPosition().apply {
        this.list = list
        this.order = order
    }

    fun file(id: Int, completed: Boolean, active: Boolean, path: String = "/voice/$id.oga") = TdApi.File().apply {
        this.id = id
        local = TdApi.LocalFile().apply {
            this.path = if (completed) path else ""
            isDownloadingCompleted = completed
            isDownloadingActive = active
        }
    }
}
