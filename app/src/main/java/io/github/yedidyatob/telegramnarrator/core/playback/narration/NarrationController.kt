package io.github.yedidyatob.telegramnarrator.core.playback.narration

import io.github.yedidyatob.telegramnarrator.domain.audio.AudioFocusPolicy
import io.github.yedidyatob.telegramnarrator.domain.audio.AudioQueue
import io.github.yedidyatob.telegramnarrator.domain.audio.ChatPlaybackHistory
import io.github.yedidyatob.telegramnarrator.domain.audio.CloudTtsPrefetch
import io.github.yedidyatob.telegramnarrator.domain.audio.MessageCleaner
import io.github.yedidyatob.telegramnarrator.domain.audio.MessageSpeechBody
import io.github.yedidyatob.telegramnarrator.domain.audio.NowPlaying
import io.github.yedidyatob.telegramnarrator.domain.audio.PlaybackItem
import io.github.yedidyatob.telegramnarrator.domain.audio.PlaybackManager
import io.github.yedidyatob.telegramnarrator.domain.audio.PlaybackPlan
import io.github.yedidyatob.telegramnarrator.domain.audio.PlaybackPosition
import io.github.yedidyatob.telegramnarrator.domain.audio.PlaybackReadProgress
import io.github.yedidyatob.telegramnarrator.domain.audio.ReadCheckpointer
import io.github.yedidyatob.telegramnarrator.domain.audio.VoiceNotePlayback
import io.github.yedidyatob.telegramnarrator.domain.sponsored.SponsoredSpeech
import io.github.yedidyatob.telegramnarrator.domain.tts.FallbackReason
import io.github.yedidyatob.telegramnarrator.domain.tts.SpeechProvider
import io.github.yedidyatob.telegramnarrator.domain.tts.SpeechSynthesisOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The reading logic of the player (#12), extracted from PlaybackService: what plays next, speaking with the
 * selected engine and its fallbacks, voice notes, dings, sponsored slots, pause / resume / skip / previous,
 * read checkpointing and audio-focus decisions.
 *
 * **Threading:** every piece of state is confined to [scope]'s single thread (the service uses the main
 * thread). Commands and port callbacks that arrive on other threads (TTS binder threads, I/O coroutines) are
 * re-dispatched onto it, and each callback is checked against a token so a late callback of an item that was
 * skipped / paused / stopped meanwhile cannot advance the queue a second time. Blocking work (network TTS,
 * TDLib) runs on [io] and its result is handled back on the controller thread.
 */
