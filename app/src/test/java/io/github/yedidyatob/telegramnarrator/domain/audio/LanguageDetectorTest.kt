package io.github.yedidyatob.telegramnarrator.domain.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LanguageDetectorTest {

    @Test
    fun `hebrew text is hebrew`() {
        assertEquals(LanguageDetector.HEBREW, LanguageDetector.detect("שלום, מה שלומך היום?"))
        // Niqqud and final letters
        assertEquals(LanguageDetector.HEBREW, LanguageDetector.detect("שָׁלוֹם"))
    }

    @Test
    fun `latin text is english`() {
        assertEquals(LanguageDetector.ENGLISH, LanguageDetector.detect("Hello, how are you?"))
    }

    @Test
    fun `cyrillic and arabic are detected`() {
        assertEquals(LanguageDetector.RUSSIAN, LanguageDetector.detect("Привет, как дела"))
        assertEquals(LanguageDetector.ARABIC, LanguageDetector.detect("مرحبا كيف حالك"))
    }

    @Test
    fun `no known script falls back to the device language, never a hard-coded hebrew (#89)`() {
        val device = java.util.Locale.FRENCH
        assertEquals(device, LanguageDetector.detect("", device))
        assertEquals(device, LanguageDetector.detect("12345 !?", device))
        assertEquals(device, LanguageDetector.detect("你好世界", device))
    }

    @Test
    fun `mixed text goes to the script with the most letters`() {
        assertEquals(LanguageDetector.HEBREW, LanguageDetector.detect("נפגשים ב-Zoom מחר בבוקר"))
        assertEquals(LanguageDetector.ENGLISH, LanguageDetector.detect("The meeting with דני is at five in the afternoon"))
        assertEquals(LanguageDetector.ENGLISH, LanguageDetector.detect("Hello there שלום"))
    }

    @Test
    fun `detectOrNull returns null without letters`() {
        assertNull(LanguageDetector.detectOrNull(null))
        assertNull(LanguageDetector.detectOrNull(""))
        assertNull(LanguageDetector.detectOrNull("123 😀 !!"))
        assertEquals(LanguageDetector.ENGLISH, LanguageDetector.detectOrNull("hello"))
        assertEquals(LanguageDetector.HEBREW, LanguageDetector.detectOrNull("שלום"))
    }
}
