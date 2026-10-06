package io.github.yedidyatob.telegramnarrator.domain.sponsored

import io.github.yedidyatob.telegramnarrator.domain.audio.LanguageDetector
import io.github.yedidyatob.telegramnarrator.domain.audio.MessageCleaner
import io.github.yedidyatob.telegramnarrator.domain.audio.MessageSpeechBody
import io.github.yedidyatob.telegramnarrator.domain.model.MessageContentType
import java.util.Locale

/**
 * What is spoken for a sponsored message: a short cue ("Sponsored" / "ממומן", or "Recommended" / "מומלץ"), in
 * the ad's own language like chat titles, then the cleaned title and text. The button text is never spoken.
 * Channel cleaning rules never apply to ads; the generic [MessageCleaner] (URLs, emoji, markdown) does.
 */
object SponsoredSpeech {
    /** Language of the cue: Hebrew for a Hebrew ad, otherwise English (the default strings). */
    fun cueLocale(ad: SponsoredAd): Locale {
        val detected = LanguageDetector.detectOrNull(ad.title + " " + ad.text)
        return if (detected == LanguageDetector.HEBREW) LanguageDetector.HEBREW else LanguageDetector.ENGLISH
    }

    /** Cleaned [raw] for TTS, or null when nothing speakable is left (blank, emoji / symbols only). */
    fun speakable(raw: String): String? {
        val cleaned = MessageCleaner.clean(raw)
        return MessageSpeechBody.resolve(cleaned, MessageContentType.TEXT)
    }

    /**
     * The utterance for [ad], with [cue] already localized (see [cueLocale]). Null when neither the title nor the
     * text has anything speakable (then only the card shows the ad).
     */
    fun speechText(ad: SponsoredAd, cue: String): String? {
        val parts = listOfNotNull(speakable(ad.title), speakable(ad.text))
        if (parts.isEmpty()) return null
        return (listOf(cue) + parts).fold("") { acc, part ->
            when {
                acc.isEmpty() -> part
                acc.last() in SENTENCE_END -> "$acc $part"
                else -> "$acc. $part"
            }
        }
    }

    private val SENTENCE_END = setOf('.', '!', '?', '…', ':', ';')
}
