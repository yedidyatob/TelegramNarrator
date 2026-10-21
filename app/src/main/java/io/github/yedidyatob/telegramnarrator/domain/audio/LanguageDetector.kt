package io.github.yedidyatob.telegramnarrator.domain.audio

import java.util.Locale

/**
 * Tiny offline language guesser based on the Unicode script of the letters in a text (#89: a fallback helper
 * of the per-chat detection, see [io.github.yedidyatob.telegramnarrator.domain.language.ChatLanguagePolicy]).
 *
 * It can only tell scripts apart, not languages that share a script: all Latin text is reported as English.
 * There is no privileged language: the script with the most letters wins, and text without letters of a known
 * script falls back to the device language.
 */
object LanguageDetector {
    val HEBREW: Locale = Locale("he")
    val ENGLISH: Locale = Locale.ENGLISH
    val RUSSIAN: Locale = Locale("ru")
    val ARABIC: Locale = Locale("ar")

    /** The script language of [text], or the device language when it has no letters of a known script. */
    fun detect(text: String, deviceLocale: Locale = Locale.getDefault()): Locale = detectOrNull(text) ?: deviceLocale

    /** Like [detect], but null when the text has no letters of a script we know (so nothing to go on). */
    fun detectOrNull(text: String?): Locale? {
        if (text == null) return null
        var hebrew = 0
        var latin = 0
        var cyrillic = 0
        var arabic = 0

        var i = 0
        while (i < text.length) {
            val codePoint = text.codePointAt(i)
            i += Character.charCount(codePoint)
            if (!Character.isLetter(codePoint)) continue
            when (Character.UnicodeScript.of(codePoint)) {
                Character.UnicodeScript.HEBREW -> hebrew++
                Character.UnicodeScript.LATIN -> latin++
                Character.UnicodeScript.CYRILLIC -> cyrillic++
                Character.UnicodeScript.ARABIC -> arabic++
                else -> {}
            }
        }
        // Ties go to the first in this order (a stable, documented choice; no language is the "default")
        return listOf(HEBREW to hebrew, ARABIC to arabic, RUSSIAN to cyrillic, ENGLISH to latin)
            .filter { it.second > 0 }
            .maxByOrNull { it.second }
            ?.first
    }
}
