package io.github.yedidyatob.telegramnarrator.domain.language

import io.github.yedidyatob.telegramnarrator.domain.audio.LanguageDetector

/**
 * The language a chat is read in (#89): detected from several of its messages and stored per chat ID.
 * [language] is an ISO 639 code ("he", "en", "pt"...). [confirmed]: the identifier was confident enough;
 * otherwise it's a fallback (script check / device language) and the chat is re-detected as messages arrive.
 */
data class ChatLanguage(
    val language: String,
    val confidence: Float,
    val samples: Int,
    val confirmed: Boolean,
    /** Messages read since the last detection, for the periodic re-check of a confirmed language. */
    val messagesSinceCheck: Int = 0
)

/** One identification result for one text: language code and confidence (0..1). */
data class LanguageGuess(val language: String, val confidence: Float)

/** Pure rules of per-chat language detection (#89 spec); unit-tested. */
object ChatLanguagePolicy {
    /** Samples per detection: the most recent speakable messages. */
    const val CHANNEL_SAMPLES = 10
    const val PRIVATE_SAMPLES = 5
    /** At least this many identified samples (channels / groups) or [MIN_PRIVATE_SAMPLES] (private chats). */
    const val MIN_SAMPLES = 3
    const val MIN_PRIVATE_SAMPLES = 2
    /** Minimum letters across the samples: very short texts are too ambiguous to confirm. */
    const val MIN_LETTERS = 40
    /** Winning language: at least this share of the weighted vote and this average confidence. */
    const val MIN_SHARE = 0.6f
    const val MIN_CONFIDENCE = 0.7f
    /** A confirmed language is checked again after this many read messages. */
    const val RECHECK_EVERY = 50
    /** "Clearly a different script": at least this many letters, all of one script group. */
    private const val OVERRIDE_MIN_LETTERS = 12

    fun sampleSize(isPrivate: Boolean): Int = if (isPrivate) PRIVATE_SAMPLES else CHANNEL_SAMPLES

    /**
     * ML Kit / BCP-47 code -> the ISO 639 code the app uses. "iw" (old Hebrew code) -> "he", "in" -> "id",
     * "no" -> "nb"; romanized variants ("ru-Latn") and undetermined ("und") -> null.
     */
    fun normalize(code: String?): String? {
        val c = code?.trim()?.lowercase() ?: return null
        if (c.isEmpty() || c == "und" || c.contains("-latn")) return null
        return when (val primary = c.substringBefore('-').substringBefore('_')) {
            "iw" -> "he"
            "in" -> "id"
            "ji" -> "yi"
            "no" -> "nb"
            "tl" -> "fil"
            else -> primary
        }
    }

    /**
     * Combines the guesses for the sampled [texts] (one list per text, best first) into a chat language.
     * Votes are weighted by confidence and text length. Not confident enough, too few samples or too little
     * text: the 4-script check on all samples, then [deviceLanguage], unconfirmed.
     */
    fun aggregate(texts: List<String>, guesses: List<List<LanguageGuess>>, isPrivate: Boolean, deviceLanguage: String): ChatLanguage {
        val letters = texts.sumOf { t -> t.count { it.isLetter() } }
        val weights = HashMap<String, Float>()
        val confidences = HashMap<String, MutableList<Float>>()
        var identified = 0
        texts.zip(guesses).forEach { (text, candidates) ->
            val best = candidates.mapNotNull { g -> normalize(g.language)?.let { LanguageGuess(it, g.confidence) } }
                .maxByOrNull { it.confidence } ?: return@forEach
            identified++
            val weight = best.confidence * text.count { it.isLetter() }.coerceAtLeast(1)
            weights[best.language] = (weights[best.language] ?: 0f) + weight
            confidences.getOrPut(best.language) { mutableListOf() }.add(best.confidence)
        }
        val minSamples = if (isPrivate) MIN_PRIVATE_SAMPLES else MIN_SAMPLES
        val total = weights.values.sum()
        val winner = weights.maxByOrNull { it.value }
        if (winner != null && total > 0f && identified >= minSamples && letters >= MIN_LETTERS) {
            val share = winner.value / total
            val confidence = confidences.getValue(winner.key).average().toFloat()
            if (share >= MIN_SHARE && confidence >= MIN_CONFIDENCE) {
                return ChatLanguage(winner.key, confidence, texts.size, confirmed = true)
            }
        }
        return fallback(texts, deviceLanguage)
    }

    /** Unconfirmed language when the identifier can't decide: script check, else the device language. */
    fun fallback(texts: List<String>, deviceLanguage: String): ChatLanguage {
        val script = LanguageDetector.detectOrNull(texts.joinToString("\n"))?.language?.let { normalize(it) }
        return ChatLanguage(script ?: normalize(deviceLanguage) ?: "en", 0f, texts.size, confirmed = false)
    }

