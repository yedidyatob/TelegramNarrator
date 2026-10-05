package com.example.telegramnarrator.domain.home

/**
 * Pure selection rules for the unread-chat multi-select UI. No Android dependencies.
 *
 * - First sync: select every unread chat.
 * - Later syncs: drop chats that disappeared, keep existing selections, auto-select newly appearing chats.
 */
object ChatSelectionLogic {

    data class State(
        val selected: Set<Long> = emptySet(),
        val known: Set<Long> = emptySet(),
        val initialized: Boolean = false
    )

    fun sync(state: State, unreadIds: List<Long>): State {
        val ids = unreadIds.toSet()
        if (!state.initialized) {
            return State(selected = ids, known = ids, initialized = true)
        }
        val newlyAppeared = ids - state.known
        return State(
            selected = (state.selected intersect ids) + newlyAppeared,
            known = ids,
            initialized = true
        )
    }

    fun toggle(selected: Set<Long>, chatId: Long): Set<Long> =
        if (chatId in selected) selected - chatId else selected + chatId

    fun selectAll(unreadIds: List<Long>): Set<Long> = unreadIds.toSet()

    fun deselectAll(): Set<Long> = emptySet()
}
