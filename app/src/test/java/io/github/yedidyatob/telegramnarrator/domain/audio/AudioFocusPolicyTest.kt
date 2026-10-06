package io.github.yedidyatob.telegramnarrator.domain.audio

import io.github.yedidyatob.telegramnarrator.domain.audio.AudioFocusPolicy.Action
import io.github.yedidyatob.telegramnarrator.domain.audio.AudioFocusPolicy.FocusChange
import org.junit.Assert.assertEquals
import org.junit.Test

class AudioFocusPolicyTest {

    private val policy = AudioFocusPolicy()

    @Test fun `transient loss pauses and gain resumes`() {
        assertEquals(Action.PAUSE, policy.onFocusChange(FocusChange.LOSS_TRANSIENT, userPaused = false))
        assertEquals(Action.RESUME, policy.onFocusChange(FocusChange.GAIN, userPaused = true))
    }

    @Test fun `can-duck loss pauses speech and gain resumes`() {
        assertEquals(Action.PAUSE, policy.onFocusChange(FocusChange.LOSS_TRANSIENT_CAN_DUCK, userPaused = false))
        assertEquals(Action.RESUME, policy.onFocusChange(FocusChange.GAIN, userPaused = true))
    }

    @Test fun `permanent loss pauses and never resumes on its own`() {
        assertEquals(Action.PAUSE, policy.onFocusChange(FocusChange.LOSS, userPaused = false))
        assertEquals(Action.NONE, policy.onFocusChange(FocusChange.GAIN, userPaused = true))
    }

    @Test fun `permanent loss after a transient one cancels the pending resume`() {
        policy.onFocusChange(FocusChange.LOSS_TRANSIENT, userPaused = false)
        assertEquals(Action.NONE, policy.onFocusChange(FocusChange.LOSS, userPaused = true))
        assertEquals(Action.NONE, policy.onFocusChange(FocusChange.GAIN, userPaused = true))
    }

    @Test fun `already paused by the user - interruption does nothing and gain does not resume`() {
        assertEquals(Action.NONE, policy.onFocusChange(FocusChange.LOSS_TRANSIENT, userPaused = true))
        assertEquals(Action.NONE, policy.onFocusChange(FocusChange.GAIN, userPaused = true))
    }

    @Test fun `user action during an interruption cancels the automatic resume`() {
        policy.onFocusChange(FocusChange.LOSS_TRANSIENT, userPaused = false)
        policy.onUserAction()
        assertEquals(Action.NONE, policy.onFocusChange(FocusChange.GAIN, userPaused = true))
    }

    @Test fun `user resumed during the interruption - gain does nothing`() {
        policy.onFocusChange(FocusChange.LOSS_TRANSIENT, userPaused = false)
        assertEquals(Action.NONE, policy.onFocusChange(FocusChange.GAIN, userPaused = false))
    }

    @Test fun `becoming noisy cancels a pending resume`() {
        policy.onFocusChange(FocusChange.LOSS_TRANSIENT, userPaused = false)
        policy.onBecomingNoisy()
        assertEquals(Action.NONE, policy.onFocusChange(FocusChange.GAIN, userPaused = true))
    }

    @Test fun `gain without a prior loss does nothing`() {
        assertEquals(Action.NONE, policy.onFocusChange(FocusChange.GAIN, userPaused = false))
    }

    @Test fun `two transient losses in a row resume once`() {
        policy.onFocusChange(FocusChange.LOSS_TRANSIENT, userPaused = false)
        policy.onFocusChange(FocusChange.LOSS_TRANSIENT_CAN_DUCK, userPaused = true)
        assertEquals(Action.RESUME, policy.onFocusChange(FocusChange.GAIN, userPaused = true))
        assertEquals(Action.NONE, policy.onFocusChange(FocusChange.GAIN, userPaused = false))
    }
}
