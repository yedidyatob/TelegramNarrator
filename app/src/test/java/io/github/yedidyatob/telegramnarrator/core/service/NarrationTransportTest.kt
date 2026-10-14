package io.github.yedidyatob.telegramnarrator.core.service

import androidx.media3.common.Player
import io.github.yedidyatob.telegramnarrator.core.service.NarrationTransport.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NarrationTransportTest {

    @Test
    fun `preparing lasts until the narration reports playing`() {
        assertEquals(NarrationPhase.PREPARING, NarrationTransport.phaseOf(false, false, NarrationPhase.PREPARING))
        assertEquals(NarrationPhase.PLAYING, NarrationTransport.phaseOf(true, false, NarrationPhase.PREPARING))
    }

    @Test
    fun `pause and stop map to paused and idle`() {
        assertEquals(NarrationPhase.PAUSED, NarrationTransport.phaseOf(true, true, NarrationPhase.PLAYING))
        assertEquals(NarrationPhase.PLAYING, NarrationTransport.phaseOf(true, false, NarrationPhase.PAUSED))
        assertEquals(NarrationPhase.IDLE, NarrationTransport.phaseOf(false, false, NarrationPhase.PLAYING))
        assertEquals(NarrationPhase.IDLE, NarrationTransport.phaseOf(false, false, NarrationPhase.IDLE))
    }

    @Test
    fun `preparing and playing are foreground states, paused and idle are not`() {
        // Media3 runs the service in the foreground for playWhenReady + BUFFERING / READY
        assertTrue(NarrationTransport.playWhenReady(NarrationPhase.PREPARING))
        assertEquals(Player.STATE_BUFFERING, NarrationTransport.playbackState(NarrationPhase.PREPARING))
        assertTrue(NarrationTransport.playWhenReady(NarrationPhase.PLAYING))
        assertEquals(Player.STATE_READY, NarrationTransport.playbackState(NarrationPhase.PLAYING))
        assertFalse(NarrationTransport.playWhenReady(NarrationPhase.PAUSED))
        assertEquals(Player.STATE_READY, NarrationTransport.playbackState(NarrationPhase.PAUSED))
        assertFalse(NarrationTransport.playWhenReady(NarrationPhase.IDLE))
        assertEquals(Player.STATE_IDLE, NarrationTransport.playbackState(NarrationPhase.IDLE))
    }

    @Test
    fun `next is next message and previous keeps the legacy next-chat mapping`() {
        assertEquals(Action.NEXT_MESSAGE, NarrationTransport.forSeek(Player.COMMAND_SEEK_TO_NEXT))
        assertEquals(Action.NEXT_MESSAGE, NarrationTransport.forSeek(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM))
        assertEquals(Action.NEXT_CHAT, NarrationTransport.forSeek(Player.COMMAND_SEEK_TO_PREVIOUS))
        assertEquals(Action.NEXT_CHAT, NarrationTransport.forSeek(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM))
        assertNull(NarrationTransport.forSeek(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM))
    }
}
