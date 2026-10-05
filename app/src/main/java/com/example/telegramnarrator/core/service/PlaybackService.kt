package com.example.telegramnarrator.core.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.telegramnarrator.core.audio.AudioFocusController
import com.example.telegramnarrator.domain.audio.AudioFocusPolicy
import com.example.telegramnarrator.MainActivity
import com.example.telegramnarrator.R
import com.example.telegramnarrator.domain.audio.MessageSpeechBody
import com.example.telegramnarrator.data.rules.ChannelRulesRepository
import com.example.telegramnarrator.core.audio.TtsManager
import com.example.telegramnarrator.data.tts.TtsPreferences
import com.example.telegramnarrator.data.edge.EdgeSpeechSynthesizer
import com.example.telegramnarrator.data.openai.OpenAiSpeechSynthesizer
import com.example.telegramnarrator.domain.tts.SpeechProvider
import com.example.telegramnarrator.domain.tts.SpeechSynthesisOutcome
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.example.telegramnarrator.domain.audio.AudioQueue
import com.example.telegramnarrator.domain.audio.CloudTtsPrefetch
import com.example.telegramnarrator.domain.audio.MessageCleaner
import com.example.telegramnarrator.domain.audio.PlaybackItem
import com.example.telegramnarrator.domain.audio.PlaybackManager
import com.example.telegramnarrator.domain.audio.PlaybackReadProgress
import com.example.telegramnarrator.domain.audio.VoiceNotePlayback
import com.example.telegramnarrator.domain.audio.ReadCheckpointer
import com.example.telegramnarrator.domain.model.Chat
import com.example.telegramnarrator.domain.repository.ChatRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

@AndroidEntryPoint
class PlaybackService : Service() {

    @Inject lateinit var ttsManager: TtsManager
    @Inject lateinit var chatRepository: ChatRepository
    @Inject lateinit var playbackManager: PlaybackManager
    @Inject lateinit var channelRules: ChannelRulesRepository
    @Inject lateinit var openAiSpeech: OpenAiSpeechSynthesizer
    @Inject lateinit var edgeSpeech: EdgeSpeechSynthesizer
    @Inject lateinit var ttsPreferences: TtsPreferences

    private val mainHandler = Handler(Looper.getMainLooper())
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    
    // Mark-as-read requests run in their own scope that is not cancelled in onDestroy(), so the
    // final flush when the service stops still gets sent
    private val markReadScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val readCheckpointer = ReadCheckpointer()
    private var readFlushJob: Job? = null

    private val audioQueue = AudioQueue()
    @Volatile private var isPlaying = false
    @Volatile private var isPaused = false
    // True once startForeground() was called for the current run
    @Volatile private var foregroundStarted = false
    private var mediaPlayer: android.media.MediaPlayer? = null
    // True when pause() paused a voice note in place (so resume continues it instead of replaying the item)
    private var voiceNotePaused = false
    // Bumped whenever the item being processed is superseded (next item, pause, skip, stop), so that
    // late callbacks / coroutines of the old item know they must not start any audio
    private val itemGeneration = AtomicInteger(0)
    // Warms the next CLOUD_TTS_PREFETCH_COUNT cloud-TTS messages into the disk cache while current audio plays
    private var prefetchJob: Job? = null
    private var statusText = ""
    private var currentItem: PlaybackItem? = null
    
    private lateinit var mediaSession: MediaSessionCompat

