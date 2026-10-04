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
import com.example.telegramnarrator.core.labelRes
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
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

@AndroidEntryPoint
class PlaybackService : Service() {

    @Inject lateinit var ttsManager: TtsManager
    @Inject lateinit var chatRepository: ChatRepository
    @Inject lateinit var playbackManager: PlaybackManager

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    
    private val audioQueue = AudioQueue()
    @Volatile private var isPlaying = false
    @Volatile private var isPaused = false
    private var mediaPlayer: android.media.MediaPlayer? = null
    // True when pause() paused a voice note in place (so resume continues it instead of replaying the item)
    private var voiceNotePaused = false
    // Bumped whenever the item being processed is superseded (next item, pause, skip, stop), so that
    // late callbacks / coroutines of the old item know they must not start any audio
    private val itemGeneration = AtomicInteger(0)
    private var statusText = ""
    private var currentItem: PlaybackItem? = null
    private var lastSender: String? = null
    // Value of lastSender before currentItem updated it (restored if the item is replayed after a pause)
    private var senderBeforeCurrentItem: String? = null
    
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
                override fun onPlay() { resumePlayback() }
                override fun onPause() { pausePlayback() }
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
            // Unread incoming messages, oldest first
            val messages = chatRepository.getChatMessages(chatId)
            if (messages.isNotEmpty()) {
                audioQueue.add(PlaybackItem.Intro(title))
                messages.forEach { msg ->
                    audioQueue.add(PlaybackItem.MessageItem(msg.senderName, msg.text, msg.id, msg.voiceNoteFileId, msg.contentType))
                }
                audioQueue.add(PlaybackItem.MarkAsRead(chatId))
                audioQueue.add(PlaybackItem.Silence(1000))
            }
        }
        
        audioQueue.add(PlaybackItem.Outro)
        
        if (!isPlaying) {
            isPlaying = true
            playbackManager.setPlaying(true)
            statusText = getString(R.string.login_status_initializing)
            updateMediaSessionState()
            startForeground(NOTIFICATION_ID, buildNotification(statusText))
            processQueue()
        } else {
            // Already playing, but we cleared the queue and added new items, so stop current TTS
            // and let the next loop run, or force processQueue()
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
                lastSender = null
                ttsManager.speak(getString(R.string.playback_new_chat, item.chatName)) { processQueue() }
            }
            is PlaybackItem.MessageItem -> {
                val sender = item.sender ?: getString(R.string.playback_unknown_sender)
                
                // Media without a caption is announced by its type ("Photo", "Sticker", ...);
                // content we can't handle has no label and is skipped below
                val text = MessageCleaner.clean(item.text, getString(R.string.playback_link))
                    .ifBlank { item.contentType.labelRes()?.let { getString(it) } ?: "" }
                // Filter if blank to not pause
                if (text.isBlank() && item.voiceNoteFileId == null) {
                    processQueue()
                    return
                }

                // Announce the sender only when it changes. This must be decided once, before lastSender
                // is updated, and is used for both the text and the voice note intro below.
                val isNewSender = sender != lastSender
                senderBeforeCurrentItem = lastSender
                lastSender = sender

                val speechText = if (isNewSender) {
                    getString(R.string.playback_from, sender, text)
                } else {
                    text
                }

                if (item.voiceNoteFileId != null) {
                    val voiceNoteLabel = getString(R.string.playback_voice_note)
                    val introText = if (isNewSender) getString(R.string.playback_from, sender, voiceNoteLabel) else voiceNoteLabel
                    scope.launch {
                        val path = chatRepository.getVoiceFilePath(item.voiceNoteFileId)
                        // Paused / skipped / stopped while the file was downloading
                        if (generation != itemGeneration.get()) return@launch
                        if (path != null) {
                            ttsManager.speak(introText) { 
                                playAudioFile(path, generation)
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

    private fun playAudioFile(path: String, generation: Int) {
        if (!isPlaying || isPaused || generation != itemGeneration.get()) return
        val player = android.media.MediaPlayer()
        try {
            player.apply {
                setDataSource(path)
                setOnCompletionListener { 
                    it.release()
                    if (mediaPlayer === it) mediaPlayer = null
                    processQueue()
                }
                setOnErrorListener { mp, _, _ ->
                    mp.release()
                    if (mediaPlayer === mp) mediaPlayer = null
                    processQueue()
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
        } catch (e: Exception) {
            player.release()
            if (mediaPlayer === player) mediaPlayer = null
            if (generation == itemGeneration.get()) processQueue()
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
        ttsManager.stop()
        releaseMediaPlayer()
        // Paused in the middle of a message: that message was pushed back to the front of the queue
        if (isPaused && currentItem == null) audioQueue.next()
        currentItem = null
        // processQueue() will be triggered by onDone if we use a listener, 
        // but currently ttsManager.speak handles the callback.
        // We need to ensure processQueue() is called exactly once.
        processQueue() 
    }

    private fun skipChat() {
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
        // Invalidate everything that is still pending for the current item
        itemGeneration.incrementAndGet()

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
            // Push the current item back so it plays (from its start) when resumed
            currentItem?.let {
                audioQueue.addFirst(it)
                currentItem = null
                // The message will be announced again from its start, including its sender
                if (it is PlaybackItem.MessageItem) lastSender = senderBeforeCurrentItem
            }
        }

        updateMediaSessionState()
        refreshNotification()
    }

    private fun resumePlayback() {
        if (!isPlaying || !isPaused) return
        isPaused = false
        playbackManager.setPaused(false)
        updateMediaSessionState()

        val player = mediaPlayer
        if (voiceNotePaused && player != null) {
            // Continue the voice note where it was paused
            voiceNotePaused = false
            try {
                player.start()
                refreshNotification()
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
            isPaused = false
            itemGeneration.incrementAndGet()
            playbackManager.setPlaying(false)
            updateMediaSessionState()
            ttsManager.stop()
            releaseMediaPlayer()
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

        // Pause <-> resume toggle
        val toggleIntent = Intent(this, PlaybackService::class.java).apply {
            action = if (isPaused) ACTION_RESUME else ACTION_PAUSE
        }
        val togglePendingIntent = PendingIntent.getService(this, 3, toggleIntent, PendingIntent.FLAG_IMMUTABLE)
        val toggleIcon = if (isPaused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause
        val toggleTitle = getString(if (isPaused) R.string.playback_resume else R.string.playback_pause)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
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
             is PlaybackItem.Intro -> "Chat: ${item.chatName}"
             is PlaybackItem.MessageItem -> "From ${item.sender ?: getString(R.string.playback_unknown_sender)}"
             else -> "Playing..."
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
        job.cancel()
        mediaSession.isActive = false
        mediaSession.release()
    }
}
