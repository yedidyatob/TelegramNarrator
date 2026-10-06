package io.github.yedidyatob.telegramnarrator.domain.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsVoiceLogicTest {

    private fun voice(
        name: String, tag: String, quality: Int = 400, network: Boolean = false, installed: Boolean = true
    ) = VoiceOption(name, tag, TtsVoiceLogic.normalizeLanguage(tag), quality, network, installed)

    private val heLocal = voice("he-il-local", "he-IL", 400)
    private val heHigh = voice("he-il-high", "he-IL", 500)
    private val heNet = voice("he-il-network", "he-IL", 500, network = true)
    private val heMissing = voice("he-il-missing", "he-IL", 500, installed = false)
    private val enUs = voice("en-us-x-a", "en-US", 300)
    private val enGb = voice("en-gb-x-b", "en-GB", 400)
    private val ru = voice("ru-ru", "ru-RU")
    private val all = listOf(enUs, heLocal, ru, heNet, heHigh, enGb, heMissing)

    @Test
    fun `legacy language codes are normalized`() {
        assertEquals("he", TtsVoiceLogic.normalizeLanguage("iw"))
        assertEquals("he", TtsVoiceLogic.normalizeLanguage("iw_IL"))
        assertEquals("he", TtsVoiceLogic.normalizeLanguage("he-IL"))
        assertEquals("en", TtsVoiceLogic.normalizeLanguage("EN_us"))
        assertEquals("id", TtsVoiceLogic.normalizeLanguage("in"))
    }

    @Test
    fun `rate is clamped and rounded`() {
        assertEquals(0.5f, TtsVoiceLogic.clampRate(0.1f), 0f)
        assertEquals(2.0f, TtsVoiceLogic.clampRate(5f), 0f)
        assertEquals(1.3f, TtsVoiceLogic.clampRate(1.2999f), 0f)
        assertEquals(1.0f, TtsVoiceLogic.clampRate(Float.NaN), 0f)
        assertEquals(1.0f, TtsVoiceLogic.clampRate(Float.POSITIVE_INFINITY), 0f)
    }

    @Test
    fun `languages are hebrew first then device languages then english without duplicates`() {
        assertEquals(listOf("he", "en"), TtsVoiceLogic.languagesToShow(emptyList()))
        assertEquals(listOf("he", "ru", "en"), TtsVoiceLogic.languagesToShow(listOf("ru-RU")))
        assertEquals(listOf("he", "en", "fr"), TtsVoiceLogic.languagesToShow(listOf("iw", "en-US", "fr-FR")))
    }

    @Test
    fun `network and not installed voices are never offered`() {
        val usable = TtsVoiceLogic.usableVoices(all)
        assertTrue(heNet !in usable)
        assertTrue(heMissing !in usable)
        assertEquals(5, usable.size)
    }

    @Test
    fun `voices are grouped by language and sorted by quality then name`() {
        val groups = TtsVoiceLogic.voicesByLanguage(all, listOf("he", "ru", "en", "ar"))
        assertEquals(listOf("he", "ru", "en", "ar"), groups.keys.toList())
        assertEquals(listOf(heHigh, heLocal), groups["he"])
        assertEquals(listOf(enGb, enUs), groups["en"])
        assertEquals(listOf(ru), groups["ru"])
        assertTrue(groups["ar"]!!.isEmpty())
    }

    @Test
    fun `chosen voice is only used for its own language and while usable`() {
        val settings = TtsSettings(voices = mapOf("he" to "he-il-high", "en" to "he-il-high", "ru" to "gone"))
        assertEquals(heHigh, TtsVoiceLogic.chosenVoiceFor(settings, "he", all))
        assertEquals(heHigh, TtsVoiceLogic.chosenVoiceFor(settings, "iw", all)) // legacy code
        assertNull(TtsVoiceLogic.chosenVoiceFor(settings, "en", all)) // a Hebrew voice is not used for English
        assertNull(TtsVoiceLogic.chosenVoiceFor(settings, "ru", all)) // uninstalled -> engine default
        assertNull(TtsVoiceLogic.chosenVoiceFor(settings, "fr", all)) // no choice -> engine default
        // a voice that became network-only is not used
        val netOnly = TtsSettings(voices = mapOf("he" to "he-il-network"))
        assertNull(TtsVoiceLogic.chosenVoiceFor(netOnly, "he", all))
    }

    @Test
    fun `settings updates`() {
        var s = TtsSettings()
        s = TtsVoiceLogic.withVoice(s, "iw", "he-il-high")
        assertEquals(mapOf("he" to "he-il-high"), s.voices)
        s = TtsVoiceLogic.withVoice(s, "en", "en-gb-x-b")
        s = TtsVoiceLogic.withVoice(s, "he", null) // back to the default voice
        assertEquals(mapOf("en" to "en-gb-x-b"), s.voices)
        s = TtsVoiceLogic.withRate(s, 9f)
        assertEquals(2.0f, s.speechRate, 0f)
        // same engine keeps the voices, another engine drops them
        assertEquals(s, TtsVoiceLogic.withEngine(s, null))
        val switched = TtsVoiceLogic.withEngine(s, "com.other.tts")
        assertEquals("com.other.tts", switched.enginePackage)
        assertTrue(switched.voices.isEmpty())
        assertEquals(2.0f, switched.speechRate, 0f)
    }

    @Test
    fun `quality buckets`() {
        assertEquals(VoiceQuality.HIGH, TtsVoiceLogic.qualityOf(heHigh))
        assertEquals(VoiceQuality.HIGH, TtsVoiceLogic.qualityOf(heLocal))
        assertEquals(VoiceQuality.NORMAL, TtsVoiceLogic.qualityOf(enUs))
        assertEquals(VoiceQuality.BASIC, TtsVoiceLogic.qualityOf(voice("x", "en-US", 200)))
    }
}
