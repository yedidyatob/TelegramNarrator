package io.github.yedidyatob.telegramnarrator.core.service

import androidx.media3.session.SessionCommand
import io.github.yedidyatob.telegramnarrator.core.service.SessionControllerPolicy.Access
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The #81 / #82 Android Auto failure: Auto was connected without library commands, so getRoot returned null. */
class PlaybackSessionCommandsTest {
    @Test
    fun `library access grants get root, children and item`() {
        val commands = PlaybackSession.baseCommandsFor(Access.LIBRARY)
        assertTrue(commands.contains(SessionCommand.COMMAND_CODE_LIBRARY_GET_LIBRARY_ROOT))
        assertTrue(commands.contains(SessionCommand.COMMAND_CODE_LIBRARY_GET_CHILDREN))
        assertTrue(commands.contains(SessionCommand.COMMAND_CODE_LIBRARY_GET_ITEM))
    }

    @Test
    fun `transport-only controllers get no library commands`() {
        val commands = PlaybackSession.baseCommandsFor(Access.TRANSPORT)
        assertFalse(commands.contains(SessionCommand.COMMAND_CODE_LIBRARY_GET_LIBRARY_ROOT))
        assertFalse(commands.contains(SessionCommand.COMMAND_CODE_LIBRARY_GET_CHILDREN))
    }
}
