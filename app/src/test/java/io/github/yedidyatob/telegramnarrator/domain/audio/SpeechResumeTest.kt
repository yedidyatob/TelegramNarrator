package io.github.yedidyatob.telegramnarrator.domain.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeechResumeTest {
    private val text = "one two three four five six seven"

    private fun offsetOf(word: String, inText: String = text) = inText.indexOf(word)

    @Test
    fun `resumes three words before the last reported word`() {
        val at = SpeechResume.resumeOffset(text, lastRangeStart = offsetOf("six"), spokenMs = 9_000, speechRate = 1f)
        assertEquals(offsetOf("three"), at)
        assertEquals("three four five six seven", text.substring(at))
    }

    @Test
    fun `near the start it resumes from the beginning`() {
        assertEquals(0, SpeechResume.resumeOffset(text, offsetOf("two"), 500, 1f))
        assertEquals(0, SpeechResume.resumeOffset(text, 0, 500, 1f))
    }

    @Test
    fun `a range inside a word or on whitespace snaps to a word start`() {
        // Inside "six" -> word "six", three back -> "three"
        assertEquals(offsetOf("three"), SpeechResume.resumeOffset(text, offsetOf("six") + 1, null, 1f))
        // On the space after "five" -> word "five", three back -> "two"
        assertEquals(offsetOf("two"), SpeechResume.resumeOffset(text, offsetOf("six") - 1, null, 1f))
    }

    @Test
    fun `an out of range offset is clamped`() {
        assertEquals(offsetOf("four"), SpeechResume.resumeOffset(text, 10_000, null, 1f))
        assertEquals(0, SpeechResume.resumeOffset(text, -5, null, 1f))
    }

    @Test
    fun `hebrew words and extra whitespace`() {
        val he = "שלום  לכם,\nמה  שלומכם היום חברים"
        val at = SpeechResume.resumeOffset(he, offsetOf("חברים", he), null, 1f)
        assertEquals("מה  שלומכם היום חברים", he.substring(at))
    }

    @Test
    fun `never splits a surrogate pair`() {
        val emoji = "a 😀😀 b c d e"
        val at = SpeechResume.resumeOffset(emoji, emoji.indexOf("d"), null, 1f)
        assertEquals("😀😀 b c d e", emoji.substring(at))
        assertEquals("b c d e", emoji.substring(SpeechResume.resumeOffset(emoji, emoji.indexOf("e"), null, 1f)))
    }

    @Test
    fun `without ranges it resumes at the start of the estimated sentence`() {
        val s = "First sentence here. Second one is a bit longer! Third? Yes."
        // 2.5 s at 10 chars/s -> ~25 chars, inside "Second ..."
        assertEquals(s.indexOf("Second"), SpeechResume.resumeOffset(s, null, 2_500, 1f))
        // Twice the rate -> ~50 chars, inside "Third?"
        assertEquals(s.indexOf("Third"), SpeechResume.resumeOffset(s, null, 2_500, 2f))
        // Estimate past the end is clamped to the last sentence
        assertEquals(s.indexOf("Yes"), SpeechResume.resumeOffset(s, null, 600_000, 1f))
    }

    @Test
    fun `without ranges or speaking time it restarts the message`() {
        assertEquals(0, SpeechResume.resumeOffset(text, null, null, 1f))
        assertEquals(0, SpeechResume.resumeOffset(text, null, 0, 1f))
        assertEquals(0, SpeechResume.resumeOffset("", 3, 1_000, 1f))
    }

    @Test
    fun `a single sentence without ranges restarts the message`() {
        assertEquals(0, SpeechResume.resumeOffset(text, null, 2_000, 1f))
    }

    @Test
    fun `line breaks and hebrew sof pasuq end sentences, decimals do not`() {
        val s = "Price is 3.5 today\nשורה שנייה׃ המשך כאן"
        assertEquals(s.indexOf("Price"), SpeechResume.sentenceStart(s, s.indexOf("today")))
        assertEquals(s.indexOf("שורה"), SpeechResume.sentenceStart(s, s.indexOf("שנייה")))
        assertEquals(s.indexOf("המשך"), SpeechResume.sentenceStart(s, s.indexOf("כאן")))
    }

    @Test
    fun `invalid rate falls back to normal speed`() {
        val s = "First sentence here. Second one is a bit longer!"
        assertEquals(s.indexOf("Second"), SpeechResume.resumeOffset(s, null, 2_500, Float.NaN))
    }
}
