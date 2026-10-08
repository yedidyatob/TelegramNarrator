package io.github.yedidyatob.telegramnarrator.ui.viewmodel

import io.github.yedidyatob.telegramnarrator.domain.audio.NowPlaying
import io.github.yedidyatob.telegramnarrator.domain.model.MessageContentType
import io.github.yedidyatob.telegramnarrator.domain.sponsored.SponsoredAd
import io.github.yedidyatob.telegramnarrator.domain.tts.SpeechProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerUiStateTest {

    private fun message(voice: Boolean = false, engine: SpeechProvider? = null) = NowPlaying(
        chatId = 1,
        chatTitle = "News",
        content = NowPlaying.Content.Message(messageId = 10, sender = "Dana", text = "שלום", contentType = MessageContentType.TEXT, isVoiceNote = voice),
        engine = engine
    )

    private val ad = SponsoredAd(chatId = 1, messageId = 99, title = "Ad", text = "Buy")

    private fun state(
        isPlaying: Boolean = true,
        isPaused: Boolean = false,
        isPreparing: Boolean = false,
        nowPlaying: NowPlaying? = message(),
        playingAd: SponsoredAd? = null,
        selected: SpeechProvider = SpeechProvider.SYSTEM
    ) = PlayerUiState.from(isPlaying, isPaused, isPreparing, nowPlaying, playingAd, selected, status = "Reading")

    @Test
    fun `inactive playback only keeps the status`() {
        val s = state(isPlaying = false, playingAd = ad)
        assertFalse(s.isActive)
        assertNull(s.nowPlaying)
        assertNull(s.sponsoredAd)
        assertNull(s.engine)
        assertEquals("Reading", s.status)
        assertFalse(s.canGoPrevious)
    }

    @Test
    fun `paused is never shown as preparing`() {
        assertTrue(state(isPreparing = true).isPreparing)
        assertFalse(state(isPreparing = true, isPaused = true).isPreparing)
    }

    @Test
    fun `the ad is shown only while the chat's sponsored slot plays`() {
        val sponsored = NowPlaying(chatId = 1, chatTitle = "News", content = NowPlaying.Content.Sponsored)
        assertEquals(ad, state(nowPlaying = sponsored, playingAd = ad).sponsoredAd)
        assertNull(state(nowPlaying = message(), playingAd = ad).sponsoredAd)
        assertNull(state(nowPlaying = sponsored.copy(chatId = 2), playingAd = ad).sponsoredAd)
    }

    @Test
    fun `previous is offered for messages and the ad, not while a chat opens`() {
        assertTrue(state().canGoPrevious)
        assertTrue(state(nowPlaying = NowPlaying(1, "News", content = NowPlaying.Content.Sponsored)).canGoPrevious)
        assertFalse(state(nowPlaying = NowPlaying(1, "News", content = NowPlaying.Content.ChatOpening)).canGoPrevious)
        assertFalse(state(nowPlaying = null).canGoPrevious)
    }

    @Test
    fun `engine indicator follows the engine that speaks`() {
        assertEquals(EngineIndicator(EngineLabel.EDGE), state(selected = SpeechProvider.EDGE).engine)
        assertEquals(EngineIndicator(EngineLabel.GEMINI), state(nowPlaying = message(engine = SpeechProvider.GEMINI), selected = SpeechProvider.GEMINI).engine)
        assertEquals(EngineIndicator(EngineLabel.SYSTEM), state().engine)
    }

    @Test
    fun `system voice standing in for an online engine is a fallback`() {
        assertEquals(
            EngineIndicator(EngineLabel.SYSTEM, isFallback = true),
            state(nowPlaying = message(engine = SpeechProvider.SYSTEM), selected = SpeechProvider.EDGE).engine
        )
    }

    @Test
    fun `voice notes play the original audio`() {
        assertEquals(EngineIndicator(EngineLabel.ORIGINAL_AUDIO), state(nowPlaying = message(voice = true), selected = SpeechProvider.GEMINI).engine)
    }
}
