package com.example.telegramnarrator.core.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
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
import javax.inject.Inject

@AndroidEntryPoint
class PlaybackService : Service() {

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
    
    private lateinit var mediaSession: MediaSessionCompat

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
        mediaSession = MediaSessionCompat(this, "PlaybackService").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPause() { stopPlayback() }
                override fun onStop() { stopPlayback() }
                override fun onSkipToNext() { skipMessage() }
                override fun onSkipToPrevious() { skipChat() }
            })
            setPlaybackState(
                PlaybackStateCompat.Builder()
                    .setActions(
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_STOP or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                    )
                    .setState(PlaybackStateCompat.STATE_PLAYING, 0L, 1f)
                    .build()
            )
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_ALL -> {
                val chatIds = intent.getLongArrayExtra(EXTRA_CHAT_IDS) ?: longArrayOf()
                scope.launch {
                    startPlayback(chatIds)
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
                audioQueue.add(PlaybackItem.Intro(title))
                messages.reversed().forEach { msg ->
                    audioQueue.add(PlaybackItem.MessageItem(msg.senderName, msg.text, msg.id, msg.voiceNoteFileId))
                }
                audioQueue.add(PlaybackItem.MarkAsRead(chatId))
                audioQueue.add(PlaybackItem.Silence(1000))
            }
        }
        
        audioQueue.add(PlaybackItem.Outro)
        
        if (!isPlaying) {
            isPlaying = true
            playbackManager.setPlaying(true)
            startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.login_status_initializing)))
            processQueue()
        } else {
            // Already playing, but we cleared the queue and added new items, so stop current TTS
            // and let the next loop run, or force processQueue()
            ttsManager.stop()
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
                ttsManager.speak(getString(R.string.playback_new_chat, item.chatName)) { processQueue() }
            }
            is PlaybackItem.MessageItem -> {
                val sender = item.sender ?: "Unknown"
                
                val text = MessageCleaner.clean(item.text, getString(R.string.playback_link))
                // Filter if blank to not pause
                if (text.isBlank() && item.voiceNoteFileId == null) {
                    processQueue()
                    return
                }

                val speechText = if (sender == lastSender) {
                    text
                } else {
                    lastSender = sender
                    getString(R.string.playback_from, sender, text)
                }

                if (item.voiceNoteFileId != null) {
                    scope.launch {
                        val path = chatRepository.getVoiceFilePath(item.voiceNoteFileId)
                        if (path != null) {
                            val introText = if (sender == lastSender) "Voice Note" else getString(R.string.playback_from, sender, "Voice Note")
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
                scope.launch {
                    chatRepository.markChatAsRead(item.chatId)
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
        processQueue()
    }

    private fun pausePlayback() {
        if (!isPlaying || isPaused) return
        isPaused = true
        playbackManager.setPaused(true)
        ttsManager.stop()
        mediaPlayer?.pause()
        
        // Push the current item back so it plays when resumed
        currentItem?.let {
            audioQueue.addFirst(it)
            currentItem = null
        }
    }

    private fun resumePlayback() {
        if (!isPlaying || !isPaused) return
        isPaused = false
        playbackManager.setPaused(false)
        
        // Start playback again
        processQueue()
    }

    private fun stopPlayback() {
            isPlaying = false
            isPaused = false
            playbackManager.setPlaying(false)
            ttsManager.stop()
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
            currentItem = null
            stopForeground(STOP_FOREGROUND_REMOVE)
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

        val skipMsgIntent = Intent(this, PlaybackService::class.java).apply { action = ACTION_SKIP_MSG }
        val skipMsgPendingIntent = PendingIntent.getService(this, 1, skipMsgIntent, PendingIntent.FLAG_IMMUTABLE)

        val skipChatIntent = Intent(this, PlaybackService::class.java).apply { action = ACTION_SKIP_CHAT }
        val skipChatPendingIntent = PendingIntent.getService(this, 2, skipChatIntent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .addAction(android.R.drawable.ic_media_next, getString(R.string.playback_skip_msg), skipMsgPendingIntent)
            .addAction(android.R.drawable.ic_media_next, getString(R.string.playback_skip_chat), skipChatPendingIntent)
            .addAction(android.R.drawable.ic_delete, getString(R.string.home_btn_stop), stopPendingIntent)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setShowActionsInCompactView(0, 1, 2)
                    .setMediaSession(mediaSession.sessionToken)
            )
            .build()
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
