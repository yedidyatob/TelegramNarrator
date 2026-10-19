package io.github.yedidyatob.telegramnarrator.core.artwork

import io.github.yedidyatob.telegramnarrator.domain.avatar.AvatarPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArtworkUrisTest {
    private val pkg = "io.github.yedidyatob.telegramnarrator"

    @Test
    fun `chat artwork uri round-trips, including negative channel ids`() {
        assertEquals("content://$pkg.artwork/chat/-1001234", ArtworkUris.forChat(pkg, -1001234))
        assertEquals(-1001234L, ArtworkUris.chatIdOf(listOf("chat", "-1001234")))
    }

    @Test
    fun `anything else is rejected`() {
        assertNull(ArtworkUris.chatIdOf(listOf("chat")))
        assertNull(ArtworkUris.chatIdOf(listOf("chat", "abc")))
        assertNull(ArtworkUris.chatIdOf(listOf("file", "1")))
        assertNull(ArtworkUris.chatIdOf(listOf("chat", "1", "x")))
    }

    @Test
    fun `initials color is stable per chat and covers negative ids`() {
        assertEquals(AvatarPalette.forChat(8), AvatarPalette.forChat(8 + AvatarPalette.gradients.size.toLong()))
        AvatarPalette.forChat(-1001234)
    }
}