    /**
     * Whether to (re-)detect before reading [newTexts] of a chat whose stored language is [stored]: no stored
     * language; unconfirmed (more text may settle it); confirmed but [RECHECK_EVERY] messages read since the
     * last check; or most of the new messages are clearly in another script.
     */
    fun needsDetection(stored: ChatLanguage?, newTexts: List<String>): Boolean {
        if (stored == null) return true
        if (newTexts.isEmpty()) return false
        if (!stored.confirmed) return true
        if (stored.messagesSinceCheck >= RECHECK_EVERY) return true
        val overrides = newTexts.count { scriptOverride(stored.language, it) != null }
        return overrides * 2 > newTexts.size
    }

    /**
     * The language to speak [text] in: the chat's [chatLanguage], unless the text is clearly in a different
     * script (then that script's language, for this utterance only). No chat language: script, else device.
     */
    fun utteranceLanguage(chatLanguage: String?, text: String, deviceLanguage: String): String {
        if (chatLanguage == null) return LanguageDetector.detectOrNull(text)?.language?.let { normalize(it) }
            ?: normalize(deviceLanguage) ?: "en"
        return scriptOverride(chatLanguage, text) ?: chatLanguage
    }

    /** The script-detected language of [text] when it clearly differs from [language]'s script, else null. */
    fun scriptOverride(language: String, text: String): String? {
        val counts = ScriptCounts.of(text)
        val dominant = counts.dominant() ?: return null
        if (counts.total < OVERRIDE_MIN_LETTERS || counts.share(dominant) < 0.9f) return null
        // Same script: no override (a French chat isn't switched to English by more Latin text). A chat language
        // in another script (Japanese, Greek...) is overridden only by text that is >= 90 % one of the four.
        if (Script.of(language) == dominant) return null
        return dominant.language
    }

    /** "chat=-100123 lang=he conf=0.93 samples=8 confirmed=true" (debug log; never any text). */
    fun logLine(chatId: Long, result: ChatLanguage): String =
        "lang chat=$chatId lang=${result.language} conf=${"%.2f".format(java.util.Locale.ROOT, result.confidence)} " +
            "samples=${result.samples} confirmed=${result.confirmed}"

    // ---- storage format: "lang|confidence|samples|confirmed|sinceCheck" ----

    fun encode(value: ChatLanguage): String =
        listOf(value.language, value.confidence, value.samples, if (value.confirmed) 1 else 0, value.messagesSinceCheck).joinToString("|")

    fun decode(raw: String?): ChatLanguage? {
        val parts = raw?.split('|') ?: return null
        if (parts.size != 5) return null
        return try {
            ChatLanguage(
                language = normalize(parts[0]) ?: return null,
                confidence = parts[1].toFloat(),
                samples = parts[2].toInt(),
                confirmed = parts[3] == "1",
                messagesSinceCheck = parts[4].toInt()
            )
        } catch (e: NumberFormatException) {
            null
        }
    }
}

/** The four scripts the offline fallback knows. */
enum class Script(val language: String) {
    HEBREW("he"), LATIN("en"), CYRILLIC("ru"), ARABIC("ar");

    companion object {
        /** The script [language] is written in, when it is one of the four (null: other / unknown). */
        fun of(language: String): Script? = when (language) {
            "he", "yi" -> HEBREW
            "ar", "fa", "ur", "ps" -> ARABIC
            "ru", "uk", "be", "bg", "sr", "mk", "kk", "ky", "mn", "tg" -> CYRILLIC
            "en", "fr", "es", "de", "it", "pt", "nl", "pl", "cs", "sk", "sl", "hr", "bs", "ro", "hu", "fi", "et",
            "lv", "lt", "sv", "da", "nb", "nn", "is", "ga", "cy", "mt", "sq", "tr", "az", "uz", "id", "ms", "vi",
            "fil", "sw", "zu", "af", "ca", "eu", "gl", "so", "jv", "su", "lb", "eo", "la", "ht", "haw", "mi",
            "sm", "st", "sn", "ny", "ig", "yo", "ha", "xh", "co", "fy", "gd", "ku" -> LATIN
            else -> null
        }
    }
}

/** Letters per known script, plus [other] letters (CJK, Greek, Devanagari...). Shares are of all letters. */
internal class ScriptCounts(private val counts: Map<Script, Int>, private val other: Int) {
    val total: Int get() = counts.values.sum() + other
    fun share(script: Script): Float = if (total == 0) 0f else (counts[script] ?: 0).toFloat() / total
    fun dominant(): Script? = counts.filterValues { it > 0 }.maxByOrNull { it.value }?.key

    companion object {
        fun of(text: String): ScriptCounts {
            val counts = HashMap<Script, Int>()
            var other = 0
            var i = 0
            while (i < text.length) {
                val cp = text.codePointAt(i)
                i += Character.charCount(cp)
                if (!Character.isLetter(cp)) continue
                val script = when (Character.UnicodeScript.of(cp)) {
                    Character.UnicodeScript.HEBREW -> Script.HEBREW
                    Character.UnicodeScript.LATIN -> Script.LATIN
                    Character.UnicodeScript.CYRILLIC -> Script.CYRILLIC
                    Character.UnicodeScript.ARABIC -> Script.ARABIC
                    else -> null
                }
                if (script == null) other++ else counts[script] = (counts[script] ?: 0) + 1
            }
            return ScriptCounts(counts, other)
        }
    }
}
