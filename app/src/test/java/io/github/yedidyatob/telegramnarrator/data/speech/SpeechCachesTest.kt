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
    fun `logout wipe deletes Gemini audio but nothing else`() {
        val cache = tmp.root
        listOf("gemini_tts").forEach { File(cache, it).apply { mkdirs() }.resolve("a.mp3").writeText("x") }
        val other = File(cache, "artwork").apply { mkdirs() }.resolve("p.png").apply { writeText("y") }
        assertTrue(SpeechCaches.wipe(cache))
        listOf("gemini_tts").forEach { assertFalse(File(cache, it).exists()) }
        assertTrue(other.exists())
        assertTrue(SpeechCaches.wipe(cache)) // nothing left: still fine
    }
}
