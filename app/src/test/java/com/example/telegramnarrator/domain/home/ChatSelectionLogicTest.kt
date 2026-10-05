package com.example.telegramnarrator.domain.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatSelectionLogicTest {

    @Test
    fun `first sync selects every unread chat`() {
        val next = ChatSelectionLogic.sync(ChatSelectionLogic.State(), listOf(1L, 2L, 3L))
        assertEquals(setOf(1L, 2L, 3L), next.selected)
        assertTrue(next.initialized)
        assertEquals(setOf(1L, 2L, 3L), next.known)
    }

    @Test
    fun `first sync with an empty list still initializes`() {
        val next = ChatSelectionLogic.sync(ChatSelectionLogic.State(), emptyList())
        assertEquals(emptySet<Long>(), next.selected)
        assertTrue(next.initialized)
    }

    @Test
    fun `newly appearing chats are auto-selected`() {
        var state = ChatSelectionLogic.sync(ChatSelectionLogic.State(), listOf(1L, 2L))
        state = ChatSelectionLogic.sync(state, listOf(1L, 2L, 3L))
        assertEquals(setOf(1L, 2L, 3L), state.selected)
    }

    @Test
    fun `disappeared chats are dropped from the selection`() {
        var state = ChatSelectionLogic.sync(ChatSelectionLogic.State(), listOf(1L, 2L, 3L))
        state = state.copy(selected = ChatSelectionLogic.toggle(state.selected, 2L)) // deselect 2
        state = ChatSelectionLogic.sync(state, listOf(1L, 3L)) // 2 gone
        assertEquals(setOf(1L, 3L), state.selected)
        assertEquals(setOf(1L, 3L), state.known)
    }

    @Test
    fun `manual deselect is preserved across sync when the chat stays`() {
        var state = ChatSelectionLogic.sync(ChatSelectionLogic.State(), listOf(1L, 2L))
        state = state.copy(selected = ChatSelectionLogic.toggle(state.selected, 1L))
        state = ChatSelectionLogic.sync(state, listOf(1L, 2L))
        assertEquals(setOf(2L), state.selected)
    }

    @Test
    fun `select all and deselect all`() {
        assertEquals(setOf(1L, 2L), ChatSelectionLogic.selectAll(listOf(1L, 2L)))
        assertEquals(emptySet<Long>(), ChatSelectionLogic.deselectAll())
    }

    @Test
    fun `toggle adds and removes`() {
        assertEquals(setOf(1L), ChatSelectionLogic.toggle(emptySet(), 1L))
        assertEquals(emptySet<Long>(), ChatSelectionLogic.toggle(setOf(1L), 1L))
    }
}
