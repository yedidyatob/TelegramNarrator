package io.github.yedidyatob.telegramnarrator.domain.tts

import io.github.yedidyatob.telegramnarrator.domain.edge.EdgeTts
import io.github.yedidyatob.telegramnarrator.domain.edge.EdgeTtsOptions
import io.github.yedidyatob.telegramnarrator.domain.edge.EdgeVoiceGender
import io.github.yedidyatob.telegramnarrator.ui.viewmodel.nameRes
import io.github.yedidyatob.telegramnarrator.ui.viewmodel.sentenceRes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TestLanguageTest {

    @Test
    fun `default is the device ui language when it is offered`() {
        assertEquals(TestLanguage.ENGLISH, TestLanguage.defaultFor("en"))
        assertEquals(TestLanguage.HEBREW, TestLanguage.defaultFor("he"))
        assertEquals(TestLanguage.ARABIC, TestLanguage.defaultFor("ar"))
        assertEquals(TestLanguage.RUSSIAN, TestLanguage.defaultFor("ru"))
        assertEquals(TestLanguage.SPANISH, TestLanguage.defaultFor("es"))
        assertEquals(TestLanguage.FRENCH, TestLanguage.defaultFor("fr"))
    }

    @Test
    fun `legacy codes and region tags are normalized`() {
        assertEquals(TestLanguage.HEBREW, TestLanguage.defaultFor("iw")) // Java's legacy code for Hebrew
        assertEquals(TestLanguage.HEBREW, TestLanguage.defaultFor("iw_IL"))
        assertEquals(TestLanguage.SPANISH, TestLanguage.defaultFor("es-MX"))
        assertEquals(TestLanguage.FRENCH, TestLanguage.defaultFor("FR"))
    }

    @Test
    fun `other or missing ui languages fall back to english`() {
        assertEquals(TestLanguage.ENGLISH, TestLanguage.defaultFor("de"))
        assertEquals(TestLanguage.ENGLISH, TestLanguage.defaultFor("zh-CN"))
        assertEquals(TestLanguage.ENGLISH, TestLanguage.defaultFor(""))
        assertEquals(TestLanguage.ENGLISH, TestLanguage.defaultFor(null))
    }

    @Test
    fun `from code`() {
        assertEquals(TestLanguage.ARABIC, TestLanguage.fromCode("ar-EG"))
        assertNull(TestLanguage.fromCode("de"))
        assertNull(TestLanguage.fromCode(null))
    }

    @Test
    fun `the list stays small and every language has its own name and sentence`() {
        assertEquals(listOf("en", "he", "ar", "ru", "es", "fr"), TestLanguage.values().map { it.code })
        assertEquals(TestLanguage.values().size, TestLanguage.values().map { it.nameRes }.toSet().size)
        assertEquals(TestLanguage.values().size, TestLanguage.values().map { it.sentenceRes }.toSet().size)
    }

    @Test
    fun `edge test uses the voice it would use for that language`() {
        val male = EdgeTtsOptions(EdgeVoiceGender.MALE)
        val female = EdgeTtsOptions(EdgeVoiceGender.FEMALE)
        assertEquals(EdgeTts.VOICE_AVRI, EdgeTts.voiceFor(male, TestLanguage.HEBREW.code))
        assertEquals(EdgeTts.VOICE_HILA, EdgeTts.voiceFor(female, TestLanguage.HEBREW.code))
        for (language in TestLanguage.values().filter { it != TestLanguage.HEBREW }) {
            assertEquals(EdgeTts.VOICE_ANDREW_MULTILINGUAL, EdgeTts.voiceFor(male, language.code))
            assertEquals(EdgeTts.VOICE_AVA_MULTILINGUAL, EdgeTts.voiceFor(female, language.code))
        }
        val custom = EdgeTtsOptions(EdgeVoiceGender.FEMALE, customVoice = "fr-FR-DeniseNeural")
        assertEquals("fr-FR-DeniseNeural", EdgeTts.voiceFor(custom, TestLanguage.ENGLISH.code))
    }
}