class NarrationController(
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    private val buildQueue: suspend (LongArray) -> PlaybackRun,
    private val speech: SpeechPort,
    private val audio: AudioPort,
    private val voiceNotes: VoiceNotePort,
    private val readMarker: ReadMarkerPort,
    private val sponsored: SponsoredPort,
    private val failures: TtsFailurePort,
    private val ui: PlaybackManager,
    private val host: PlaybackHost,
    private val clock: () -> Long
) {
    companion object {
        /** How long a sponsored-message slot waits for getChatSponsoredMessages before it is passed. */
        const val SPONSORED_FETCH_TIMEOUT_MS = 5_000L
    }

    private val audioQueue = AudioQueue()
    private val readCheckpointer = ReadCheckpointer()
    private val chatHistory = ChatPlaybackHistory()
    private val focusPolicy = AudioFocusPolicy()

    var isPlaying = false
        private set
    var isPaused = false
        private set
    private var currentItem: PlaybackItem? = null
    // True when pause() paused a file in place (so resume continues it instead of replaying the item)
    private var audioPausedInPlace = false
    // Bumped whenever the item being processed is superseded (next item, pause, skip, stop), so that late
    // coroutines of the old item know they must not start any audio
    private var itemGeneration = 0
    // Identify the current system utterance / media file; callbacks of older ones are ignored
    private var speechToken = 0
    private var audioToken = 0
    private var prefetchJob: Job? = null
    private var readFlushJob: Job? = null
    private var plan = PlaybackPlan.EMPTY
    private var runChats: Map<Long, RunChat> = emptyMap()

    /** Snapshot of the queue (tests / diagnostics). */
    internal fun queueSnapshot(): List<PlaybackItem> = audioQueue.snapshot()

    // ---- Commands (any thread) ----------------------------------------------------------------------

    /** Starts reading [chatIds] (or restarts with them while already playing). */
    fun playChats(chatIds: LongArray) = onController {
        try {
            startPlayback(chatIds)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            stopPlayback()
        }
    }

    fun stop() = onController { stopPlayback() }
    fun userPause() = onController { focusPolicy.onUserAction(); pausePlayback() }
    fun userResume() = onController { focusPolicy.onUserAction(); resumePlayback() }
    fun skipMessage() = onController { doSkipMessage() }
    fun skipChat() = onController { doSkipChat() }
    fun previousMessage() = onController { doPreviousMessage() }

    fun onAudioFocusChange(change: AudioFocusPolicy.FocusChange) = onController {
        when (focusPolicy.onFocusChange(change, userPaused = isPaused || !isPlaying)) {
            AudioFocusPolicy.Action.PAUSE -> pausePlayback()
            AudioFocusPolicy.Action.RESUME -> resumePlayback()
            AudioFocusPolicy.Action.NONE -> Unit
        }
    }

    /** Headphones unplugged / Bluetooth disconnected: pause instead of playing from the speaker. */
    fun onBecomingNoisy() = onController {
        focusPolicy.onBecomingNoisy()
        pausePlayback()
    }

    /** Service is being destroyed: send pending read checkpoints now. Call on the controller thread. */
    fun shutdown() {
        cancelPrefetch()
        flushReadCheckpoints()
    }

    private fun onController(block: suspend () -> Unit) {
        scope.launch { block() }
    }

    // ---- Run ----------------------------------------------------------------------------------------

    private suspend fun startPlayback(chatIds: LongArray) {
        audioQueue.clear()
        // A new run starts without the previous run's sponsored card
        ui.clearSponsoredAd()
        // Wait for the TTS engine before grabbing audio focus
        speech.awaitReady()
        val run = withContext(io) { buildQueue(chatIds) }

        plan = PlaybackPlan.from(run.items)
        runChats = run.chats
        chatHistory.onChatStarted()
        audioQueue.addAll(run.items)

        if (!isPlaying) {
            isPlaying = true
            focusPolicy.onUserAction()
            host.requestAudioFocus()
            host.registerNoisyReceiver()
            ui.setPlaying(true)
            host.onStateChanged(isPlaying, isPaused)
            processQueue()
        } else {
            // Already playing: the queue was replaced, stop the current item (it is not marked as read)
            flushReadCheckpoints()
            cancelPrefetch()
            stopAllAudio()
            currentItem = null
            if (isPaused) {
                // Starting a new playback also un-pauses
                isPaused = false
                ui.setPaused(false)
                host.onStateChanged(isPlaying, isPaused)
            }
            processQueue()
        }
    }

    private fun processQueue() {
        if (!isPlaying || isPaused) return

        val item = audioQueue.next()
        currentItem = item
        if (item == null) {
            stopPlayback()
            return
        }

        val generation = ++itemGeneration
        // A new item never inherits the previous item's "Preparing audio…" state
        ui.clearAudioLoading()
        host.onItemStarted(item)

        when (item) {
            is PlaybackItem.Intro -> {
                ui.setPlayingChatId(item.chatId)
                chatHistory.onChatStarted()
                if (!item.silent) publishChatOpening(item.chatId, item.chatName)
                // The previous chat's sponsored card is shown until the next chat starts
                ui.clearSponsoredAd()
                // "Opening" the chat: fetch its sponsored messages now so the slot after them doesn't wait
                if (audioQueue.snapshot().any { it is PlaybackItem.SponsoredSlot && it.chatId == item.chatId }) {
                    sponsored.prefetch(item.chatId)
                }
                if (item.silent) {
                    processQueue()
                } else {
                    // Language-neutral ding (no spoken "New chat"); the title follows as ChatTitle
                    playDing(Ding.CHAT_BOUNDARY, generation) { processQueue() }
                }
            }
            is PlaybackItem.ChatTitle -> {
                ui.setPlayingChatId(item.chatId)
                publishChatOpening(item.chatId, item.chatName)
                // Not a message: nothing is marked as read; only clear currentItem so a racing pause doesn't replay it
                speakText(item.text, generation) {
                    if (currentItem === item) currentItem = null
                }
            }
            is PlaybackItem.MessageItem -> playMessage(item, generation)
            is PlaybackItem.SponsoredSlot -> playSponsoredSlot(item, generation)
            is PlaybackItem.Silence -> processQueue()
            // Distinct end cue (not speech, not the chat-boundary ding)
            is PlaybackItem.Outro -> playDing(Ding.END_OF_MESSAGES, generation) { stopPlayback() }
        }
    }

    private fun playMessage(item: PlaybackItem.MessageItem, generation: Int) {
        if (item.dropped) {
            // Dropped by the channel rules: not read, but handled, so it is marked as read in order
            onMessageFullyPlayed(item)
            processQueue()
            return
        }
        val cleanedText = MessageCleaner.clean(item.text)

        // Voice notes: play the downloaded audio (never speak "Voice note" / the sender). If it cannot be
        // downloaded or played, skip silently (still marked as read) like a photo.
        val fileId = item.voiceNoteFileId
        if (fileId != null) {
            chatHistory.onMessageStarted(item)
            publishMessage(item, isVoiceNote = true)
            val loading = ui.beginAudioLoading()
            scope.launch {
                val path = try {
                    withContext(io) { voiceNotes.voiceFilePath(fileId) }
                } finally {
                    ui.endAudioLoading(loading)
                }
                // Paused / skipped / stopped while the file was downloading
                if (generation != itemGeneration) return@launch
                when (val outcome = VoiceNotePlayback.afterDownload(path)) {
                    is VoiceNotePlayback.Outcome.Play ->
                        playFile(outcome.path, generation, onPlayed = { onMessageFullyPlayed(item) })
                    VoiceNotePlayback.Outcome.SkipSilently -> {
                        onMessageFullyPlayed(item)
                        processQueue()
                    }
                }
            }
            return
        }

        // Media-only (photo/video/sticker/... with no caption) and symbol-only rows are not spoken, but still
        // marked as read when passed
        if (MessageSpeechBody.shouldSkipSilently(cleanedText, item.contentType, item.voiceNoteFileId)) {
            onMessageFullyPlayed(item)
            processQueue()
            return
        }
        val text = MessageSpeechBody.resolve(cleanedText, item.contentType).orEmpty()
        chatHistory.onMessageStarted(item)
        publishMessage(item, isVoiceNote = false)
        // No "Message from X:": the body only (the notification shows the sender)
        speakText(text, generation) { onMessageFullyPlayed(item) }
    }

    /**
     * The chat's official sponsored message (Telegram API ToS 3.3): show the card, speak the localized cue and
     * the cleaned title / text, and report the view once it was read aloud in full. No ad passes silently.
     * Never marked as read.
     */
    private fun playSponsoredSlot(item: PlaybackItem.SponsoredSlot, generation: Int) {
        val loading = ui.beginAudioLoading()
        scope.launch {
            val ad = try {
                withTimeoutOrNull(SPONSORED_FETCH_TIMEOUT_MS) { withContext(io) { sponsored.adFor(item.chatId) } }
            } finally {
                ui.endAudioLoading(loading)
            }
            // Paused / skipped / stopped while fetching (pause re-queues the slot)
            if (generation != itemGeneration || !isPlaying || isPaused) return@launch
            if (ad == null) {
                if (currentItem === item) currentItem = null
                processQueue()
                return@launch
            }
            ui.showSponsoredAd(ad)
            publish(item.chatId, item.chatName, NowPlaying.Content.Sponsored, engine = speech.provider)
            host.onSponsoredStarted(ad, item.chatName)
            val text = SponsoredSpeech.speechText(ad, host.sponsoredCue(ad))
            if (text == null) {
                // Nothing speakable (e.g. emoji-only): the card still shows the ad
                if (currentItem === item) currentItem = null
                processQueue()
                return@launch
            }
            speakText(text, generation) {
                if (currentItem === item) currentItem = null
                // Read aloud in full: counts as a view (once per ad per fetch)
                sponsored.reportViewed(ad)
            }
        }
    }

    // ---- Speaking -----------------------------------------------------------------------------------

    /**
     * Speaks a message body / chat title / ad with the selected engine, then moves on. [onSpoken] runs only if
     * the speech finished. Network engines synthesize a cached MP3 played through the audio slot; a failure
     * tells the user (once per episode) and falls back to the system voice.
     */
    private fun speakText(text: String, generation: Int, onSpoken: () -> Unit) {
        val provider = speech.provider
        if (provider == SpeechProvider.SYSTEM) {
            speakWithSystemTts(text, onSpoken)
            return
        }
        ui.setNowPlayingEngine(provider)
        val loading = ui.beginAudioLoading()
        scope.launch {
            val outcome = try {
                withContext(io) { speech.synthesize(provider, text) }
            } finally {
                ui.endAudioLoading(loading)
            }
            // Paused / skipped / stopped while the audio was being fetched
            if (generation != itemGeneration || !isPlaying || isPaused) return@launch
            when (outcome) {
                is SpeechSynthesisOutcome.Ready -> {
                    if (!outcome.fromCache) failures.onNetworkSuccess()
                    ui.setNowPlayingEngine(provider)
                    // Warm the next few messages into the disk cache while this one plays
                    scheduleCloudPrefetch(generation)
                    playFile(
                        outcome.file.absolutePath, generation, onPlayed = onSpoken, speed = outcome.playbackSpeed,
                        onPlaybackError = {
                            // Corrupt / unplayable file: drop it and read the text with the system voice
                            speech.discard(outcome.file)
                            failures.onFailure(FallbackReason.UNPLAYABLE)
                            speakWithSystemTts(text, onSpoken)
                        }
                    )
                }
                SpeechSynthesisOutcome.UseSystem -> speakWithSystemTts(text, onSpoken)
                is SpeechSynthesisOutcome.Fallback -> {
                    failures.onFailure(outcome.reason)
                    speakWithSystemTts(text, onSpoken)
                }
            }
        }
    }

    private fun speakWithSystemTts(text: String, onSpoken: () -> Unit) {
        ui.setNowPlayingEngine(SpeechProvider.SYSTEM)
        // Until the engine starts speaking the bar shows "Preparing audio…"
        val loading = ui.beginAudioLoading()
        val token = ++speechToken
        speech.speak(
            text,
            onStart = { onController { ui.endAudioLoading(loading) } },
            onDone = { completed ->
                onController {
                    if (token != speechToken) return@onController
                    ui.endAudioLoading(loading)
                    if (completed) onSpoken()
                    processQueue()
                }
            }
        )
    }

    /**
     * While network-voice audio plays, synthesize the next [CloudTtsPrefetch.COUNT] speakable items into the
     * disk cache. A failed prefetch only tells the user when the network is down (once per episode).
     */
    private fun scheduleCloudPrefetch(generation: Int) {
        cancelPrefetch()
        val provider = speech.provider
        if (provider == SpeechProvider.SYSTEM) return
        val texts = CloudTtsPrefetch.upcomingSpeechTexts(audioQueue.snapshot(), CloudTtsPrefetch.COUNT)
        if (texts.isEmpty()) return
        prefetchJob = scope.launch {
            for (text in texts) {
                if (generation != itemGeneration || !isPlaying || isPaused) return@launch
                if (speech.provider != provider) return@launch
                try {
                    when (val outcome = withContext(io) { speech.synthesize(provider, text) }) {
                        is SpeechSynthesisOutcome.Ready -> if (!outcome.fromCache) failures.onNetworkSuccess()
                        is SpeechSynthesisOutcome.Fallback -> failures.onPrefetchFailure(outcome.reason)
                        SpeechSynthesisOutcome.UseSystem -> Unit
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Next item; playback falls back per message anyway
                }
            }
        }
    }

    private fun cancelPrefetch() {
        prefetchJob?.cancel()
        prefetchJob = null
    }

    // ---- Audio slot ---------------------------------------------------------------------------------

    /**
     * Plays a voice note / synthesized message. [onPlayed] runs when it played to the end (messages: marked
     * as read). [onPlaybackError] replaces the default "skip silently but mark read" of unplayable files.
     */
    private fun playFile(
        path: String,
        generation: Int,
        onPlayed: () -> Unit,
        speed: Float = 1f,
        onPlaybackError: (() -> Unit)? = null
    ) {
        if (!isPlaying || isPaused || generation != itemGeneration) return
        val handleError = {
            if (onPlaybackError != null) onPlaybackError()
            else {
                onPlayed()
                processQueue()
            }
        }
        startAudio(
            AudioSource.FilePath(path), speed,
            onComplete = {
                onPlayed()
                processQueue()
            },
            onError = { if (generation == itemGeneration) handleError() }
        )
    }

    private fun playDing(ding: Ding, generation: Int, onDone: () -> Unit) {
        val done = { if (generation == itemGeneration) onDone() }
        startAudio(AudioSource.Tone(ding), 1f, onComplete = done, onError = done)
    }

    private fun startAudio(source: AudioSource, speed: Float, onComplete: () -> Unit, onError: () -> Unit) {
        audioPausedInPlace = false
        val token = ++audioToken
        val started = audio.play(
            source, speed,
            onComplete = { onController { if (token == audioToken) onComplete() } },
            onError = { onController { if (token == audioToken) onError() } }
        )
        if (!started) onError()
    }

    private fun releaseAudio() {
        audioPausedInPlace = false
        audioToken++
        audio.release()
    }

    private fun stopAllAudio() {
        speechToken++
        speech.stop()
        releaseAudio()
    }

    // ---- Transport commands -------------------------------------------------------------------------

    private fun doSkipMessage() {
        cancelPrefetch()
        stopAllAudio()
        // Paused in the middle of a message: it was pushed back to the front of the queue
        val skipped = if (isPaused && currentItem == null) audioQueue.next() else currentItem
        // The user chose to skip this message, so it counts as handled and is marked as read
        if (skipped is PlaybackItem.MessageItem) markMessagePlayed(skipped)
        flushReadCheckpoints()
        currentItem = null
        processQueue()
    }

    /**
     * Previous message: replays the message before the current one in this chat, then continues with the
     * current one; on the chat's first message it restarts it. Also resumes when paused.
     */
    private fun doPreviousMessage() {
        if (!isPlaying) return
        cancelPrefetch()
        stopAllAudio()
        // Paused in the middle of an item: it was pushed back to the front of the queue
        val current = currentItem ?: if (isPaused) audioQueue.next() else null
        currentItem = null
        flushReadCheckpoints()
        chatHistory.previous(current).asReversed().forEach { audioQueue.addFirst(it) }
        if (isPaused) {
            focusPolicy.onUserAction()
            host.requestAudioFocus()
            isPaused = false
            ui.setPaused(false)
            host.onStateChanged(isPlaying, isPaused)
        }
        processQueue()
    }

    private fun doSkipChat() {
        // The message being played and the rest of the chat are NOT marked as read
        flushReadCheckpoints()
        cancelPrefetch()
        stopAllAudio()
        currentItem = null
        audioQueue.skipToNextChat()
        processQueue()
    }

    private fun pausePlayback() {
        if (!isPlaying || isPaused) return
        isPaused = true
        ui.setPaused(true)
        // Invalidate in-flight work first so late results cannot start the next item
        itemGeneration++
        cancelPrefetch()
        ui.clearAudioLoading()

        if (audio.hasPlayer) {
            // A file is playing: pause it in place, resume continues from the same position
            if (audio.pause()) audioPausedInPlace = true else releaseAudio()
        } else {
            speechToken++
            speech.stop()
            // Push only an in-progress item back; completed messages were cleared in onMessageFullyPlayed
            PlaybackReadProgress.itemToRequeueOnPause(currentItem)?.let { audioQueue.addFirst(it) }
            currentItem = null
        }
        // After the requeue so a completion that raced with pause is still marked read now; the interrupted
        // message was never passed to messagePlayed, so it stays unread
        flushReadCheckpoints()
        host.onStateChanged(isPlaying, isPaused)
    }

    private fun resumePlayback() {
        if (!isPlaying || !isPaused) return
        host.requestAudioFocus()
        isPaused = false
        ui.setPaused(false)
        host.onStateChanged(isPlaying, isPaused)

        if (audioPausedInPlace && audio.hasPlayer) {
            audioPausedInPlace = false
            if (audio.resume()) {
                // Resume warming the next network-voice items for the remaining playback time
                if ((currentItem is PlaybackItem.MessageItem || currentItem is PlaybackItem.ChatTitle) &&
                    speech.provider != SpeechProvider.SYSTEM
                ) {
                    scheduleCloudPrefetch(itemGeneration)
                }
            } else {
                releaseAudio()
                processQueue()
            }
        } else {
            processQueue()
        }
    }

    private fun stopPlayback() {
        isPlaying = false
        focusPolicy.onUserAction()
        host.abandonAudioFocus()
        host.unregisterNoisyReceiver()
        isPaused = false
        itemGeneration++
        cancelPrefetch()
        flushReadCheckpoints()
        ui.setPlaying(false)
        host.onStateChanged(isPlaying, isPaused)
        stopAllAudio()
        currentItem = null
        host.onStopped()
    }

    // ---- Read checkpoints ---------------------------------------------------------------------------

    /** Message finished: clear [currentItem] if it still points here so pause cannot re-queue it, then checkpoint. */
    private fun onMessageFullyPlayed(item: PlaybackItem.MessageItem) {
        currentItem = PlaybackReadProgress.afterMessageCompleted(currentItem, item)
        markMessagePlayed(item)
    }

    private fun markMessagePlayed(item: PlaybackItem.MessageItem) {
        sendReadBatches(readCheckpointer.messagePlayed(item.chatId, item.messageId, clock()))
        scheduleReadFlush()
    }

    private fun flushReadCheckpoints() {
        readFlushJob?.cancel()
        readFlushJob = null
        sendReadBatches(readCheckpointer.flush())
    }

    private fun scheduleReadFlush() {
        readFlushJob?.cancel()
        val dueAt = readCheckpointer.nextDueAtMs() ?: return
        readFlushJob = scope.launch {
            delay((dueAt - clock()).coerceAtLeast(0L))
            sendReadBatches(readCheckpointer.flushIfDue(clock()))
        }
    }

    private fun sendReadBatches(batches: List<ReadCheckpointer.Batch>) = batches.forEach(readMarker::markRead)

    // ---- Now playing --------------------------------------------------------------------------------

    private fun publish(chatId: Long, fallbackTitle: String, content: NowPlaying.Content, engine: SpeechProvider?,
                        messagePosition: PlaybackPosition? = null) {
        val chat = runChats[chatId] ?: RunChat(fallbackTitle, null)
        ui.setNowPlaying(
            NowPlaying(
                chatId = chatId,
                chatTitle = chat.title,
                chatPhotoFileId = chat.photoFileId,
                content = content,
                chatPosition = plan.chatPosition(chatId),
                messagePosition = messagePosition,
                messageCount = plan.messageCount(chatId),
                engine = engine
            )
        )
    }

    /** The chat opens (ding + spoken title): the Player shows the chat and how many messages it has. */
    private fun publishChatOpening(chatId: Long, chatName: String) =
        publish(chatId, chatName, NowPlaying.Content.ChatOpening, engine = ui.nowPlaying.value?.engine ?: speech.provider)

    private fun publishMessage(item: PlaybackItem.MessageItem, isVoiceNote: Boolean) = publish(
        item.chatId,
        ui.nowPlaying.value?.chatTitle.orEmpty(),
        NowPlaying.Content.Message(
            messageId = item.messageId,
            sender = item.sender,
            text = item.text,
            contentType = item.contentType,
            isVoiceNote = isVoiceNote
        ),
        // A voice note plays its own audio; text is spoken by the chosen engine (updated on fallback)
        engine = if (isVoiceNote) null else speech.provider,
        messagePosition = plan.messagePosition(item.chatId, item.messageId)
    )
}
