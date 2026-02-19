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
import com.example.telegramnarrator.domain.model.Chat
import com.example.telegramnarrator.domain.repository.ChatRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class PlaybackService : Service() {

    @Inject lateinit var ttsManager: TtsManager
    @Inject lateinit var chatRepository: ChatRepository

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    
    private val audioQueue = AudioQueue()
    private var isPlaying = false

    companion object {
        const val ACTION_PLAY_ALL = "ACTION_PLAY_ALL"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_NEXT = "ACTION_NEXT"
        const val EXTRA_CHAT_IDS = "EXTRA_CHAT_IDS"
        
        const val CHANNEL_ID = "PlaybackChannel"
        const val NOTIFICATION_ID = 1
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
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
            ACTION_NEXT -> nextItem()
        }
        return START_NOT_STICKY
    }

    private suspend fun startPlayback(chatIds: LongArray) {
        audioQueue.clear()
        
        // Fetch messages for each chat and build queue
        // In a real app, strict error handling and maybe streaming/lazy loading
        chatIds.forEach { chatId ->
            val messages = chatRepository.getChatMessages(chatId, 20) // Limit 20 for now
            if (messages.isNotEmpty()) {
                audioQueue.add(PlaybackItem.Intro("Chat ${chatId}")) // We need chat Title, but repository returns ID for now. Ideally repo returns Chat object or we fetch it.
                // Reversing because getChatHistory often returns new->old. We want Old->New for narration.
                // If TDLib returns New->Old, reverse. If Old->New (from=0, offset=0 usually New->Old), verify.
                // TDLib GetChatHistory: usually returns messages in reverse chronological order (newest first). 
                // So we reverse to narrate oldest (unread) to newest.
                messages.reversed().forEach { msg ->
                    audioQueue.add(PlaybackItem.MessageItem(msg.senderName, msg.text, msg.id))
                }
                audioQueue.add(PlaybackItem.Silence(1000))
            }
        }
        
        audioQueue.add(PlaybackItem.Outro)
        
        if (!isPlaying) {
            isPlaying = true
            startForeground(NOTIFICATION_ID, buildNotification("Playing..."))
            processQueue()
        }
    }

    private fun processQueue() {
        if (!isPlaying) return
        
        val item = audioQueue.next()
        if (item == null) {
            stopPlayback()
            return
        }

        updateNotification(item)
        
        when (item) {
            is PlaybackItem.Intro -> {
                ttsManager.speak("New chat: ${item.chatName}") { processQueue() }
            }
            is PlaybackItem.MessageItem -> {
                val text = MessageCleaner.clean(item.text)
                val sender = item.sender ?: "Unknown"
                ttsManager.speak("Message from $sender: $text") { processQueue() }
            }
            is PlaybackItem.Silence -> {
                // simple silence simulation
                // ttsManager.speak("", ...) with delay or just play silence
                // For now, just skip immediately for speed in prototype
                processQueue()
            }
            is PlaybackItem.Outro -> {
                 ttsManager.speak("End of messages.") { stopPlayback() }
            }
        }
    }

    private fun nextItem() {
        ttsManager.stop()
        // processing will continue via onDone callback? 
        // No, stop() might flush. We need to explicitly trigger next if stop() doesn't fire onDone.
        processQueue() 
    }

    private fun stopPlayback() {
        isPlaying = false
        ttsManager.stop()
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

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Telegram Narrator")
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopPendingIntent)
            .build()
    }
    
    private fun updateNotification(item: PlaybackItem) {
        val text = when(item) {
             is PlaybackItem.Intro -> "Chat: ${item.chatName}"
             is PlaybackItem.MessageItem -> "Message from ${item.sender}"
             else -> "Playing..."
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
        ttsManager.shutdown()
    }
}
