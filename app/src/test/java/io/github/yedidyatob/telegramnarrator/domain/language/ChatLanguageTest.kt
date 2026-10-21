package io.github.yedidyatob.telegramnarrator.domain.language

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatLanguageTest {
    private val fr = "Bonjour à tous, voici les nouvelles du jour pour la région"
    private fun g(lang: String, conf: Float = 0.95f) = listOf(LanguageGuess(lang, conf))

    @Test
    fun `normalize maps legacy and romanized codes`() {
        assertEquals("he", ChatLanguagePolicy.normalize("iw"))
        assertEquals("id", ChatLanguagePolicy.normalize("in"))
        assertEquals("nb", ChatLanguagePolicy.normalize("no"))
        assertEquals("pt", ChatLanguagePolicy.normalize("pt-BR"))
        assertNull(ChatLanguagePolicy.normalize("und"))
        assertNull(ChatLanguagePolicy.normalize("ru-Latn"))
        assertNull(ChatLanguagePolicy.normalize(null))
    }

    @Test
    fun `aggregate confirms a confident majority`() {
        val texts = List(5) { fr }
        val r = ChatLanguagePolicy.aggregate(texts, List(5) { g("fr") }, isPrivate = false, deviceLanguage = "en")
        assertEquals("fr", r.language)
        assertTrue(r.confirmed)
    }

    @Test
    fun `aggregate falls back to script then device when unsure`() {
        val texts = List(5) { fr }
        val low = ChatLanguagePolicy.aggregate(texts, List(5) { g("fr", 0.3f) }, false, "de")
        assertFalse(low.confirmed)
        assertEquals("en", low.language) // Latin script fallback
        val none = ChatLanguagePolicy.aggregate(listOf("你好世界"), listOf(emptyList()), false, "de")
        assertEquals("de", none.language)
        assertFalse(none.confirmed)
    }

    @Test
    fun `too few samples are not confirmed`() {
        val r = ChatLanguagePolicy.aggregate(listOf(fr, fr), List(2) { g("fr") }, isPrivate = false, deviceLanguage = "en")
        assertFalse(r.confirmed)
        val p = ChatLanguagePolicy.aggregate(listOf(fr, fr), List(2) { g("fr") }, isPrivate = true, deviceLanguage = "en")
        assertTrue(p.confirmed)
    }

    @Test
    fun `split vote is not confirmed`() {
        val texts = List(6) { fr }
        val guesses = List(3) { g("fr") } + List(3) { g("es") }
        assertFalse(ChatLanguagePolicy.aggregate(texts, guesses, false, "en").confirmed)
    }

    @Test
    fun `needsDetection rules`() {
        val confirmed = ChatLanguage("fr", 0.9f, 10, confirmed = true)
        assertTrue(ChatLanguagePolicy.needsDetection(null, listOf("x")))
        assertFalse(ChatLanguagePolicy.needsDetection(confirmed, listOf(fr)))
        assertTrue(ChatLanguagePolicy.needsDetection(confirmed.copy(confirmed = false), listOf(fr)))
        assertTrue(ChatLanguagePolicy.needsDetection(confirmed.copy(messagesSinceCheck = 50), listOf(fr)))
        assertTrue(ChatLanguagePolicy.needsDetection(confirmed, listOf("שלום לכולם וברוכים הבאים לערוץ")))
    }

    @Test
    fun `utterance language uses chat language with a script override`() {
        assertEquals("fr", ChatLanguagePolicy.utteranceLanguage("fr", "Hello everyone, how are you", "en"))
        assertEquals("he", ChatLanguagePolicy.utteranceLanguage("fr", "שלום לכולם וברוכים הבאים", "en"))
        assertEquals("fr", ChatLanguagePolicy.utteranceLanguage("fr", "שלום", "en")) // too short to override
        assertEquals("ru", ChatLanguagePolicy.utteranceLanguage(null, "Привет", "en"))
        assertEquals("de", ChatLanguagePolicy.utteranceLanguage(null, "123", "de"))
    }

    @Test
    fun `encode and decode round trip, bad input is null`() {
        val v = ChatLanguage("he", 0.91f, 8, confirmed = true, messagesSinceCheck = 3)
        assertEquals(v, ChatLanguagePolicy.decode(ChatLanguagePolicy.encode(v)))
        assertNull(ChatLanguagePolicy.decode("garbage"))
        assertNull(ChatLanguagePolicy.decode("he|x|1|1|0"))
    }

    @Test
    fun `log line has no text`() {
        val line = ChatLanguagePolicy.logLine(-100L, ChatLanguage("fr", 0.9f, 5, true))
        assertTrue(line.contains("lang=fr"))
    }

    private class MemoryStore : ChatLanguageStore {
        val map = HashMap<Long, ChatLanguage>()
        override fun get(chatId: Long) = map[chatId]
        override fun put(chatId: Long, value: ChatLanguage) { map[chatId] = value }
        override fun remove(chatId: Long) { map.remove(chatId) }
        override fun clear() = map.clear()
    }

    @Test
    fun `resolver detects, counts, keeps confirmed on inconclusive recheck, resets`() = runBlocking {
        val store = MemoryStore()
        var answer = "fr"
        var conf = 0.95f
        var calls = 0
        val r = ChatLanguageResolver(store, { calls++; g(answer, conf) }, { "en" })
        assertEquals("fr", r.resolve(1, false, List(5) { fr })?.language)
        assertTrue(store.map[1]!!.confirmed)
        val before = calls
        r.resolve(1, false, List(3) { fr })
        assertEquals(before, calls)
        assertEquals(3, store.map[1]!!.messagesSinceCheck)
        store.map[1] = store.map[1]!!.copy(messagesSinceCheck = 60)
        conf = 0.2f
        assertEquals("fr", r.resolve(1, false, List(5) { fr })?.language)
        assertEquals(0, store.map[1]!!.messagesSinceCheck)
        r.reset(1)
        assertNull(r.languageOf(1))
        assertEquals("en", r.languageCodeOf(1))
        r.resolve(2, false, List(5) { fr }); r.resetAll()
        assertTrue(store.map.isEmpty())
    }

    @Test
    fun `resolver survives identifier failures`() = runBlocking {
        val r = ChatLanguageResolver(MemoryStore(), { error("boom") }, { "de" })
        val out = r.resolve(1, false, List(5) { fr })!!
        assertFalse(out.confirmed)
    }
}
