package io.github.yedidyatob.telegramnarrator.domain.sponsored

import io.github.yedidyatob.telegramnarrator.domain.audio.LanguageDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class SponsoredSpeechTest {
    @Test
    fun `cue, then title, then text`() {
        val ad = ad(title = "Daily News", text = "The best channel for news")
        assertEquals("Sponsored. Daily News. The best channel for news", SponsoredSpeech.speechText(ad, "Sponsored"))
    }

    @Test
    fun `the button text is never spoken`() {
        val ad = ad(title = "Daily News", text = "Read more").copy(buttonText = "VIEW CHANNEL")
        assertFalse(SponsoredSpeech.speechText(ad, "Sponsored")!!.contains("VIEW CHANNEL"))
    }

    @Test
    fun `urls, emoji and markdown are cleaned`() {
        val ad = ad(title = "🔥 Crypto **Tips** 🔥", text = "Join now https://t.me/tips 🚀")
        assertEquals("Sponsored. Crypto Tips. Join now", SponsoredSpeech.speechText(ad, "Sponsored"))
    }

    @Test
    fun `channel cleaning rules are not involved`() {
        // Text that channel rules typically drop (the "° תוכן שיווקי" marketing marker) is still spoken for
        // official ads; only the generic cleaning removes the symbol
        val ad = ad(title = "חדשות", text = "° תוכן שיווקי")
        assertEquals("ממומן. חדשות. תוכן שיווקי", SponsoredSpeech.speechText(ad, "ממומן"))
    }

    @Test
    fun `existing punctuation is kept without doubling`() {
        val ad = ad(title = "Big sale!", text = "Only today.")
        assertEquals("Recommended. Big sale! Only today.", SponsoredSpeech.speechText(ad, "Recommended"))
    }

    @Test
    fun `missing title or text is skipped`() {
        assertEquals("Sponsored. Only text", SponsoredSpeech.speechText(ad(title = "", text = "Only text"), "Sponsored"))
        assertEquals("Sponsored. Only title", SponsoredSpeech.speechText(ad(title = "Only title", text = "🎉"), "Sponsored"))
    }

    @Test
    fun `nothing speakable gives no utterance`() {
        assertNull(SponsoredSpeech.speechText(ad(title = "🎉🎉", text = "https://example.com ####"), "Sponsored"))
    }

    @Test
    fun `cue language follows the ad`() {
        assertEquals(LanguageDetector.HEBREW, SponsoredSpeech.cueLocale(ad(title = "ערוץ החדשות", text = "הצטרפו עכשיו")))
        assertEquals(LanguageDetector.ENGLISH, SponsoredSpeech.cueLocale(ad(title = "News", text = "Join now")))
        // Scripts without their own cue strings use the English (default) strings
        assertEquals(LanguageDetector.ENGLISH, SponsoredSpeech.cueLocale(ad(title = "Новости", text = "Подпишитесь")))
        // Mostly-Hebrew with a Latin brand name stays Hebrew
        assertEquals(LanguageDetector.HEBREW, SponsoredSpeech.cueLocale(ad(title = "חדשות CNN", text = "הצטרפו לערוץ שלנו היום")))
    }

    @Test
    fun `cue is Sponsored or Recommended in the ad's language`() {
        val hebrew = ad(title = "ערוץ החדשות", text = "הצטרפו עכשיו")
        val english = ad(title = "News", text = "Join now")
        assertEquals(SponsoredSpeech.Cue.SPONSORED_HE, SponsoredSpeech.cue(hebrew))
        assertEquals(SponsoredSpeech.Cue.SPONSORED_EN, SponsoredSpeech.cue(english))
        assertEquals(SponsoredSpeech.Cue.RECOMMENDED_HE, SponsoredSpeech.cue(hebrew.copy(isRecommended = true)))
        assertEquals(SponsoredSpeech.Cue.RECOMMENDED_EN, SponsoredSpeech.cue(english.copy(isRecommended = true)))
    }

    @Test
    fun `sponsor info joins both fields`() {
        val ad = ad().copy(sponsorInfo = "Sponsor Ltd", additionalInfo = "Ad by X")
        assertEquals("Sponsor Ltd\n\nAd by X", ad.sponsorInfoText)
        assertFalse(ad().hasSponsorInfo)
    }
}
