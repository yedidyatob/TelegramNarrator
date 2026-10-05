package com.example.telegramnarrator.domain.edge

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EdgeTtsTest {

    // ---- Sec-MS-GEC / URL ----

    @Test
    fun `sec-ms-gec matches the edge-tts reference implementation`() {
        // Reference value computed with edge_tts.drm.DRM.generate_sec_ms_gec() (edge-tts 7.2.8) at unix time 1791230400
        val expected = "4415FD07FA63201C026AF42B07BDA602B98F42E4D23E5361BD78EE6EBE7FBEF0"
        assertEquals(expected, EdgeTts.secMsGec(1_791_230_400L))
    }

    @Test
    fun `sec-ms-gec is stable within a 5 minute window and changes after it`() {
        val start = 1_791_230_400L // multiple of 300 in Windows file time as well
        assertEquals(EdgeTts.secMsGec(start), EdgeTts.secMsGec(start + 299))
        assertNotEquals(EdgeTts.secMsGec(start), EdgeTts.secMsGec(start + 300))
        assertEquals(64, EdgeTts.secMsGec(start).length)
        assertTrue(EdgeTts.secMsGec(start).all { it in '0'..'9' || it in 'A'..'F' })
    }

    @Test
    fun `wss url carries token, connection id, gec and version`() {
        val url = EdgeTts.buildWssUrl("abc123", "DEADBEEF")
        assertEquals(
            "wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1" +
                "?TrustedClientToken=6A5AA1D4EAFF4E9FB37E23D68491D6F4" +
                "&ConnectionId=abc123&Sec-MS-GEC=DEADBEEF&Sec-MS-GEC-Version=1-${EdgeTts.CHROMIUM_FULL_VERSION}",
            url
        )
    }

    @Test
    fun `connection id and muid have the expected shape`() {
        val id = EdgeTts.newConnectionId()
        assertEquals(32, id.length)
        assertFalse(id.contains('-'))
        val muid = EdgeTts.newMuid()
        assertEquals(32, muid.length)
        assertTrue(muid.all { it in '0'..'9' || it in 'A'..'F' })
    }

    // ---- Cache key ----

    @Test
    fun `cache key is stable for same text and voice`() {
        val a = EdgeTts.cacheKey("שלום עולם", EdgeTts.VOICE_AVRI)
        assertEquals(64, a.length)
        assertEquals(a, EdgeTts.cacheKey("שלום עולם", EdgeTts.VOICE_AVRI))
    }

    @Test
    fun `cache key changes with text or voice`() {
        val base = EdgeTts.cacheKey("hello", EdgeTts.VOICE_AVRI)
        assertNotEquals(base, EdgeTts.cacheKey("hello!", EdgeTts.VOICE_AVRI))
        assertNotEquals(base, EdgeTts.cacheKey("hello", EdgeTts.VOICE_HILA))
    }

    // ---- Voices ----

    @Test
    fun `voice names are validated`() {
        assertTrue(EdgeTts.isValidVoiceName("he-IL-AvriNeural"))
        assertTrue(EdgeTts.isValidVoiceName("en-US-AvaMultilingualNeural"))
        assertTrue(EdgeTts.isValidVoiceName("zh-CN-liaoning-XiaobeiNeural"))
        assertTrue(EdgeTts.isValidVoiceName("sr-Latn-RS-NicholasNeural"))
        assertTrue(EdgeTts.isValidVoiceName("  he-IL-HilaNeural "))
        assertFalse(EdgeTts.isValidVoiceName(null))
        assertFalse(EdgeTts.isValidVoiceName(""))
        assertFalse(EdgeTts.isValidVoiceName("Avri"))
        assertFalse(EdgeTts.isValidVoiceName("he-IL-Avri"))
        assertFalse(EdgeTts.isValidVoiceName("he-IL-Avri'Neural"))
        assertFalse(EdgeTts.isValidVoiceName("he-IL-<x>Neural"))
    }

    @Test
    fun `normalize falls back to Avri`() {
        assertEquals(EdgeTts.VOICE_AVRI, EdgeTts.normalizeVoice("nope"))
        assertEquals(EdgeTts.VOICE_AVRI, EdgeTts.normalizeVoice(null))
        assertEquals(EdgeTts.VOICE_HILA, EdgeTts.normalizeVoice(" he-IL-HilaNeural "))
    }

    @Test
    fun `hebrew voices hand non-hebrew text to a multilingual voice of the same gender`() {
        assertEquals(EdgeTts.VOICE_AVRI, EdgeTts.voiceFor(EdgeTts.VOICE_AVRI, "he"))
        assertEquals(EdgeTts.VOICE_HILA, EdgeTts.voiceFor(EdgeTts.VOICE_HILA, "he"))
        assertEquals(EdgeTts.VOICE_ANDREW_MULTILINGUAL, EdgeTts.voiceFor(EdgeTts.VOICE_AVRI, "en"))
        assertEquals(EdgeTts.VOICE_AVA_MULTILINGUAL, EdgeTts.voiceFor(EdgeTts.VOICE_HILA, "ru"))
        // Other voices are used as-is for every language
        assertEquals("en-GB-SoniaNeural", EdgeTts.voiceFor("en-GB-SoniaNeural", "he"))
        assertEquals(EdgeTts.VOICE_AVA_MULTILINGUAL, EdgeTts.voiceFor(EdgeTts.VOICE_AVA_MULTILINGUAL, "en"))
    }

    // ---- Messages / SSML ----

    @Test
    fun `ssml wraps escaped text with voice and prosody`() {
        val ssml = EdgeTts.buildSsml(EdgeTts.VOICE_HILA, "a &amp; b")
        assertEquals(
            "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='en-US'>" +
                "<voice name='he-IL-HilaNeural'><prosody pitch='+0Hz' rate='+0%' volume='+0%'>a &amp; b" +
                "</prosody></voice></speak>",
            ssml
        )
    }

    @Test
    fun `ssml never injects an invalid voice name`() {
        assertTrue(EdgeTts.buildSsml("x'/><evil", "t").contains("<voice name='he-IL-AvriNeural'>"))
    }

    @Test
    fun `speech config and ssml messages have the protocol headers`() {
        val config = EdgeTts.speechConfigMessage("TS")
        assertTrue(config.startsWith("X-Timestamp:TS\r\nContent-Type:application/json; charset=utf-8\r\nPath:speech.config\r\n\r\n"))
        assertTrue(config.contains("\"outputFormat\":\"audio-24khz-48kbitrate-mono-mp3\""))
        val ssml = EdgeTts.ssmlMessage("rid", "TS", "<speak/>")
        assertEquals("X-RequestId:rid\r\nContent-Type:application/ssml+xml\r\nX-Timestamp:TSZ\r\nPath:ssml\r\n\r\n<speak/>", ssml)
    }

    @Test
    fun `date string is javascript style utc`() {
        assertEquals(
            "Thu Jan 01 1970 00:00:00 GMT+0000 (Coordinated Universal Time)",
            EdgeTts.dateToString(0L)
        )
    }

    @Test
    fun `http date header is parsed for clock skew`() {
        assertEquals(1_791_230_400L, EdgeTts.parseHttpDate("Mon, 05 Oct 2026 20:00:00 GMT"))
        assertNull(EdgeTts.parseHttpDate("garbage"))
        assertNull(EdgeTts.parseHttpDate(null))
    }

    // ---- Text preparation ----

    @Test
    fun `xml special characters are escaped and control characters removed`() {
        assertEquals(listOf("a &amp; &lt;b&gt; c d"), EdgeTts.prepareTextChunks("a & <b> c\u000Bd"))
    }

    @Test
    fun `short text is one chunk and blank text none`() {
        assertEquals(listOf("שלום"), EdgeTts.prepareTextChunks("  שלום  "))
        assertTrue(EdgeTts.prepareTextChunks("   ").isEmpty())
    }

    @Test
    fun `long text is split on spaces within the byte limit`() {
        val text = (1..50).joinToString(" ") { "שלום$it" } // Hebrew letters are 2 bytes in UTF-8
        val chunks = EdgeTts.splitByUtf8Bytes(text, 40)
        assertTrue(chunks.size > 1)
        chunks.forEach { assertTrue(it.toByteArray(Charsets.UTF_8).size <= 40) }
        assertEquals(text.split(" "), chunks.flatMap { it.split(" ") })
    }

    @Test
    fun `split never cuts an xml entity or a surrogate pair`() {
        val withEntity = "aaaaaaa&amp;bbbbbbb"
        val chunks = EdgeTts.splitByUtf8Bytes(withEntity, 10)
        chunks.forEach { chunk ->
            val amp = chunk.lastIndexOf('&')
            if (amp >= 0) assertTrue("entity cut in '$chunk'", chunk.indexOf(';', amp) > amp)
        }
        assertEquals(withEntity, chunks.joinToString(""))

        val emoji = "😀".repeat(10) // 4 bytes each
        val emojiChunks = EdgeTts.splitByUtf8Bytes(emoji, 10)
        emojiChunks.forEach { assertTrue(it.toByteArray(Charsets.UTF_8).size <= 10) }
        assertEquals(emoji, emojiChunks.joinToString(""))
    }

    // ---- Frames ----

    @Test
    fun `text frame headers are parsed`() {
        val headers = EdgeTts.parseTextFrameHeaders("X-RequestId:abc\r\nPath:turn.end\r\n\r\n{}")
        assertEquals("turn.end", headers["Path"])
        assertEquals("abc", headers["X-RequestId"])
    }

    @Test
    fun `binary audio frame is split into headers and payload`() {
        val header = "X-RequestId:abc\r\nContent-Type:audio/mpeg\r\nPath:audio\r\n".toByteArray()
        val payload = byteArrayOf(0xFF.toByte(), 0xF3.toByte(), 1, 2, 3)
        val frame = byteArrayOf((header.size shr 8).toByte(), header.size.toByte()) + header + payload
        val parsed = EdgeTts.parseBinaryFrame(frame)!!
        assertEquals("audio", parsed.path)
        assertEquals("audio/mpeg", parsed.contentType)
        assertArrayEquals(payload, parsed.audio)
    }

    @Test
    fun `malformed binary frames are rejected`() {
        assertNull(EdgeTts.parseBinaryFrame(byteArrayOf(0)))
        assertNull(EdgeTts.parseBinaryFrame(byteArrayOf(0, 50, 1, 2)))
    }
}
