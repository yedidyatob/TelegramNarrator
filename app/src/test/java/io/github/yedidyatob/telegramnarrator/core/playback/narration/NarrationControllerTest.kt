package io.github.yedidyatob.telegramnarrator.core.playback.narration

import io.github.yedidyatob.telegramnarrator.domain.audio.AudioFocusPolicy
import io.github.yedidyatob.telegramnarrator.domain.audio.PlaybackItem
import io.github.yedidyatob.telegramnarrator.domain.audio.PlaybackManager
import io.github.yedidyatob.telegramnarrator.domain.audio.ReadCheckpointer
import io.github.yedidyatob.telegramnarrator.domain.sponsored.SponsoredAd
import io.github.yedidyatob.telegramnarrator.domain.tts.FallbackReason
import io.github.yedidyatob.telegramnarrator.domain.tts.SpeechProvider
import io.github.yedidyatob.telegramnarrator.domain.tts.SpeechSynthesisOutcome
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class NarrationControllerTest {

    // ---- Fakes ----------------------------------------------------------------------------------

    private class Utterance(
        val text: String,
        val languageSource: String,
        val onStart: () -> Unit,
        val onRange: (Int) -> Unit,
        val onDone: (Boolean) -> Unit
    ) {
        fun rangeAt(word: String) = onRange(text.indexOf(word).also { check(it >= 0) { "$word not in '$text'" } })
    }

    private class FakeSpeech : SpeechPort {
        override var provider = SpeechProvider.SYSTEM
        override var speechRate = 1f
        var settings: Any = "settings-1"
        val utterances = mutableListOf<Utterance>()
        val synthesized = mutableListOf<String>()
        var stops = 0
        var outcome: (String) -> SpeechSynthesisOutcome = { SpeechSynthesisOutcome.Ready(File("/tmp/$it.mp3"), fromCache = true) }
        val last get() = utterances.last()

        override fun settingsKey(): Any = settings
        override suspend fun awaitReady() = Unit
        override fun speak(text: String, languageSource: String, onStart: () -> Unit, onRange: (Int) -> Unit, onDone: (Boolean) -> Unit) {
            utterances += Utterance(text, languageSource, onStart, onRange, onDone)
        }
        override fun stop() { stops++ }
        override fun synthesize(provider: SpeechProvider, text: String): SpeechSynthesisOutcome {
            synthesized += text
            return outcome(text)
        }
        override fun discard(file: File) = Unit
    }

    private class FakeAudio : AudioPort {
        val played = mutableListOf<AudioSource>()
        var onComplete: (() -> Unit)? = null
        var onError: (() -> Unit)? = null
        var paused = false
        var resumes = 0
        override var hasPlayer = false
        override fun play(source: AudioSource, speed: Float, onComplete: () -> Unit, onError: () -> Unit): Boolean {
            played += source
            this.onComplete = onComplete
            this.onError = onError
            hasPlayer = true
            paused = false
            return true
        }
        fun complete() { hasPlayer = false; onComplete!!.invoke() }
        fun fail() { hasPlayer = false; onError!!.invoke() }
        override fun pause(): Boolean { paused = true; return hasPlayer }
        override fun resume(): Boolean { paused = false; resumes++; return hasPlayer }
        override fun release() { hasPlayer = false }
    }

    private class FakeHost : PlaybackHost {
        val states = mutableListOf<Pair<Boolean, Boolean>>()
        var stopped = 0
        var focusRequests = 0
        override fun onItemStarted(item: PlaybackItem) = Unit
        override fun onSponsoredStarted(ad: SponsoredAd, chatName: String) = Unit
        override fun onStateChanged(isPlaying: Boolean, isPaused: Boolean) { states += isPlaying to isPaused }
        override fun onStopped() { stopped++ }
        override fun requestAudioFocus() { focusRequests++ }
        override fun abandonAudioFocus() = Unit
        override fun registerNoisyReceiver() = Unit
        override fun unregisterNoisyReceiver() = Unit
        override fun sponsoredCue(ad: SponsoredAd) = "Sponsored"
    }

    private class FakeSponsored(var ad: SponsoredAd? = null) : SponsoredPort {
        val viewed = mutableListOf<SponsoredAd>()
        override fun prefetch(chatId: Long) = Unit
        override suspend fun adFor(chatId: Long) = ad
        override fun reportViewed(ad: SponsoredAd) { viewed += ad }
    }

    private class FakeFailures : TtsFailurePort {
        val failures = mutableListOf<FallbackReason>()
        override fun onFailure(reason: FallbackReason) { failures += reason }
        override fun onPrefetchFailure(reason: FallbackReason) = Unit
        override fun onNetworkSuccess() = Unit
    }

    private val speech = FakeSpeech()
    private val audio = FakeAudio()
    private val host = FakeHost()
    private val sponsored = FakeSponsored()
    private val failures = FakeFailures()
    private val ui = PlaybackManager()
    private val readBatches = mutableListOf<ReadCheckpointer.Batch>()
    private val markedRead get() = readBatches.flatMap { it.messageIds }

    private val chat = 7L
    private val text1 = "one two three four five six seven eight. Nine ten eleven twelve."
    private val msg1 = PlaybackItem.MessageItem("Ann", text1, 1L, chat)
    private val msg2 = PlaybackItem.MessageItem("Bob", "second message here", 2L, chat)

    private fun TestScope.controller(items: List<PlaybackItem>): NarrationController {
        val dispatcher = StandardTestDispatcher(testScheduler)
        return NarrationController(
            scope = TestScope(dispatcher),
            io = dispatcher,
            buildQueue = { PlaybackRun(items, mapOf(chat to RunChat("Chat", null))) },
            speech = speech,
            audio = audio,
            voiceNotes = { null },
            readMarker = { readBatches += it },
            sponsored = sponsored,
            failures = failures,
            ui = ui,
            host = host,
            clock = { testScheduler.currentTime }
        )
    }

    private fun TestScope.started(vararg items: PlaybackItem): NarrationController =
        controller(items.toList() + PlaybackItem.Outro).also {
            it.playChats(longArrayOf(chat))
            advanceUntilIdle()
        }

    // ---- System TTS pause / resume --------------------------------------------------------------

    @Test
    fun `system tts resumes three words before the last reported word`() = runTest {
        val c = started(msg1, msg2)
        assertEquals(text1, speech.last.text)
        speech.last.onStart()
        speech.last.rangeAt("six")
        advanceUntilIdle()

        c.userPause(); advanceUntilIdle()
        assertEquals(text1.indexOf("three"), c.resumeOffset())
        assertTrue(markedRead.isEmpty()) // interrupted: not read

        c.userResume(); advanceUntilIdle()
        assertEquals("three four five six seven eight. Nine ten eleven twelve.", speech.last.text)
        // Language / voice still chosen from the whole message
        assertEquals(text1, speech.last.languageSource)
        assertNull(c.resumeOffset())

        // Finishing the rest counts as the whole message: marked read, next message starts
        speech.last.onDone(true); advanceUntilIdle()
        assertEquals("second message here", speech.last.text)
        c.stop(); advanceUntilIdle()
        assertEquals(listOf(1L), markedRead)
    }

    @Test
    fun `offsets of a resumed utterance are relative to its rest of the text`() = runTest {
        val c = started(msg1)
        speech.last.onStart(); speech.last.rangeAt("six"); advanceUntilIdle()
        c.userPause(); advanceUntilIdle()
        c.userResume(); advanceUntilIdle()
        // Second pause, later in the text ("eleven" in the rest)
        speech.last.onStart(); speech.last.rangeAt("eleven"); advanceUntilIdle()
        c.userPause(); advanceUntilIdle()
        assertEquals(text1.indexOf("eight."), c.resumeOffset())
        c.userResume(); advanceUntilIdle()
        assertEquals("eight. Nine ten eleven twelve.", speech.last.text)
    }

    @Test
    fun `engine without ranges resumes at the start of the current sentence`() = runTest {
        val c = started(msg1)
        speech.last.onStart(); advanceUntilIdle()
        // ~45 chars at 10 chars/s: inside the second sentence
        testScheduler.advanceTimeBy(4_500); advanceUntilIdle()
        c.userPause(); advanceUntilIdle()
        c.userResume(); advanceUntilIdle()
        assertEquals("Nine ten eleven twelve.", speech.last.text)
    }

    @Test
    fun `paused before the engine started speaking restarts the message`() = runTest {
        val c = started(msg1)
        c.userPause(); advanceUntilIdle()
        c.userResume(); advanceUntilIdle()
        assertEquals(text1, speech.last.text)
    }

    @Test
    fun `voice or rate change while paused restarts the message`() = runTest {
        val c = started(msg1)
        speech.last.onStart(); speech.last.rangeAt("six"); advanceUntilIdle()
        c.userPause(); advanceUntilIdle()
        speech.settings = "settings-2"
        c.userResume(); advanceUntilIdle()
        assertEquals(text1, speech.last.text)
    }

    @Test
    fun `engine change while paused restarts the message with the new engine`() = runTest {
        val c = started(msg1)
        speech.last.onStart(); speech.last.rangeAt("six"); advanceUntilIdle()
        c.userPause(); advanceUntilIdle()
        speech.provider = SpeechProvider.EDGE
        speech.settings = "edge"
        val spokenBefore = speech.utterances.size
        c.userResume(); advanceUntilIdle()
        assertEquals(listOf(text1), speech.synthesized)
        assertEquals(spokenBefore, speech.utterances.size)
        assertTrue(audio.hasPlayer)
    }

    @Test
    fun `skip while paused drops the resume point and marks the message read`() = runTest {
        val c = started(msg1, msg2)
        speech.last.onStart(); speech.last.rangeAt("six"); advanceUntilIdle()
        c.userPause(); advanceUntilIdle()
        c.skipMessage(); advanceUntilIdle()
        assertNull(c.resumeOffset())
        assertEquals(listOf(1L), markedRead)
        c.userResume(); advanceUntilIdle()
        assertEquals("second message here", speech.last.text)
    }

    @Test
    fun `previous while paused replays from the start`() = runTest {
        val c = started(msg2, msg1)
        speech.last.onDone(true); advanceUntilIdle() // msg2 done, msg1 speaking
        speech.last.onStart(); speech.last.rangeAt("six"); advanceUntilIdle()
        c.userPause(); advanceUntilIdle()
        c.previousMessage(); advanceUntilIdle()
        assertNull(c.resumeOffset())
        assertFalse(c.isPaused)
        assertEquals("second message here", speech.last.text)
        speech.last.onDone(true); advanceUntilIdle()
        // The interrupted message comes back whole, not from the old offset
        assertEquals(text1, speech.last.text)
    }

    @Test
    fun `skip chat and stop drop the resume point`() = runTest {
        val c = started(msg1)
        speech.last.onStart(); speech.last.rangeAt("six"); advanceUntilIdle()
        c.userPause(); advanceUntilIdle()
        c.skipChat(); advanceUntilIdle()
        assertNull(c.resumeOffset())

        val c2 = started(msg1)
        speech.last.onStart(); speech.last.rangeAt("six"); advanceUntilIdle()
        c2.userPause(); advanceUntilIdle()
        c2.stop(); advanceUntilIdle()
        assertNull(c2.resumeOffset())
        assertFalse(c2.isPlaying)
    }

    @Test
    fun `transient focus loss pauses and resumes mid-message`() = runTest {
        val c = started(msg1)
        speech.last.onStart(); speech.last.rangeAt("six"); advanceUntilIdle()
        c.onAudioFocusChange(AudioFocusPolicy.FocusChange.LOSS_TRANSIENT); advanceUntilIdle()
        assertTrue(c.isPaused)
        c.onAudioFocusChange(AudioFocusPolicy.FocusChange.GAIN); advanceUntilIdle()
        assertFalse(c.isPaused)
        assertEquals("three four five six seven eight. Nine ten eleven twelve.", speech.last.text)
    }

    @Test
    fun `headphones unplugged pauses, and only the user resumes (mid-message)`() = runTest {
        val c = started(msg1)
        speech.last.onStart(); speech.last.rangeAt("six"); advanceUntilIdle()
        c.onBecomingNoisy(); advanceUntilIdle()
        assertTrue(c.isPaused)
        assertEquals(true to true, host.states.last())
        // Regaining focus after becoming noisy must not resume by itself
        c.onAudioFocusChange(AudioFocusPolicy.FocusChange.GAIN); advanceUntilIdle()
        assertTrue(c.isPaused)
        c.userResume(); advanceUntilIdle()
        assertEquals("three four five six seven eight. Nine ten eleven twelve.", speech.last.text)
    }

    @Test
    fun `late callbacks of a stopped utterance are ignored`() = runTest {
        val c = started(msg1, msg2)
        val first = speech.last
        c.skipMessage(); advanceUntilIdle()
        val second = speech.last
        first.onRange(10); first.onDone(true); advanceUntilIdle()
        // Still on msg2: the stale onDone did not advance the queue a second time
        assertEquals(second, speech.last)
        assertEquals(listOf(1L), markedRead.distinct())
        // A stale range of the old utterance does not move the new message's resume point
        c.userPause(); advanceUntilIdle()
        assertEquals(0, c.resumeOffset())
    }

    // ---- Network voices ---------------------------------------------------------------------------

    @Test
    fun `network voice pauses the file in place and resumes it without re-synthesizing`() = runTest {
        speech.provider = SpeechProvider.EDGE
        val c = started(msg1, msg2)
        assertEquals(listOf(text1, "second message here"), speech.synthesized.distinct())
        val synthesizedBefore = speech.synthesized.size
        c.userPause(); advanceUntilIdle()
        assertTrue(audio.paused)
        c.userResume(); advanceUntilIdle()
        assertEquals(1, audio.resumes)
        // Only the prefetch of the next item may run again, never the current message
        assertTrue(speech.synthesized.drop(synthesizedBefore).none { it == text1 })
        audio.complete(); advanceUntilIdle()
        c.stop(); advanceUntilIdle()
        assertEquals(listOf(1L), markedRead)
    }

    @Test
    fun `network failure falls back to system tts, which also resumes mid-message`() = runTest {
        speech.provider = SpeechProvider.EDGE
        speech.outcome = { SpeechSynthesisOutcome.Fallback(FallbackReason.EDGE_UNAVAILABLE) }
        val c = started(msg1)
        assertEquals(listOf(FallbackReason.EDGE_UNAVAILABLE), failures.failures.take(1))
        assertEquals(text1, speech.last.text)
        speech.last.onStart(); speech.last.rangeAt("six"); advanceUntilIdle()
        c.userPause(); advanceUntilIdle()
        val synthesizedBefore = speech.synthesized.size
        c.userResume(); advanceUntilIdle()
        // Continues with the system voice it was being read with, without retrying the network voice
        assertEquals("three four five six seven eight. Nine ten eleven twelve.", speech.last.text)
        assertEquals(synthesizedBefore, speech.synthesized.size)
    }

    @Test
    fun `unplayable network file falls back to system tts`() = runTest {
        speech.provider = SpeechProvider.GEMINI
        started(msg1)
        audio.fail(); advanceUntilIdle()
        assertEquals(FallbackReason.UNPLAYABLE, failures.failures.last())
        assertEquals(text1, speech.last.text)
    }

    // ---- Queue ------------------------------------------------------------------------------------

    @Test
    fun `chat opening plays the ding, speaks the title, then messages and the end cue`() = runTest {
        val c = started(PlaybackItem.Intro("Chat", chat), PlaybackItem.ChatTitle("Chat", chat), msg1)
        assertEquals(AudioSource.Tone(Ding.CHAT_BOUNDARY), audio.played.single())
        audio.complete(); advanceUntilIdle()
        assertEquals("Chat", speech.last.text)
        speech.last.onDone(true); advanceUntilIdle()
        assertEquals(text1, speech.last.text)
        speech.last.onDone(true); advanceUntilIdle()
        assertEquals(AudioSource.Tone(Ding.END_OF_MESSAGES), audio.played.last())
        audio.complete(); advanceUntilIdle()
        assertFalse(c.isPlaying)
        assertEquals(1, host.stopped)
        assertEquals(listOf(1L), markedRead) // the title is never marked read
    }

    @Test
    fun `sponsored message is read, reported as viewed and its card dismissed`() = runTest {
        val ad = SponsoredAd(chatId = chat, messageId = 99L, title = "Ad title", text = "Buy things")
        sponsored.ad = ad
        started(PlaybackItem.SponsoredSlot(chat, "Chat"))
        assertEquals(ad, ui.sponsoredAd.value)
        assertTrue(speech.last.text.contains("Buy things"))
        speech.last.onDone(true); advanceUntilIdle()
        assertEquals(listOf(ad), sponsored.viewed)
        assertNull(ui.sponsoredAd.value)
        assertTrue(markedRead.isEmpty())
    }

    @Test
    fun `dropped and media-only messages are marked read without speaking`() = runTest {
        val dropped = PlaybackItem.MessageItem("A", "ad text", 5L, chat, dropped = true)
        val c = started(dropped, msg1)
        assertEquals(text1, speech.last.text)
        assertEquals(1, speech.utterances.size)
        c.stop(); advanceUntilIdle()
        assertEquals(listOf(5L), markedRead)
    }
}
