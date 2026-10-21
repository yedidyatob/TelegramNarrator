package io.github.yedidyatob.telegramnarrator.domain.language

import io.github.yedidyatob.telegramnarrator.domain.edge.EdgeTts
import io.github.yedidyatob.telegramnarrator.domain.edge.EdgeTtsOptions
import io.github.yedidyatob.telegramnarrator.domain.edge.EdgeVoiceGender
import io.github.yedidyatob.telegramnarrator.domain.edge.EdgeVoiceMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EdgeVoiceMapTest {
    private val male = EdgeTtsOptions(gender = EdgeVoiceGender.MALE)
    private val female = EdgeTtsOptions(gender = EdgeVoiceGender.FEMALE)

    @Test
    fun `map covers many languages with valid voice names`() {
        assertTrue(EdgeVoiceMap.VOICES.size >= 60)
        EdgeVoiceMap.VOICES.values.forEach { (m, f) ->
            assertTrue(m != null || f != null)
            listOfNotNull(m, f).forEach { assertTrue(it, EdgeTts.isValidVoiceName(it)) }
        }
    }

    @Test
    fun `voice per language and gender`() {
        assertEquals("he-IL-AvriNeural", EdgeTts.voiceFor(male, "he"))
        assertEquals("he-IL-HilaNeural", EdgeTts.voiceFor(female, "iw"))
        assertTrue(EdgeTts.voiceFor(male, "fr").startsWith("fr-"))
        assertTrue(EdgeTts.voiceFor(female, "ru").startsWith("ru-"))
        assertEquals(EdgeTts.multilingualVoice(EdgeVoiceGender.MALE), EdgeTts.voiceFor(male, "en"))
    }

    @Test
    fun `unknown language uses multilingual, custom voice wins`() {
        assertEquals(EdgeTts.multilingualVoice(EdgeVoiceGender.FEMALE), EdgeTts.voiceFor(female, "xx"))
        assertEquals("fr-FR-HenriNeural", EdgeTts.voiceFor(male.copy(customVoice = "fr-FR-HenriNeural"), "he"))
    }
}
