package io.github.yedidyatob.telegramnarrator.domain.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkAsReadSettingTest {
    @Test fun `default is on for both debug and release builds`() {
        val settings = TtsSettings()
        assertTrue(settings.markAsReadEnabled(isDebugBuild = true))
        assertTrue(settings.markAsReadEnabled(isDebugBuild = false))
    }

    @Test fun `release builds always mark as read, even with a stored off value`() {
        // Telegram API ToS 1.4: no "ghost mode" in the published app
        assertTrue(TtsSettings(markAsReadOverride = false).markAsReadEnabled(isDebugBuild = false))
        assertTrue(TtsSettings(markAsReadOverride = true).markAsReadEnabled(isDebugBuild = false))
        assertTrue(TtsSettings(markAsReadOverride = null).markAsReadEnabled(isDebugBuild = false))
    }

    @Test fun `debug builds honor the switch`() {
        assertTrue(TtsSettings(markAsReadOverride = true).markAsReadEnabled(isDebugBuild = true))
        assertFalse(TtsSettings(markAsReadOverride = false).markAsReadEnabled(isDebugBuild = true))
    }

    @Test fun `switch is shown only in debug builds`() {
        assertTrue(TtsSettings.markAsReadSwitchVisible(isDebugBuild = true))
        assertFalse(TtsSettings.markAsReadSwitchVisible(isDebugBuild = false))
    }

    @Test fun `changing other settings keeps the debug choice`() {
        val settings = TtsVoiceLogic.withRate(TtsSettings(markAsReadOverride = false), 1.5f)
        assertEquals(false, settings.markAsReadOverride)
        assertFalse(settings.markAsReadEnabled(isDebugBuild = true))
        assertTrue(settings.markAsReadEnabled(isDebugBuild = false))
    }
}
