package com.example.telegramnarrator.data.edge

import com.example.telegramnarrator.domain.edge.EdgeTts
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Opt-in smoke test against the real (unofficial) Edge TTS service. Skipped by default / in CI:
 *
 *     EDGE_TTS_LIVE=1 ./gradlew testDebugUnitTest --tests '*EdgeTtsClientLiveTest*'
 *
 * Writes the MP3s to app/build/edge_tts_live/ so they can be listened to.
 */
class EdgeTtsClientLiveTest {

    @Test
    fun `synthesizes hebrew with both hebrew voices`() {
        assumeTrue("set EDGE_TTS_LIVE=1 to run", System.getenv("EDGE_TTS_LIVE") == "1")
        val client = EdgeTtsClient()
        val out = File("build/edge_tts_live").apply { mkdirs() }
        for (voice in listOf(EdgeTts.VOICE_AVRI, EdgeTts.VOICE_HILA)) {
            val bytes = client.synthesize("שלום, זו בדיקה של קול נוירוני. Tel Aviv & Jerusalem <3", voice)
            File(out, "$voice.mp3").writeBytes(bytes)
            assertTrue("$voice returned only ${bytes.size} bytes", bytes.size > 5_000)
            // MPEG audio frame sync (0xFFE) or an ID3 tag
            val sync = (bytes[0].toInt() and 0xFF) == 0xFF && (bytes[1].toInt() and 0xE0) == 0xE0
            val id3 = String(bytes, 0, 3, Charsets.ISO_8859_1) == "ID3"
            assertTrue("$voice did not return MP3", sync || id3)
        }
    }

    @Test
    fun `long text is split into several requests and concatenated`() {
        assumeTrue("set EDGE_TTS_LIVE=1 to run", System.getenv("EDGE_TTS_LIVE") == "1")
        // ~4.5 KB of UTF-8 (Hebrew letters are 2 bytes): more than one 4096-byte request
        val text = "זהו משפט לבדיקה של פיצול טקסט ארוך לכמה בקשות. ".repeat(50)
        assertTrue(EdgeTts.prepareTextChunks(text).size > 1)
        val bytes = EdgeTtsClient().synthesize(text, EdgeTts.VOICE_HILA)
        File("build/edge_tts_live").apply { mkdirs() }.resolve("long.mp3").writeBytes(bytes)
        assertTrue(bytes.size > 50_000)
    }
}
