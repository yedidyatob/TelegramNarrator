package io.github.yedidyatob.telegramnarrator.core.service

import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import dagger.hilt.android.AndroidEntryPoint
import io.github.yedidyatob.telegramnarrator.R
import io.github.yedidyatob.telegramnarrator.core.audio.AudioFocusController
import io.github.yedidyatob.telegramnarrator.core.audio.TtsManager
import io.github.yedidyatob.telegramnarrator.core.playback.narration.BuildPlaybackQueue
import io.github.yedidyatob.telegramnarrator.core.playback.narration.MarkMessagesRead
import io.github.yedidyatob.telegramnarrator.core.playback.narration.NarrationController
import io.github.yedidyatob.telegramnarrator.core.playback.narration.PlaybackHost
import io.github.yedidyatob.telegramnarrator.core.tts.TtsFailureNotifier
import io.github.yedidyatob.telegramnarrator.data.edge.EdgeSpeechSynthesizer
import io.github.yedidyatob.telegramnarrator.data.gemini.GeminiSpeechSynthesizer
import io.github.yedidyatob.telegramnarrator.data.rules.ChannelRulesRepository
import io.github.yedidyatob.telegramnarrator.data.tts.TtsPreferences
import io.github.yedidyatob.telegramnarrator.domain.audio.PlaybackItem
import io.github.yedidyatob.telegramnarrator.domain.audio.PlaybackManager
import io.github.yedidyatob.telegramnarrator.domain.repository.ChatRepository
import io.github.yedidyatob.telegramnarrator.domain.sponsored.SponsoredAd
import io.github.yedidyatob.telegramnarrator.domain.sponsored.SponsoredMessagesRepository
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import javax.inject.Inject

/**
 * Foreground service that hosts the reading: it adapts Android (notification, MediaSession, audio focus,
 * becoming-noisy, TextToSpeech, MediaPlayer) to [NarrationController], which holds all playback logic (#12).
 */
@AndroidEntryPoint
class PlaybackService : Service(), PlaybackHost {
    @Inject lateinit var ttsManager: TtsManager
    @Inject lateinit var chatRepository: ChatRepository
    @Inject lateinit var playbackManager: PlaybackManager
    @Inject lateinit var channelRules: ChannelRulesRepository
    @Inject lateinit var geminiSpeech: GeminiSpeechSynthesizer
    @Inject lateinit var edgeSpeech: EdgeSpeechSynthesizer
    @Inject lateinit var ttsPreferences: TtsPreferences
    @Inject lateinit var sponsoredMessages: SponsoredMessagesRepository

    companion object {
        const val ACTION_PLAY_ALL = "ACTION_PLAY_ALL"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_PAUSE = "ACTION_PAUSE"
        const val ACTION_RESUME = "ACTION_RESUME"
        const val ACTION_SKIP_MSG = "ACTION_SKIP_MSG"
        const val ACTION_SKIP_CHAT = "ACTION_SKIP_CHAT"
        /** Replays the previous message of the current chat (restarts the current one on the chat's first). */
        const val ACTION_PREVIOUS_MSG = "ACTION_PREVIOUS_MSG"
        const val EXTRA_CHAT_IDS = "EXTRA_CHAT_IDS"
    }

