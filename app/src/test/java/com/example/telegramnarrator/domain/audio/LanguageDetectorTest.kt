package com.example.telegramnarrator.domain.audio

import org.junit.Assert.assertEquals
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
    fun `empty, numeric and unknown script text defaults to hebrew`() {
        assertEquals(LanguageDetector.DEFAULT, LanguageDetector.detect(""))
        assertEquals(LanguageDetector.DEFAULT, LanguageDetector.detect("12345 !?"))
        assertEquals(LanguageDetector.DEFAULT, LanguageDetector.detect("你好世界"))
    }

    @Test
    fun `mixed text keeps hebrew unless another script dominates`() {
        // Hebrew sentence with an English word
        assertEquals(LanguageDetector.HEBREW, LanguageDetector.detect("נפגשים ב-Zoom מחר בבוקר"))
        // Roughly balanced
        assertEquals(LanguageDetector.HEBREW, LanguageDetector.detect("Hello שלום"))
        // Mostly English with a Hebrew name
        assertEquals(LanguageDetector.ENGLISH, LanguageDetector.detect("The meeting with דני is at five in the afternoon"))
    }

    @Test
    fun `default is hebrew`() {
        assertEquals("he", LanguageDetector.DEFAULT.language)
    }
}
