package io.github.yedidyatob.telegramnarrator.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class AvatarInitialsTest {

    @Test
    fun `first letters of the first two words`() {
        assertEquals("DT", AvatarInitials.of("Daily Tech"))
        assertEquals("NE", AvatarInitials.of("news everyday and more"))
        assertEquals("M", AvatarInitials.of("  Mom  "))
    }

    @Test
    fun `hebrew titles`() {
        assertEquals("חי", AvatarInitials.of("חדשות ישראל"))
    }

    @Test
    fun `leading symbols are skipped`() {
        assertEquals("BC", AvatarInitials.of("🔥 Best Channel"))
        assertEquals("AB", AvatarInitials.of("@alpha #beta"))
    }

    @Test
    fun `symbol-only and empty titles`() {
        assertEquals("🔥", AvatarInitials.of("🔥🔥"))
        assertEquals("?", AvatarInitials.of("   "))
    }
}