    private val logErrors = CoroutineExceptionHandler { _, e -> android.util.Log.e("PlaybackService", "Playback error", e) }
    // The narration's thread: all playback state lives on the main thread
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + logErrors)
    // Not cancelled in onDestroy(): the final mark-as-read flush and view reports must still be sent
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + logErrors)

    private lateinit var narration: NarrationController
    private lateinit var notifications: PlaybackNotifications
    private lateinit var mediaSession: PlaybackMediaSession
    private lateinit var focusController: AudioFocusController
    @Volatile private var foregroundStarted = false
    private var statusText = ""
    private var paused = false
    private var playing = false
    private lateinit var wakeLock: PlaybackWakeLock

    private val noisyReceiver = BecomingNoisyReceiver(this) { narration.onBecomingNoisy() }

    override fun onCreate() {
        super.onCreate()
        notifications = PlaybackNotifications(this).also { it.createChannel() }
        wakeLock = PlaybackWakeLock(this)
        narration = NarrationController(
            scope = scope,
            io = Dispatchers.IO,
            buildQueue = BuildPlaybackQueue(chatRepository, { channelRules.engine }, { getString(R.string.playback_unknown_chat) })::invoke,
            speech = EngineSpeechPort(ttsManager, ttsPreferences, geminiSpeech, edgeSpeech),
            audio = MediaPlayerAudioPort(this),
            voiceNotes = { chatRepository.getVoiceFilePath(it) },
            readMarker = MarkMessagesRead(chatRepository, backgroundScope),
            sponsored = RepositorySponsoredPort(sponsoredMessages, backgroundScope),
            failures = TtsFailureNotifier(applicationContext),
            ui = playbackManager,
            host = this,
            clock = SystemClock::elapsedRealtime
        )
        focusController = AudioFocusController(this) { narration.onAudioFocusChange(it) }
        mediaSession = PlaybackMediaSession(this) { narration }
        onStateChanged(isPlaying = false, isPaused = false)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (ServiceCommands.stopsIdleService(intent?.action, foregroundStarted)) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        when (intent?.action) {
            ACTION_PLAY_ALL -> {
                // startForegroundService() requires startForeground() within ~5 s; loading chats can take longer
                enterForeground()
                narration.playChats(intent.getLongArrayExtra(EXTRA_CHAT_IDS) ?: longArrayOf())
            }
            ACTION_STOP -> narration.stop()
            ACTION_PAUSE -> narration.userPause()
            ACTION_RESUME -> narration.userResume()
            ACTION_SKIP_MSG -> narration.skipMessage()
            ACTION_SKIP_CHAT -> narration.skipChat()
            ACTION_PREVIOUS_MSG -> narration.previousMessage()
        }
        // Not restarted after the process is killed: playback never resumes by itself. The reading position is
        // kept by Telegram (played messages were marked as read), so the next Play continues where it stopped.
        return START_NOT_STICKY
    }

    /** Shows the "preparing" foreground notification once per run. */
    private fun enterForeground() {
        if (foregroundStarted) return
        foregroundStarted = true
        statusText = getString(R.string.playback_preparing)
        val notification = notifications.build(statusText, paused, mediaSession.session)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(PlaybackNotifications.NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(PlaybackNotifications.NOTIFICATION_ID, notification)
        }
    }

    // ---- PlaybackHost ----

    override fun onItemStarted(item: PlaybackItem) {
        // Renews the wake lock's timeout for the new item
        wakeLock.update(ServiceCommands.holdsWakeLock(playing, paused))
        notifications.statusFor(item)?.let(::setStatusText)
    }

    override fun onSponsoredStarted(ad: SponsoredAd, chatName: String) = setStatusText(notifications.sponsoredStatus(ad, chatName))

    override fun onStateChanged(isPlaying: Boolean, isPaused: Boolean) {
        val wasPaused = paused
        paused = isPaused
        playing = isPlaying
        wakeLock.update(ServiceCommands.holdsWakeLock(isPlaying, isPaused))
        mediaSession.setState(isPlaying, isPaused)
        if (isPlaying && foregroundStarted && wasPaused != isPaused) refreshNotification()
    }

    override fun onStopped() {
        foregroundStarted = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun requestAudioFocus() {
        focusController.request()
    }

    override fun abandonAudioFocus() = focusController.abandon()

    override fun registerNoisyReceiver() = noisyReceiver.register()

    override fun unregisterNoisyReceiver() = noisyReceiver.unregister()

    override fun sponsoredCue(ad: SponsoredAd): String = notifications.sponsoredCue(ad)

    private fun setStatusText(text: String) {
        playbackManager.setStatus(text)
        statusText = text
        refreshNotification()
    }

    private fun refreshNotification() {
        val text = if (paused) getString(R.string.playback_paused) else statusText
        getSystemService(NotificationManager::class.java)
            .notify(PlaybackNotifications.NOTIFICATION_ID, notifications.build(text, paused, mediaSession.session))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        narration.shutdown()
        wakeLock.update(false)
        focusController.abandon()
        noisyReceiver.unregister()
        scope.cancel()
        mediaSession.release()
    }
}
