package com.example.telegramnarrator.domain.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpokenPhraseLanguageTest {

    private val he = SpokenPhraseLanguage.HEBREW
    private val en = SpokenPhraseLanguage.ENGLISH

    @Test
    fun `hebrew text gives hebrew phrases`() {
        assertEquals(he, SpokenPhraseLanguage.choose("כותרות השבת"))
        assertEquals(he, SpokenPhraseLanguage.choose("אבו עלי אקספרס"))
    }

    @Test
    fun `english and other languages give english phrases`() {
        assertEquals(en, SpokenPhraseLanguage.choose("Breaking news"))
        assertEquals(en, SpokenPhraseLanguage.choose("Привет мир"))
        assertEquals(en, SpokenPhraseLanguage.choose("مرحبا بالعالم"))
    }

    @Test
    fun `first text with letters decides`() {
        // message text wins over the sender and the chat title
        assertEquals(en, SpokenPhraseLanguage.choose("hello", "דוד", "חדשות"))
        assertEquals(he, SpokenPhraseLanguage.choose("שלום", "John", "News"))
        // no letters in the text (emoji, digits, empty): fall back to the sender, then the chat
        assertEquals(he, SpokenPhraseLanguage.choose("", "דוד", "News"))
        assertEquals(en, SpokenPhraseLanguage.choose("12 :)", null, "News"))
        assertEquals(he, SpokenPhraseLanguage.choose("😀", null, "חדשות"))
    }

    @Test
    fun `nothing to go on gives english not the device or app default`() {
        assertEquals(en, SpokenPhraseLanguage.choose())
        assertEquals(en, SpokenPhraseLanguage.choose("", null, "123"))
    }

    @Test
    fun `mixed text follows the detector`() {
        assertEquals(he, SpokenPhraseLanguage.choose("חדשות מ-BBC היום"))
        assertEquals(en, SpokenPhraseLanguage.choose("Today's top story of the day בקצרה"))
    }

    @Test
    fun `detectOrNull returns null without letters and agrees with detect otherwise`() {
        assertNull(LanguageDetector.detectOrNull(null))
        assertNull(LanguageDetector.detectOrNull(""))
        assertNull(LanguageDetector.detectOrNull("123 😀 !!"))
        assertEquals(LanguageDetector.DEFAULT, LanguageDetector.detect("123 😀"))
        assertEquals(LanguageDetector.ENGLISH, LanguageDetector.detectOrNull("hello"))
        assertEquals(LanguageDetector.HEBREW, LanguageDetector.detectOrNull("שלום"))
    }
}
