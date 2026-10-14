package io.github.yedidyatob.telegramnarrator.core.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceCommandsTest {
    @Test
    fun `a control command reaching an idle service stops it instead of starting playback`() {
        for (action in listOf(
            PlaybackService.ACTION_PAUSE, PlaybackService.ACTION_RESUME, PlaybackService.ACTION_SKIP_MSG,
            PlaybackService.ACTION_SKIP_CHAT, PlaybackService.ACTION_PREVIOUS_MSG, PlaybackService.ACTION_STOP, null
        )) {
            assertTrue("$action", ServiceCommands.stopsIdleService(action, foregroundStarted = false))
            assertFalse("$action", ServiceCommands.stopsIdleService(action, foregroundStarted = true))
        }
        assertFalse(ServiceCommands.stopsIdleService(PlaybackService.ACTION_PLAY_ALL, foregroundStarted = false))
    }

    @Test
    fun `wake lock only while playing and not paused`() {
        assertTrue(ServiceCommands.holdsWakeLock(isPlaying = true, isPaused = false))
        assertFalse(ServiceCommands.holdsWakeLock(isPlaying = true, isPaused = true))
        assertFalse(ServiceCommands.holdsWakeLock(isPlaying = false, isPaused = false))
    }
}
