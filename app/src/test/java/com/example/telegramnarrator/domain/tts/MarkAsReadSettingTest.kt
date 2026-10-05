package com.example.telegramnarrator.domain.tts

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

    @Test fun `explicit choice wins over the build type`() {
        assertTrue(TtsSettings(markAsReadOverride = true).markAsReadEnabled(isDebugBuild = true))
        assertFalse(TtsSettings(markAsReadOverride = false).markAsReadEnabled(isDebugBuild = false))
        assertFalse(TtsSettings(markAsReadOverride = false).markAsReadEnabled(isDebugBuild = true))
    }

    @Test fun `changing other settings keeps the choice`() {
        val settings = TtsVoiceLogic.withRate(TtsSettings(markAsReadOverride = true), 1.5f)
        assertEquals(true, settings.markAsReadOverride)
    }
}
