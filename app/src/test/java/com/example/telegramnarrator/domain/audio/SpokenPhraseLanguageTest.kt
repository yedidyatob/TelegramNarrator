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

    @Test
    fun `forIntro prefers upcoming Hebrew messages over Latin chat title`() {
        // Device-test bug: channel titled in Latin / English brand, Hebrew posts -> must say שיחה חדשה
        assertEquals(
            he,
            SpokenPhraseLanguage.forIntro(
                chatTitle = "CNN Breaking",
                upcomingTexts = listOf("המצב בצפון מחמיר הבוקר"),
                upcomingSenders = listOf("Editor")
            )
        )
        assertEquals(
            he,
            SpokenPhraseLanguage.forIntro(
                chatTitle = "Abu Ali Express",
                upcomingTexts = listOf("", "", "כותרות השבת"),
                upcomingSenders = emptyList()
            )
        )
    }

    @Test
    fun `forIntro uses English when upcoming content is English even if title is Hebrew`() {
        assertEquals(
            en,
            SpokenPhraseLanguage.forIntro(
                chatTitle = "חדשות העולם",
                upcomingTexts = listOf("Breaking: markets rally overnight"),
                upcomingSenders = listOf("דוד")
            )
        )
    }

    @Test
    fun `forIntro falls back to title then English when messages have no letters`() {
        assertEquals(
            he,
            SpokenPhraseLanguage.forIntro(
                chatTitle = "חדשות",
                upcomingTexts = listOf("", "📷", "12"),
                upcomingSenders = emptyList()
            )
        )
        assertEquals(
            en,
            SpokenPhraseLanguage.forIntro(
                chatTitle = "News",
                upcomingTexts = listOf("", "😀"),
                upcomingSenders = emptyList()
            )
        )
        assertEquals(
            he,
            SpokenPhraseLanguage.forIntro(
                chatTitle = "News",
                upcomingTexts = emptyList(),
                upcomingSenders = listOf("משה")
            )
        )
    }
}
