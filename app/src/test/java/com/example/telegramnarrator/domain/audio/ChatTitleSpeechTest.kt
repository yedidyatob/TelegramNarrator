package com.example.telegramnarrator.domain.audio

import com.example.telegramnarrator.domain.edge.EdgeTts
import com.example.telegramnarrator.domain.edge.EdgeTtsOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatTitleSpeechTest {

    @Test
    fun `plain titles are spoken as-is`() {
        assertEquals("אבו עלי אקספרס", ChatTitleSpeech.speakableTitle("אבו עלי אקספרס"))
        assertEquals("BBC News", ChatTitleSpeech.speakableTitle("BBC News"))
        assertEquals("Новости 24", ChatTitleSpeech.speakableTitle("Новости 24"))
    }

    @Test
    fun `no New chat or Message from words are added`() {
        val spoken = ChatTitleSpeech.speakableTitle("חדשות")!!
        assertEquals("חדשות", spoken)
        assertFalse(spoken.contains("New chat", ignoreCase = true))
        assertFalse(spoken.contains("שיחה חדשה"))
        assertFalse(spoken.contains("Message from", ignoreCase = true))
    }

    @Test
    fun `emoji and symbol-only parts are stripped`() {
        assertEquals("חדשות בזמן אמת", ChatTitleSpeech.speakableTitle("🔴 חדשות בזמן אמת 🔴"))
        assertEquals("Abu Ali Express אבו עלי", ChatTitleSpeech.speakableTitle("Abu Ali Express | אבו עלי"))
        assertEquals("News Live", ChatTitleSpeech.speakableTitle("⚡️ News • Live ⚡️"))
        assertEquals("Channel 14", ChatTitleSpeech.speakableTitle("**Channel** 14"))
    }

    @Test
    fun `punctuation attached to words is kept`() {
        assertEquals("Dr. Who's Club", ChatTitleSpeech.speakableTitle("Dr. Who's Club"))
        // A lone dash between words is a symbol-only part
        assertEquals("ערוץ 7 חדשות", ChatTitleSpeech.speakableTitle("ערוץ 7 - חדשות"))
    }

    @Test
    fun `titles that become empty are not spoken`() {
        assertNull(ChatTitleSpeech.speakableTitle(null))
        assertNull(ChatTitleSpeech.speakableTitle(""))
        assertNull(ChatTitleSpeech.speakableTitle("   "))
        assertNull(ChatTitleSpeech.speakableTitle("🔥🔥🔥"))
        assertNull(ChatTitleSpeech.speakableTitle("🔥 | ★ | —"))
        assertNull(ChatTitleSpeech.speakableTitle("https://t.me/somechannel"))
    }

    @Test
    fun `the title language comes from the title text itself`() {
        assertEquals(LanguageDetector.HEBREW, LanguageDetector.detect(ChatTitleSpeech.speakableTitle("🔴 חדשות 🔴")!!))
        assertEquals(LanguageDetector.ENGLISH, LanguageDetector.detect(ChatTitleSpeech.speakableTitle("📰 BBC News")!!))
        assertEquals(LanguageDetector.RUSSIAN, LanguageDetector.detect(ChatTitleSpeech.speakableTitle("Новости ⚡")!!))
        assertEquals(LanguageDetector.ARABIC, LanguageDetector.detect(ChatTitleSpeech.speakableTitle("أخبار")!!))
    }

    @Test
    fun `Edge picks the Hebrew voice for Hebrew titles and the multilingual voice otherwise`() {
        val options = EdgeTtsOptions()
        val hebrew = LanguageDetector.detect(ChatTitleSpeech.speakableTitle("חדשות 12")!!).language
        val english = LanguageDetector.detect(ChatTitleSpeech.speakableTitle("BBC News")!!).language
        assertEquals(EdgeTts.hebrewVoice(options.gender), EdgeTts.voiceFor(options, hebrew))
        assertEquals(EdgeTts.multilingualVoice(options.gender), EdgeTts.voiceFor(options, english))
    }

    @Test
    fun `chat opening is the ding followed by the spoken title`() {
        val items = ChatTitleSpeech.chatOpening(7L, rawTitle = "🔴 חדשות", displayTitle = "🔴 חדשות", silent = false)
        assertEquals(
            listOf(
                PlaybackItem.Intro("🔴 חדשות", 7L, silent = false),
                PlaybackItem.ChatTitle("חדשות", 7L, "🔴 חדשות")
            ),
            items
        )
    }

    @Test
    fun `chat opening without a speakable title is just the ding`() {
        val items = ChatTitleSpeech.chatOpening(7L, rawTitle = "🔥🔥", displayTitle = "🔥🔥", silent = false)
        assertEquals(listOf(PlaybackItem.Intro("🔥🔥", 7L, silent = false)), items)
        // Unknown chat (no title from TDLib): the "Chat <id>" placeholder is display-only, never spoken
        val unknown = ChatTitleSpeech.chatOpening(8L, rawTitle = null, displayTitle = "Chat 8", silent = false)
        assertEquals(listOf(PlaybackItem.Intro("Chat 8", 8L, silent = false)), unknown)
    }

    @Test
    fun `silent chats get neither ding nor title`() {
        val items = ChatTitleSpeech.chatOpening(7L, rawTitle = "News", displayTitle = "News", silent = true)
        assertEquals(listOf(PlaybackItem.Intro("News", 7L, silent = true)), items)
        assertTrue((items.single() as PlaybackItem.Intro).silent)
    }
}
