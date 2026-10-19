package io.github.yedidyatob.telegramnarrator.core.service

import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The unread list once TDLib has finished delivering it. After LoadChats the chats arrive one UpdateNewChat at a
 * time; taking the first non-empty value (as #84 did) gave Auto a partial list. Waits until the list has been
 * unchanged for [quietMs], at most [maxMs], then returns the latest value.
 */
object ChatListSettle {
    const val QUIET_MS = 700L
    const val MAX_MS = 4_000L

    @OptIn(FlowPreview::class)
    suspend fun <T> await(list: Flow<T>, quietMs: Long = QUIET_MS, maxMs: Long = MAX_MS): T =
        withTimeoutOrNull(maxMs) { list.debounce(quietMs).first() } ?: list.first()
}
