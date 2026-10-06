package io.github.yedidyatob.telegramnarrator.domain.audio

import java.util.Locale

/**
 * Tiny offline language guesser based on the Unicode script of the letters in a text.
 *
 * It can only tell scripts apart, not languages that share a script: all Latin text is
 * reported as English. Hebrew is the app's default, so it wins for empty text, text without
 * letters, unsupported scripts and mixed text, unless another script clearly dominates.
 */
object LanguageDetector {
    val HEBREW: Locale = Locale("he")
    val ENGLISH: Locale = Locale.ENGLISH
    val RUSSIAN: Locale = Locale("ru")
    val ARABIC: Locale = Locale("ar")

    val DEFAULT: Locale = HEBREW

    // A non-Hebrew script has to have at least this many times more letters than Hebrew to win
    private const val DOMINANCE_FACTOR = 3

    fun detect(text: String): Locale = detectOrNull(text) ?: DEFAULT

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

        if (hebrew + latin + cyrillic + arabic == 0) return null
        val candidates = listOf(ENGLISH to latin, RUSSIAN to cyrillic, ARABIC to arabic)
        val (locale, count) = candidates.maxByOrNull { it.second } ?: return DEFAULT
        return if (count > 0 && count >= hebrew * DOMINANCE_FACTOR) locale else DEFAULT
    }
}