    // Audio focus: pause for calls / navigation / other apps, resume afterwards (see AudioFocusPolicy)
    private val focusPolicy = AudioFocusPolicy()
    private lateinit var focusController: AudioFocusController
    // Headphones unplugged / Bluetooth disconnected: pause instead of blasting messages from the speaker
    private var noisyReceiverRegistered = false
    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                focusPolicy.onBecomingNoisy()
                pausePlayback()
            }
        }
    }

    companion object {
        const val ACTION_PLAY_ALL = "ACTION_PLAY_ALL"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_PAUSE = "ACTION_PAUSE"
        const val ACTION_RESUME = "ACTION_RESUME"
        const val ACTION_SKIP_MSG = "ACTION_SKIP_MSG"
        const val ACTION_SKIP_CHAT = "ACTION_SKIP_CHAT"
        const val EXTRA_CHAT_IDS = "EXTRA_CHAT_IDS"
        
        const val CHANNEL_ID = "PlaybackChannel"
        const val NOTIFICATION_ID = 1
        private const val TOAST_THROTTLE_MS = 30_000L
        /** Upcoming speakable messages to synthesize into the disk cache while the current cloud TTS item plays. */
        const val CLOUD_TTS_PREFETCH_COUNT = CloudTtsPrefetch.COUNT
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        focusController = AudioFocusController(this) { handleFocusChange(it) }
        mediaSession = MediaSessionCompat(this, "PlaybackService").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() { userResume() }
                override fun onPause() { userPause() }
                override fun onStop() { stopPlayback() }
                override fun onSkipToNext() { skipMessage() }
                override fun onSkipToPrevious() { skipChat() }
            })
            isActive = true
        }
        updateMediaSessionState()
    }

    private fun updateMediaSessionState() {
        val state = when {
            !isPlaying -> PlaybackStateCompat.STATE_STOPPED
            isPaused -> PlaybackStateCompat.STATE_PAUSED
            else -> PlaybackStateCompat.STATE_PLAYING
        }
        val speed = if (state == PlaybackStateCompat.STATE_PLAYING) 1f else 0f
        mediaSession.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_PLAY_PAUSE or
                    PlaybackStateCompat.ACTION_STOP or
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                )
                .setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, speed)
                .build()
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_ALL -> {
                val chatIds = intent.getLongArrayExtra(EXTRA_CHAT_IDS) ?: longArrayOf()
                // startForegroundService() requires startForeground() within ~5 s, so go to the foreground
                // right away - loading chats/messages from TDLib below can take longer than that
                enterForeground()
                scope.launch {
                    try {
                        startPlayback(chatIds)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        android.util.Log.e("PlaybackService", "Could not start playback", e)
                        stopPlayback()
                    }
                }
            }
            ACTION_STOP -> stopPlayback()
            ACTION_PAUSE -> userPause()
            ACTION_RESUME -> userResume()
            ACTION_SKIP_MSG -> skipMessage()
            ACTION_SKIP_CHAT -> skipChat()
        }
        return START_NOT_STICKY
    }

    /** Shows the "preparing" foreground notification once (idempotent while the service stays in the foreground). */
    private fun enterForeground() {
        if (foregroundStarted) return
        foregroundStarted = true
        statusText = getString(R.string.playback_preparing)
        val notification = buildNotification(statusText)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private suspend fun startPlayback(chatIds: LongArray) {
        audioQueue.clear()
        
        // Wait for TTS engine to initialize before grabbing the mic/audio focus
        ttsManager.isInitialized.first { it }
        
        chatIds.forEach { chatId ->
            val chat = chatRepository.getChat(chatId)
            val title = chat?.title ?: "Chat $chatId"
            // Unread incoming messages, oldest first
            val messages = chatRepository.getChatMessages(chatId)
            // Per-channel cleaning rules decide which messages are dropped and cut/replace text before
            // the generic MessageCleaner runs. A "drop the next N" group that continues in the next batch
            // is deferred (not queued, not marked as read) so the next run sees it whole.
            val moreUnreadFollows = (chat?.unreadCount ?: 0) > messages.size
            val decisions = channelRules.engine.evaluate(chatId, chat?.title, messages, moreUnreadFollows)
                .filter { !it.deferred }
            if (decisions.isNotEmpty()) {
                audioQueue.add(
                    PlaybackItem.Intro(
                        title,
                        chatId,
                        silent = decisions.all { it.dropped }
                    )
                )
                decisions.forEach { decision ->
                    val msg = decision.message
                    audioQueue.add(
                        PlaybackItem.MessageItem(
                            msg.senderName, decision.text, msg.id, chatId, msg.voiceNoteFileId, msg.contentType,
                            dropped = decision.dropped
                        )
                    )
                }
                audioQueue.add(PlaybackItem.Silence(1000))
            }
        }
        
        audioQueue.add(PlaybackItem.Outro)
        
        if (!isPlaying) {
            isPlaying = true
            focusPolicy.onUserAction()
            focusController.request()
            registerNoisyReceiver()
            playbackManager.setPlaying(true)
            updateMediaSessionState()
            processQueue()
        } else {
            // Already playing, but we cleared the queue and added new items, so stop current TTS
            // and let the next loop run, or force processQueue()
            // (the interrupted message is not marked as read)
            flushReadCheckpoints()
            cancelPrefetch()
            ttsManager.stop()
            releaseMediaPlayer()
            currentItem = null
            if (isPaused) {
                // Starting a new playback also un-pauses
                isPaused = false
                playbackManager.setPaused(false)
                updateMediaSessionState()
            }
            processQueue()
        }
    }

    private fun processQueue() {
        if (!isPlaying || isPaused) return
        
        currentItem = audioQueue.next()
        val item = currentItem
        if (item == null) {
            stopPlayback()
            return
        }

        val generation = itemGeneration.incrementAndGet()
        updateNotification(item)
        
        when (item) {
            is PlaybackItem.Intro -> {
                playbackManager.setPlayingChatId(item.chatId)
                if (item.silent) {
                    processQueue()
                } else {
                    // Language-neutral ding (no spoken "New chat" / "שיחה חדשה")
                    playChatBoundaryDing(generation)
                }
            }
            is PlaybackItem.MessageItem -> {
                if (item.dropped) {
                    // Dropped by the channel rules: not read, but handled, so it is marked as read in order
                    onMessageFullyPlayed(item)
                    processQueue()
                    return
                }
                val cleanedText = MessageCleaner.clean(item.text)

                // Voice notes: play the downloaded audio in the queue — never speak "Voice note" / sender.
                // If the file cannot be downloaded or played, skip silently (still mark read) like a photo.
                if (item.voiceNoteFileId != null) {
                    scope.launch {
                        val path = chatRepository.getVoiceFilePath(item.voiceNoteFileId)
                        // Paused / skipped / stopped while the file was downloading
                        if (generation != itemGeneration.get()) return@launch
                        when (val outcome = VoiceNotePlayback.afterDownload(path)) {
                            is VoiceNotePlayback.Outcome.Play ->
                                playAudioFile(outcome.path, generation, item)
                            VoiceNotePlayback.Outcome.SkipSilently -> {
                                onMessageFullyPlayed(item)
                                processQueue()
                            }
                        }
                    }
                    return
                }

                // Media-only (photo/video/sticker/... with no caption) and symbol-only rows: not spoken.
                // Skip silently but still mark as read when we pass them (batches with the next text).
                if (MessageSpeechBody.shouldSkipSilently(cleanedText, item.contentType, item.voiceNoteFileId)) {
                    onMessageFullyPlayed(item)
                    processQueue()
                    return
                }
                val text = MessageSpeechBody.resolve(cleanedText, item.contentType).orEmpty()
                // No "Message from X:" — speak the body only (notification still shows the sender).
                speakMessage(item, text)
            }
            is PlaybackItem.Silence -> {
                processQueue()
            }
            is PlaybackItem.Outro -> {
                // Distinct ding (not speech, not the chat-boundary tone)
                playEndOfMessagesDing(generation)
            }
        }
    }

    // Speaks a message; it counts as played (and gets marked as read) only if the speech finished.
    // Network engines (OpenAI BYOK / experimental Edge) synthesize a cached MP3 that is played via the
    // MediaPlayer queue path; any failure shows a toast and falls back to system TTS.
    private fun speakMessage(item: PlaybackItem.MessageItem, speechText: String) {
        val synthesize: ((String) -> SpeechSynthesisOutcome)? = when (ttsPreferences.settings.value.provider) {
            SpeechProvider.SYSTEM -> null
            SpeechProvider.OPENAI -> openAiSpeech::synthesize
            SpeechProvider.EDGE -> edgeSpeech::synthesize
        }
        if (synthesize == null) {
            speakWithSystemTts(item, speechText)
            return
        }
        val generation = itemGeneration.get()
        scope.launch {
            val outcome = synthesize(speechText)
            // Paused / skipped / stopped while the audio was being fetched
            if (generation != itemGeneration.get() || !isPlaying || isPaused) return@launch
            when (outcome) {
                is SpeechSynthesisOutcome.Ready -> {
                    // Warm the next few messages into the disk cache while this one plays
                    scheduleCloudPrefetch(generation)
                    playAudioFile(
                        outcome.file.absolutePath, generation, item,
                        speed = outcome.playbackSpeed,
                        onPlaybackError = {
                            // Corrupt / unplayable synthesized file: drop it and read the message with system TTS
                            openAiSpeech.discard(outcome.file)
                            edgeSpeech.discard(outcome.file)
                            showFallbackToast(getString(R.string.tts_fallback_unplayable))
                            speakWithSystemTts(item, speechText)
                        }
                    )
                }
                SpeechSynthesisOutcome.UseSystem -> speakWithSystemTts(item, speechText)
                is SpeechSynthesisOutcome.Fallback -> {
                    showFallbackToast(outcome.reason)
                    speakWithSystemTts(item, speechText)
                }
            }
        }
    }

    private fun speakWithSystemTts(item: PlaybackItem.MessageItem, speechText: String) {
        ttsManager.speak(speechText) { completed ->
            if (completed) onMessageFullyPlayed(item)
            processQueue()
        }
    }

    /**
     * While cloud TTS audio plays, synthesize the next [CLOUD_TTS_PREFETCH_COUNT] speakable queue items
     * into the existing disk cache (cache hits are cheap). Outcomes are ignored — no toast on prefetch
     * failure; playback still falls back per message when its turn comes. Cancelled on skip/stop/pause
     * or when [generation] is superseded / the engine is no longer a cloud provider.
     */
    private fun scheduleCloudPrefetch(generation: Int) {
        cancelPrefetch()
        val provider = ttsPreferences.settings.value.provider
        if (provider != SpeechProvider.OPENAI && provider != SpeechProvider.EDGE) return
        val synthesize: (String) -> SpeechSynthesisOutcome = when (provider) {
            SpeechProvider.OPENAI -> openAiSpeech::synthesize
            SpeechProvider.EDGE -> edgeSpeech::synthesize
            SpeechProvider.SYSTEM -> return
        }
        val texts = CloudTtsPrefetch.upcomingSpeechTexts(audioQueue.snapshot(), CLOUD_TTS_PREFETCH_COUNT)
        if (texts.isEmpty()) return
        prefetchJob = scope.launch {
            for (text in texts) {
                if (generation != itemGeneration.get() || !isPlaying || isPaused) return@launch
                if (ttsPreferences.settings.value.provider != provider) return@launch
                try {
                    synthesize(text)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    android.util.Log.w(
                        "PlaybackService",
                        "Cloud TTS prefetch failed: ${e.javaClass.simpleName}: ${e.message}"
                    )
                }
            }
        }
    }

    private fun cancelPrefetch() {
        prefetchJob?.cancel()
        prefetchJob = null
    }

    // At most one fallback toast per TOAST_THROTTLE_MS, so a dead network does not toast on every message
    @Volatile private var lastFallbackToastAt = 0L

    private fun showFallbackToast(message: String) {
        val now = SystemClock.elapsedRealtime()
        if (lastFallbackToastAt != 0L && now - lastFallbackToastAt < TOAST_THROTTLE_MS) return
        lastFallbackToastAt = now
        showToast(message)
    }

    private fun showToast(message: String) {
        mainHandler.post {
            Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Plays [path] (a voice note or a synthesized message) through the [mediaPlayer] slot. [speed] != 1 sets
     * the MediaPlayer playback speed (used for Edge TTS, whose audio is cached at normal speed).
     * [onPlaybackError] replaces the default "skip silently but mark read" handling of unplayable files.
     */
    private fun playAudioFile(
        path: String,
        generation: Int,
        item: PlaybackItem.MessageItem,
        speed: Float = 1f,
        onPlaybackError: (() -> Unit)? = null
    ) {
        if (!isPlaying || isPaused || generation != itemGeneration.get()) return
        val handleError = {
            if (onPlaybackError != null) onPlaybackError()
            else {
                onMessageFullyPlayed(item)
                processQueue()
            }
        }
        val player = android.media.MediaPlayer()
        try {
            player.apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setDataSource(path)
                setOnCompletionListener { 
                    it.release()
                    if (mediaPlayer === it) mediaPlayer = null
                    // The voice note played to the end
                    onMessageFullyPlayed(item)
                    processQueue()
                }
                setOnErrorListener { mp, _, _ ->
                    mp.release()
                    if (mediaPlayer === mp) mediaPlayer = null
                    // Unplayable voice note: skip silently but still mark read (like a photo)
                    if (generation == itemGeneration.get()) handleError()
                    true
                }
                prepare()
            }

            // pause / skip / stop may have happened while the file was being prepared
            if (!isPlaying || isPaused || generation != itemGeneration.get()) {
                player.release()
                return
            }
            mediaPlayer = player
            player.start()
            // After start(): on a prepared/paused player a non-zero speed would itself start playback
            if (speed != 1f) {
                try {
                    player.playbackParams = player.playbackParams.setSpeed(speed)
                } catch (e: Exception) {
                    // Speed change unsupported on this device: keep normal speed
                }
            }
        } catch (e: Exception) {
            player.release()
            if (mediaPlayer === player) mediaPlayer = null
            if (generation == itemGeneration.get()) handleError()
        }
    }

    /** Short language-neutral ding that marks a chat boundary (replaces spoken "New chat: …"). */
    private fun playChatBoundaryDing(generation: Int) {
        playRawDing(R.raw.chat_boundary_ding, generation) {
            if (generation == itemGeneration.get()) processQueue()
        }
    }

    /** Distinct end cue (not speech, not the chat-boundary ding). */
    private fun playEndOfMessagesDing(generation: Int) {
        playRawDing(R.raw.end_of_messages_ding, generation) {
            if (generation == itemGeneration.get()) stopPlayback()
        }
    }

    /**
     * Plays a short raw WAV ding via the same [mediaPlayer] slot as voice notes.
     * [onDone] runs on completion, error, or prepare failure (when still current).
     */
    private fun playRawDing(resId: Int, generation: Int, onDone: () -> Unit) {
        releaseMediaPlayer()
        val player = android.media.MediaPlayer()
        try {
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            val afd = resources.openRawResourceFd(resId)
            try {
                player.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            } finally {
                afd.close()
            }
            player.setOnCompletionListener {
                it.release()
                if (mediaPlayer === it) mediaPlayer = null
                onDone()
            }
            player.setOnErrorListener { mp, _, _ ->
                mp.release()
                if (mediaPlayer === mp) mediaPlayer = null
                onDone()
                true
            }
            player.prepare()
            if (!isPlaying || isPaused || generation != itemGeneration.get()) {
                player.release()
                return
            }
            mediaPlayer = player
            player.start()
        } catch (e: Exception) {
            player.release()
            if (mediaPlayer === player) mediaPlayer = null
            if (generation == itemGeneration.get()) onDone()
        }
    }

    private fun releaseMediaPlayer() {
        voiceNotePaused = false
        val player = mediaPlayer ?: return
        mediaPlayer = null
        try {
            player.stop()
        } catch (e: IllegalStateException) {
            // Not started / already released: nothing to stop
        }
        player.release()
    }

    private fun skipMessage() {
        cancelPrefetch()
        ttsManager.stop()
        releaseMediaPlayer()
        // Paused in the middle of a message: that message was pushed back to the front of the queue
        val skipped = if (isPaused && currentItem == null) audioQueue.next() else currentItem
        // The user chose to skip this message, so it counts as handled and is marked as read
        if (skipped is PlaybackItem.MessageItem) markMessagePlayed(skipped)
        flushReadCheckpoints()
        currentItem = null
        // processQueue() will be triggered by onDone if we use a listener, 
        // but currently ttsManager.speak handles the callback.
        // We need to ensure processQueue() is called exactly once.
        processQueue() 
    }

    private fun skipChat() {
        // The message being played and the rest of the chat are NOT marked as read
        flushReadCheckpoints()
        cancelPrefetch()
        ttsManager.stop()
        releaseMediaPlayer()
        currentItem = null
        audioQueue.skipToNextChat()
        processQueue()
    }

    private fun pausePlayback() {
        if (!isPlaying || isPaused) return
        isPaused = true
        playbackManager.setPaused(true)
        // Invalidate in-flight audio first so late callbacks cannot start the next item
        itemGeneration.incrementAndGet()
        cancelPrefetch()

        val player = mediaPlayer
        if (player != null) {
            // A voice note is playing: pause it in place, resume continues from the same position
            try {
                player.pause()
                voiceNotePaused = true
            } catch (e: IllegalStateException) {
                releaseMediaPlayer()
            }
        } else {
            ttsManager.stop()
            // Push only an in-progress item back. Completed messages are cleared in
            // onMessageFullyPlayed so a race with pause cannot replay them.
            PlaybackReadProgress.itemToRequeueOnPause(currentItem)?.let {
                audioQueue.addFirst(it)
                currentItem = null
            }
            currentItem = null
        }

        // Flush after stop/requeue so a completion that raced with pause is still marked read now.
        // The interrupted in-progress message was never passed to messagePlayed, so it stays unread.
        flushReadCheckpoints()

        updateMediaSessionState()
        refreshNotification()
    }

    // Explicit user requests (notification button, headset button, media controls): they also cancel any
    // pending automatic resume after an audio-focus interruption
    private fun userPause() {
        focusPolicy.onUserAction()
        pausePlayback()
    }

    private fun userResume() {
        focusPolicy.onUserAction()
        resumePlayback()
    }

    private fun handleFocusChange(change: AudioFocusPolicy.FocusChange) {
        when (focusPolicy.onFocusChange(change, userPaused = isPaused || !isPlaying)) {
            AudioFocusPolicy.Action.PAUSE -> pausePlayback()
            AudioFocusPolicy.Action.RESUME -> resumePlayback()
            AudioFocusPolicy.Action.NONE -> Unit
        }
    }

    private fun registerNoisyReceiver() {
        if (noisyReceiverRegistered) return
        ContextCompat.registerReceiver(
            this, noisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        noisyReceiverRegistered = true
    }

    private fun unregisterNoisyReceiver() {
        if (!noisyReceiverRegistered) return
        noisyReceiverRegistered = false
        unregisterReceiver(noisyReceiver)
    }

    private fun resumePlayback() {
        if (!isPlaying || !isPaused) return
        focusController.request()
        isPaused = false
        playbackManager.setPaused(false)
        updateMediaSessionState()

        val player = mediaPlayer
        if (voiceNotePaused && player != null) {
            // Continue the voice note / synthesized file where it was paused
            voiceNotePaused = false
            try {
                player.start()
                refreshNotification()
                // Resume warming the next cloud-TTS items for the remaining playback time
                if (currentItem is PlaybackItem.MessageItem &&
                    (ttsPreferences.settings.value.provider == SpeechProvider.OPENAI ||
                        ttsPreferences.settings.value.provider == SpeechProvider.EDGE)
                ) {
                    scheduleCloudPrefetch(itemGeneration.get())
                }
            } catch (e: IllegalStateException) {
                releaseMediaPlayer()
                processQueue()
            }
        } else {
            // Start playback again
            processQueue()
        }
    }

    private fun stopPlayback() {
            isPlaying = false
            focusPolicy.onUserAction()
            focusController.abandon()
            unregisterNoisyReceiver()
            isPaused = false
            itemGeneration.incrementAndGet()
            cancelPrefetch()
            flushReadCheckpoints()
            playbackManager.setPlaying(false)
            updateMediaSessionState()
            ttsManager.stop()
            releaseMediaPlayer()
            currentItem = null
            foregroundStarted = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
    }

    /**
     * Message finished (spoken / voice note / dropped / empty): clear [currentItem] if it still
     * points here so pause cannot re-queue it, then checkpoint for mark-as-read.
     */
    private fun onMessageFullyPlayed(item: PlaybackItem.MessageItem) {
        currentItem = PlaybackReadProgress.afterMessageCompleted(currentItem, item)
        markMessagePlayed(item)
    }

    /** A message was fully played (or deliberately skipped): queue it for marking as read. */
    private fun markMessagePlayed(item: PlaybackItem.MessageItem) {
        sendReadBatches(readCheckpointer.messagePlayed(item.chatId, item.messageId, SystemClock.elapsedRealtime()))
        scheduleReadFlush()
    }

    /** Sends everything that is waiting to be marked as read, right now. */
    private fun flushReadCheckpoints() {
        readFlushJob?.cancel()
        readFlushJob = null
        sendReadBatches(readCheckpointer.flush())
    }

    private fun scheduleReadFlush() {
        readFlushJob?.cancel()
        val dueAt = readCheckpointer.nextDueAtMs() ?: return
        readFlushJob = scope.launch {
            delay((dueAt - SystemClock.elapsedRealtime()).coerceAtLeast(0L))
            sendReadBatches(readCheckpointer.flushIfDue(SystemClock.elapsedRealtime()))
        }
    }

    private fun sendReadBatches(batches: List<ReadCheckpointer.Batch>) {
        batches.forEach { batch ->
            markReadScope.launch {
                chatRepository.markChatAsRead(batch.chatId, batch.messageIds)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_playback),
                NotificationManager.IMPORTANCE_LOW
            ).apply { setShowBadge(false) }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val stopIntent = Intent(this, PlaybackService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE)

        val skipMsgIntent = Intent(this, PlaybackService::class.java).apply { action = ACTION_SKIP_MSG }
        val skipMsgPendingIntent = PendingIntent.getService(this, 1, skipMsgIntent, PendingIntent.FLAG_IMMUTABLE)

        val skipChatIntent = Intent(this, PlaybackService::class.java).apply { action = ACTION_SKIP_CHAT }
        val skipChatPendingIntent = PendingIntent.getService(this, 2, skipChatIntent, PendingIntent.FLAG_IMMUTABLE)

        // Pause <-> resume toggle
        val toggleIntent = Intent(this, PlaybackService::class.java).apply {
            action = if (isPaused) ACTION_RESUME else ACTION_PAUSE
        }
        val togglePendingIntent = PendingIntent.getService(this, 3, toggleIntent, PendingIntent.FLAG_IMMUTABLE)
        val toggleIcon = if (isPaused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause
        val toggleTitle = getString(if (isPaused) R.string.playback_resume else R.string.playback_pause)

        // Tapping the notification opens the app
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this, 4, openIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_stat_narrator)
            .setContentIntent(openPendingIntent)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(toggleIcon, toggleTitle, togglePendingIntent)
            .addAction(android.R.drawable.ic_media_next, getString(R.string.playback_skip_msg), skipMsgPendingIntent)
            .addAction(android.R.drawable.ic_media_next, getString(R.string.playback_skip_chat), skipChatPendingIntent)
            .addAction(android.R.drawable.ic_delete, getString(R.string.home_btn_stop), stopPendingIntent)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setShowActionsInCompactView(0, 1, 3)
                    .setMediaSession(mediaSession.sessionToken)
            )
            .build()
    }
    
    private fun updateNotification(item: PlaybackItem) {
        val text = when(item) {
             is PlaybackItem.Intro -> getString(R.string.notification_chat, item.chatName)
             is PlaybackItem.MessageItem -> getString(R.string.notification_from, item.sender ?: getString(R.string.playback_unknown_sender))
             else -> getString(R.string.notification_playing)
        }
        playbackManager.setStatus(text)
        statusText = text
        refreshNotification()
    }

    private fun refreshNotification() {
        val text = if (isPaused) getString(R.string.playback_paused) else statusText
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        flushReadCheckpoints()
        focusController.abandon()
        unregisterNoisyReceiver()
        job.cancel()
        mediaSession.isActive = false
        mediaSession.release()
    }
}
