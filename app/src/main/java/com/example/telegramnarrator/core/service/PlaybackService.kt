package com.example.telegramnarrator.core.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.content.Context
import androidx.core.app.NotificationCompat
import com.example.telegramnarrator.MainActivity
import com.example.telegramnarrator.R
import com.example.telegramnarrator.core.audio.TtsManager
import com.example.telegramnarrator.domain.audio.AudioQueue
import com.example.telegramnarrator.domain.audio.MessageCleaner
import com.example.telegramnarrator.domain.audio.PlaybackItem
import com.example.telegramnarrator.domain.audio.PlaybackManager
import com.example.telegramnarrator.domain.model.Chat
import com.example.telegramnarrator.domain.repository.ChatRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import androidx.media.MediaBrowserServiceCompat
import android.os.Bundle
import javax.inject.Inject

@AndroidEntryPoint
class PlaybackService : MediaBrowserServiceCompat() {

    @Inject lateinit var ttsManager: TtsManager
    @Inject lateinit var chatRepository: ChatRepository
    @Inject lateinit var playbackManager: PlaybackManager

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    
    private val audioQueue = AudioQueue()
    private var isPlaying = false
    private var isPaused = false
    private var mediaPlayer: android.media.MediaPlayer? = null
    private var currentItem: PlaybackItem? = null
    private var lastSender: String? = null
    private var isFirstMessageInChat = false
    
    private lateinit var mediaSession: MediaSessionCompat
    
