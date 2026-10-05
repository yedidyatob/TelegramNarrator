package com.example.telegramnarrator.domain.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackSpeedCycleTest {

    @Test
    fun `cycles 1 - 1_25 - 1_5 - 2 - 1`() {
        assertEquals(1.25f, PlaybackSpeedCycle.next(1.0f))
        assertEquals(1.5f, PlaybackSpeedCycle.next(1.25f))
        assertEquals(2.0f, PlaybackSpeedCycle.next(1.5f))
        assertEquals(1.0f, PlaybackSpeedCycle.next(2.0f))
    }

    @Test
    fun `unknown rate jumps back to 1`() {
        assertEquals(1.0f, PlaybackSpeedCycle.next(0.8f))
        assertEquals(1.0f, PlaybackSpeedCycle.next(1.7f))
    }
}
