package io.github.yedidyatob.telegramnarrator.domain.audio

/**
 * Where to continue a system-TTS utterance that was paused mid-message.
 *
 * `android.speech.tts.TextToSpeech` has no pause, so pausing stops the utterance. While it speaks, most engines
 * report the start offset of each word (`UtteranceProgressListener.onRangeStart`); on resume the rest of the text
 * is spoken again from a few words before the last reported word, so the listener gets some context back. An
 * engine that never reports ranges gives no position, so it resumes at the start of the sentence that was being
 * spoken, estimated (conservatively, i.e. too early rather than too late) from how long it had been speaking; with
 * no estimate at all it restarts the message.
 *
 * All offsets are UTF-16 indexes into the full text and always land on a word start (never inside a surrogate
 * pair or a word).
 */
object SpeechResume {
    /** Words repeated before the last word the engine reported. */
    const val WORDS_BACK = 3

    /**
     * Characters per second at speech rate 1.0 used to estimate the position without ranges. Real voices speak
     * ~12-16 characters per second; a low value errs towards repeating a little rather than skipping text.
     */
    const val ESTIMATE_CHARS_PER_SECOND = 10f

    /**
     * Resume offset into [text].
     *
     * @param lastRangeStart offset (into [text]) of the last word the engine reported, or null if it never did
     * @param spokenMs how long this message had been audible before the pause (sum over earlier resumes), or
     *   null if the engine never started speaking it
     * @param speechRate the system TTS rate (1.0 = normal) used for the estimate
     */
    fun resumeOffset(text: String, lastRangeStart: Int?, spokenMs: Long?, speechRate: Float): Int {
        if (text.isEmpty()) return 0
        if (lastRangeStart != null) {
            return wordsBefore(text, wordStartAtOrBefore(text, lastRangeStart.coerceIn(0, text.length - 1)), WORDS_BACK)
        }
        if (spokenMs == null || spokenMs <= 0L) return 0
        val rate = if (speechRate.isFinite() && speechRate > 0f) speechRate else 1f
        val estimate = (spokenMs / 1000f * ESTIMATE_CHARS_PER_SECOND * rate).toLong()
            .coerceIn(0L, (text.length - 1).toLong()).toInt()
        return sentenceStart(text, estimate)
    }

    /** The start of the word containing (or, on whitespace, preceding) [index]. */
    fun wordStartAtOrBefore(text: String, index: Int): Int {
        var i = index.coerceIn(0, text.length)
        // On whitespace / past the end: step back to the previous word
        while (i > 0 && (i >= text.length || text[i].isWhitespace())) i--
        while (i > 0 && !text[i - 1].isWhitespace()) i--
        return i
    }

    /** The start of the word [count] words before the word starting at [wordStart] (0 = that word). */
    fun wordsBefore(text: String, wordStart: Int, count: Int): Int {
        var i = wordStart
        repeat(count) {
            if (i == 0) return 0
            var j = i
            while (j > 0 && text[j - 1].isWhitespace()) j--
            while (j > 0 && !text[j - 1].isWhitespace()) j--
            i = j
        }
        return i
    }

    /**
     * The start of the sentence containing [index]: just after the previous `.`, `!`, `?`, `…`, `׃` (Hebrew sof
     * pasuq) or line break that is followed by whitespace, skipping that whitespace; 0 if there is none.
     */
    fun sentenceStart(text: String, index: Int): Int {
        var i = index.coerceIn(0, text.length)
        // A terminator right at / before index belongs to the sentence being spoken: look before it
        while (i > 0) {
            val c = text[i - 1]
            val atBoundary = c == '\n' ||
                (c in SENTENCE_END && (i >= text.length || text[i].isWhitespace()))
            if (atBoundary) break
            i--
        }
        while (i < text.length && text[i].isWhitespace()) i++
        return if (i > index) wordStartAtOrBefore(text, index) else i
    }

    private val SENTENCE_END = setOf('.', '!', '?', '…', '׃', '。')
}