    // Audio focus
    private lateinit var audioManager: android.media.AudioManager
    private val audioFocusChangeListener = android.media.AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            android.media.AudioManager.AUDIOFOCUS_LOSS,
            android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                pausePlayback()
            }
            android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                mediaPlayer?.setVolume(0.2f, 0.2f)
            }
            android.media.AudioManager.AUDIOFOCUS_GAIN -> {
                mediaPlayer?.setVolume(1.0f, 1.0f)
                resumePlayback()
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
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        mediaSession = MediaSessionCompat(this, "PlaybackService").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() { 
                    if (isPaused) {
                        resumePlayback()
                    } else if (!isPlaying) {
                        scope.launch {
                            val ids = playbackManager.selectedChatIds.value.toLongArray()
                            if (ids.isNotEmpty()) startPlayback(ids)
                        }
                    }
                }
                override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
                    if (mediaId == "play_all") onPlay()
                }
                override fun onPause() { pausePlayback() }
                override fun onStop() { stopPlayback() }
                override fun onSkipToNext() { skipMessage() }
                override fun onSkipToPrevious() { skipChat() }
            })
        }
        updateMediaSessionState()
        mediaSession.isActive = true
        sessionToken = mediaSession.sessionToken

        scope.launch {
            playbackManager.playbackSpeed.collect { speed ->
                ttsManager.setSpeechRate(speed)
            }
        }
    }

    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): BrowserRoot? {
        // Basic filter: only allow trusted apps or just return root for AA
        return BrowserRoot("root", null)
    }

    override fun onLoadChildren(parentId: String, result: Result<MutableList<MediaBrowserCompat.MediaItem>>) {
        if (parentId == "root") {
            val items = mutableListOf<MediaBrowserCompat.MediaItem>()
            val desc = MediaDescriptionCompat.Builder()
                .setMediaId("play_all")
                .setTitle("Narrate New Messages")
                .setSubtitle("Listen to unread messages from Telegram")
                .build()
            items.add(MediaBrowserCompat.MediaItem(desc, MediaBrowserCompat.MediaItem.FLAG_PLAYABLE))
            result.sendResult(items)
        } else {
            result.sendResult(null)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_ALL -> {
                val chatIds = intent.getLongArrayExtra(EXTRA_CHAT_IDS) 
                    ?: playbackManager.selectedChatIds.value.toLongArray()
                if (chatIds.isNotEmpty()) {
                    scope.launch { startPlayback(chatIds) }
                }
            }
            ACTION_STOP -> stopPlayback()
            ACTION_PAUSE -> pausePlayback()
            ACTION_RESUME -> resumePlayback()
            ACTION_SKIP_MSG -> skipMessage()
            ACTION_SKIP_CHAT -> skipChat()
        }
        return START_NOT_STICKY
    }

    private suspend fun startPlayback(chatIds: LongArray) {
        audioQueue.clear()
        
        // Wait for TTS engine to initialize before grabbing the mic/audio focus
        ttsManager.isInitialized.first { it }
        
        chatIds.forEach { chatId ->
            val chat = chatRepository.getChat(chatId)
            val title = chat?.title ?: "Chat $chatId"
            val messages = chatRepository.getChatMessages(chatId, 20)
            if (messages.isNotEmpty()) {
                audioQueue.add(PlaybackItem.Intro(title, chatId))
                messages.reversed().forEach { msg ->
                    audioQueue.add(PlaybackItem.MessageItem(msg.senderName, msg.text, msg.id, msg.voiceNoteFileId))
                }
                audioQueue.add(PlaybackItem.MarkAsRead(chatId))
                audioQueue.add(PlaybackItem.Silence(1000))
            }
        }
        
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioManager.requestAudioFocus(
                android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(
                        android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setOnAudioFocusChangeListener(audioFocusChangeListener)
                    .build()
            )
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                audioFocusChangeListener,
                android.media.AudioManager.STREAM_MUSIC,
                android.media.AudioManager.AUDIOFOCUS_GAIN
            )
        }

        if (result != android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            return
        }

        audioQueue.add(PlaybackItem.Outro)
        
        if (!isPlaying) {
            isPlaying = true
            isPaused = false
            playbackManager.setPlaying(true)
            playbackManager.setPaused(false)
            updateMediaSessionState()
            startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.login_status_initializing)))
            processQueue()
        } else {
            // Already playing, but we cleared the queue and added new items, so stop current TTS
            // and let the next loop run, or force processQueue()
            ttsManager.stop()
            isPaused = false
            playbackManager.setPaused(false)
            updateMediaSessionState()
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

        updateNotification(item)
        
        when (item) {
            is PlaybackItem.Intro -> {
                lastSender = null
                isFirstMessageInChat = true
                playbackManager.setPlayingChatId(item.chatId)
                ttsManager.speak(getString(R.string.playback_new_chat, item.chatName)) { processQueue() }
            }
            is PlaybackItem.MessageItem -> {
                val sender = item.sender ?: "Unknown"
                
                val text = MessageCleaner.clean(item.text)
                // Filter if blank to not pause
                if (text.isBlank() && item.voiceNoteFileId == null) {
                    processQueue()
                    return
                }

                val speechText = if (sender == lastSender || isFirstMessageInChat) {
                    isFirstMessageInChat = false
                    lastSender = sender
                    text
                } else {
                    lastSender = sender
                    getString(R.string.playback_from, sender, text)
                }

                if (item.voiceNoteFileId != null) {
                    scope.launch {
                        val path = chatRepository.getVoiceFilePath(item.voiceNoteFileId)
                        if (path != null) {
                            val introText = if (sender == lastSender || isFirstMessageInChat) {
                                "Voice Note"
                            } else {
                                getString(R.string.playback_from, sender, "Voice Note")
                            }
                            isFirstMessageInChat = false
                            lastSender = sender
                            ttsManager.speak(introText) { 
                                playAudioFile(path)
                            }
                        } else {
                            ttsManager.speak(speechText) { processQueue() }
                        }
                    }
                } else {
                    ttsManager.speak(speechText) { processQueue() }
                }
            }
            is PlaybackItem.Silence -> {
                processQueue()
            }
            is PlaybackItem.MarkAsRead -> {
                if (playbackManager.shouldMarkAsRead.value) {
                    scope.launch {
                        chatRepository.markChatAsRead(item.chatId)
                    }
                }
                processQueue()
            }
            is PlaybackItem.Outro -> {
                  ttsManager.speak(getString(R.string.playback_end)) { stopPlayback() }
            }
        }
    }

    private fun playAudioFile(path: String) {
        if (!isPlaying) return
        try {
            mediaPlayer = android.media.MediaPlayer().apply {
                setDataSource(path)
                setOnCompletionListener { 
                    it.release()
                    mediaPlayer = null
                    processQueue()
                }
                setOnErrorListener { mp, _, _ ->
                    mp.release()
                    mediaPlayer = null
                    processQueue()
                    true
                }
                prepare()
                start()
            }
        } catch (e: Exception) {
            mediaPlayer?.release()
            mediaPlayer = null
            processQueue()
        }
    }

    private fun skipMessage() {
        ttsManager.stop()
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        // processQueue() will be triggered by onDone if we use a listener, 
        // but currently ttsManager.speak handles the callback.
        // We need to ensure processQueue() is called exactly once.
        processQueue() 
    }

    private fun skipChat() {
        ttsManager.stop()
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        audioQueue.skipToNextChat()
        lastSender = null
        processQueue()
    }

    private fun updateMediaSessionState() {
        val state = if (isPaused) PlaybackStateCompat.STATE_PAUSED else if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_STOPPED
        val actions = PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_STOP or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
        
        mediaSession.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(actions)
                .setState(state, 0L, 1f)
                .build()
        )
    }

    private fun pausePlayback() {
        if (!isPlaying || isPaused) return
        isPaused = true
        playbackManager.setPaused(true)
        updateMediaSessionState()
        ttsManager.stop()
        mediaPlayer?.pause()
        
        // Push the current item back so it plays when resumed
        currentItem?.let {
            audioQueue.addFirst(it)
            currentItem = null
        }
        // Update notification to show play button
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(playbackManager.currentStatus.value ?: "Paused"))
    }

    private fun resumePlayback() {
        if (!isPlaying || !isPaused) return
        isPaused = false
        playbackManager.setPaused(false)
        updateMediaSessionState()
        
        // Start playback again
        processQueue()
    }

    private fun stopPlayback() {
            isPlaying = false
            isPaused = false
            playbackManager.setPlaying(false)
            updateMediaSessionState()
            ttsManager.stop()
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
            currentItem = null
            lastSender = null
            stopForeground(STOP_FOREGROUND_REMOVE)
            // Abandon audio focus
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioManager.abandonAudioFocusRequest(
                    android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN)
                        .setOnAudioFocusChangeListener(audioFocusChangeListener)
                        .build()
                )
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(audioFocusChangeListener)
            }
            stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Playback",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val stopIntent = Intent(this, PlaybackService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE)

        val pauseResumeAction = if (isPaused) ACTION_RESUME else ACTION_PAUSE
        val pauseResumeIcon = if (isPaused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause
        val pauseResumeText = if (isPaused) "Resume" else "Pause"
        val pauseResumeIntent = Intent(this, PlaybackService::class.java).apply { action = pauseResumeAction }
        val pauseResumePendingIntent = PendingIntent.getService(this, 10, pauseResumeIntent, PendingIntent.FLAG_IMMUTABLE)

        val skipMsgIntent = Intent(this, PlaybackService::class.java).apply { action = ACTION_SKIP_MSG }
        val skipMsgPendingIntent = PendingIntent.getService(this, 1, skipMsgIntent, PendingIntent.FLAG_IMMUTABLE)

        val skipChatIntent = Intent(this, PlaybackService::class.java).apply { action = ACTION_SKIP_CHAT }
        val skipChatPendingIntent = PendingIntent.getService(this, 2, skipChatIntent, PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .addAction(android.R.drawable.ic_media_previous, "Next Chat", skipChatPendingIntent)
            .addAction(pauseResumeIcon, pauseResumeText, pauseResumePendingIntent)
            .addAction(android.R.drawable.ic_media_next, "Next Msg", skipMsgPendingIntent)
            .addAction(android.R.drawable.ic_delete, getString(R.string.home_btn_stop), stopPendingIntent)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setShowActionsInCompactView(0, 1, 2)
                    .setMediaSession(mediaSession.sessionToken)
            )
            .setOngoing(!isPaused)
        
        return builder.build()
    }
    
    private fun updateNotification(item: PlaybackItem) {
        val text = when(item) {
             is PlaybackItem.Intro -> "Chat: ${item.chatName}"
             is PlaybackItem.MessageItem -> "From ${item.sender ?: "Unknown"}"
             else -> "Playing..."
        }
        playbackManager.setStatus(text)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
        mediaSession.isActive = false
        mediaSession.release()
    }
}
