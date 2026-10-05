package com.example.telegramnarrator.domain.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkAsReadSettingTest {
    @Test fun `default is off in debug builds and on in release builds`() {
        val settings = TtsSettings()
        assertFalse(settings.markAsReadEnabled(isDebugBuild = true))
        assertTrue(settings.markAsReadEnabled(isDebugBuild = false))
    }

    @Test fun `explicit choice wins over the build type`() {
        assertTrue(TtsSettings(markAsReadOverride = true).markAsReadEnabled(isDebugBuild = true))
        assertFalse(TtsSettings(markAsReadOverride = false).markAsReadEnabled(isDebugBuild = false))
    }

    @Test fun `changing other settings keeps the choice`() {
        val settings = TtsVoiceLogic.withRate(TtsSettings(markAsReadOverride = true), 1.5f)
        assertEquals(true, settings.markAsReadOverride)
    }
}
