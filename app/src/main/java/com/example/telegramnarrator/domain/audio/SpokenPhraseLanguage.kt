package com.example.telegramnarrator.domain.audio

import java.util.Locale

/**
 * Which language the app's own spoken phrases ("New chat: ...", "Message from ...", "Voice note",
 * "End of messages", ...) are said in.
 *
 * The rule does NOT depend on the device locale, only on the content being read:
 *  - the first of the given texts (in priority order) that contains letters decides;
 *  - Hebrew text -> Hebrew phrases; any other language (English, Russian, Arabic ...) -> English phrases;
 *  - if none of the texts has letters there is nothing Hebrew to go on, so English is used.
 *
 * Priority used by the playback service:
 *  - chat announcement ("New chat"): upcoming message texts / captions first, then senders, then the
 *    chat title — so a Latin title with Hebrew messages gets Hebrew "שיחה חדשה", not English "New chat";
 *  - message: the message text (or caption), then the sender name, then the chat title;
 *  - end of messages: the title of the last chat that was read.
 */
object SpokenPhraseLanguage {
    val HEBREW: Locale = LanguageDetector.HEBREW
    val ENGLISH: Locale = LanguageDetector.ENGLISH

    fun choose(vararg texts: String?): Locale {
        for (text in texts) {
            val detected = LanguageDetector.detectOrNull(text) ?: continue
            return if (detected.language == HEBREW.language) HEBREW else ENGLISH
        }
        return ENGLISH
    }

    /**
     * Language for the "New chat: ..." intro: prefer the language of the messages about to be read,
     * not the device locale and not the chat title alone (titles are often Latin brand names).
     */
    fun forIntro(chatTitle: String?, upcomingTexts: List<String?>, upcomingSenders: List<String?> = emptyList()): Locale =
        choose(*(upcomingTexts + upcomingSenders + listOf(chatTitle)).toTypedArray())
}
