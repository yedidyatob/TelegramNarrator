package io.github.yedidyatob.telegramnarrator.data.speech

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SpeechCachesTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun `logout wipe deletes Edge, Gemini and legacy OpenAI audio but nothing else`() {
        val cache = tmp.root
        listOf("edge_tts", "gemini_tts", "openai_tts").forEach { File(cache, it).apply { mkdirs() }.resolve("a.mp3").writeText("x") }
        val other = File(cache, "artwork").apply { mkdirs() }.resolve("p.png").apply { writeText("y") }
        assertTrue(SpeechCaches.wipe(cache))
        listOf("edge_tts", "gemini_tts", "openai_tts").forEach { assertFalse(File(cache, it).exists()) }
        assertTrue(other.exists())
        assertTrue(SpeechCaches.wipe(cache)) // nothing left: still fine
    }
}
