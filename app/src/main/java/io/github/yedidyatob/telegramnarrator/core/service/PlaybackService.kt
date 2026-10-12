package io.github.yedidyatob.telegramnarrator.core.service

import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.content.ContextCompat
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
import io.github.yedidyatob.telegramnarrator.data.openai.OpenAiSpeechSynthesizer
import io.github.yedidyatob.telegramnarrator.data.rules.ChannelRulesRepository
import io.github.yedidyatob.telegramnarrator.data.tts.TtsPreferences
import io.github.yedidyatob.telegramnarrator.domain.audio.PlaybackItem
import io.github.yedidyatob.telegramnarrator.domain.audio.PlaybackManager
import io.github.yedidyatob.telegramnarrator.domain.repository.ChatRepository
import io.github.yedidyatob.telegramnarrator.domain.sponsored.SponsoredAd
import io.github.yedidyatob.telegramnarrator.domain.sponsored.SponsoredMessagesRepository
import io.github.yedidyatob.telegramnarrator.domain.sponsored.SponsoredSpeech
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
    @Inject lateinit var openAiSpeech: OpenAiSpeechSynthesizer
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
    // The controller's thread: all playback state lives on the main thread
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + logErrors)
    // Not cancelled in onDestroy(): the final mark-as-read flush and view reports must still be sent
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + logErrors)

    private lateinit var controller: NarrationController
    private lateinit var notifications: PlaybackNotifications
    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var focusController: AudioFocusController
    @Volatile private var foregroundStarted = false
    private var statusText = ""
    private var paused = false

    private var noisyReceiverRegistered = false
    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) controller.onBecomingNoisy()
        }
    }

    override fun onCreate() {
        super.onCreate()
        notifications = PlaybackNotifications(this).also { it.createChannel() }
        controller = NarrationController(
            scope = scope,
            io = Dispatchers.IO,
            buildQueue = BuildPlaybackQueue(chatRepository, { channelRules.engine }, { getString(R.string.playback_unknown_chat) })::invoke,
            speech = EngineSpeechPort(ttsManager, ttsPreferences, openAiSpeech, edgeSpeech),
            audio = MediaPlayerAudioPort(this),
            voiceNotes = { chatRepository.getVoiceFilePath(it) },
            readMarker = MarkMessagesRead(chatRepository, backgroundScope),
            sponsored = RepositorySponsoredPort(sponsoredMessages, backgroundScope),
            failures = TtsFailureNotifier(applicationContext),
            ui = playbackManager,
            host = this,
            clock = SystemClock::elapsedRealtime
        )
        focusController = AudioFocusController(this) { controller.onAudioFocusChange(it) }
        mediaSession = MediaSessionCompat(this, "PlaybackService").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() = controller.userResume()
                override fun onPause() = controller.userPause()
                override fun onStop() = controller.stop()
                override fun onSkipToNext() = controller.skipMessage()
                override fun onSkipToPrevious() = controller.skipChat()
            })
            isActive = true
        }
        onStateChanged(isPlaying = false, isPaused = false)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_ALL -> {
                // startForegroundService() requires startForeground() within ~5 s; loading chats can take longer
                enterForeground()
                controller.playChats(intent.getLongArrayExtra(EXTRA_CHAT_IDS) ?: longArrayOf())
            }
            ACTION_STOP -> controller.stop()
            ACTION_PAUSE -> controller.userPause()
            ACTION_RESUME -> controller.userResume()
            ACTION_SKIP_MSG -> controller.skipMessage()
            ACTION_SKIP_CHAT -> controller.skipChat()
            ACTION_PREVIOUS_MSG -> controller.previousMessage()
        }
        return START_NOT_STICKY
    }

    /** Shows the "preparing" foreground notification once per run. */
    private fun enterForeground() {
        if (foregroundStarted) return
        foregroundStarted = true
        statusText = getString(R.string.playback_preparing)
        val notification = notifications.build(statusText, paused, mediaSession)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(PlaybackNotifications.NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(PlaybackNotifications.NOTIFICATION_ID, notification)
        }
    }

    // ---- PlaybackHost ----

    override fun onItemStarted(item: PlaybackItem) {
        notifications.statusFor(item)?.let(::setStatusText)
    }

    override fun onSponsoredStarted(ad: SponsoredAd, chatName: String) = setStatusText(notifications.sponsoredStatus(ad, chatName))

    override fun onStateChanged(isPlaying: Boolean, isPaused: Boolean) {
        val wasPaused = paused
        paused = isPaused
        val state = when {
            !isPlaying -> PlaybackStateCompat.STATE_STOPPED
            isPaused -> PlaybackStateCompat.STATE_PAUSED
            else -> PlaybackStateCompat.STATE_PLAYING
        }
        mediaSession.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE or PlaybackStateCompat.ACTION_STOP or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                )
                .setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, if (state == PlaybackStateCompat.STATE_PLAYING) 1f else 0f)
                .build()
        )
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

    override fun registerNoisyReceiver() {
        if (noisyReceiverRegistered) return
        ContextCompat.registerReceiver(
            this, noisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), ContextCompat.RECEIVER_NOT_EXPORTED
        )
        noisyReceiverRegistered = true
    }

    override fun unregisterNoisyReceiver() {
        if (!noisyReceiverRegistered) return
        noisyReceiverRegistered = false
        unregisterReceiver(noisyReceiver)
    }

    /** "Sponsored" / "Recommended" in the ad's own language (Hebrew or English), like chat titles. */
    override fun sponsoredCue(ad: SponsoredAd): String = getString(
        when (SponsoredSpeech.cue(ad)) {
            SponsoredSpeech.Cue.SPONSORED_EN -> R.string.sponsored_cue_en
            SponsoredSpeech.Cue.SPONSORED_HE -> R.string.sponsored_cue_he
            SponsoredSpeech.Cue.RECOMMENDED_EN -> R.string.sponsored_cue_recommended_en
            SponsoredSpeech.Cue.RECOMMENDED_HE -> R.string.sponsored_cue_recommended_he
        }
    )

    private fun setStatusText(text: String) {
        playbackManager.setStatus(text)
        statusText = text
        refreshNotification()
    }

    private fun refreshNotification() {
        val text = if (paused) getString(R.string.playback_paused) else statusText
        getSystemService(NotificationManager::class.java)
            .notify(PlaybackNotifications.NOTIFICATION_ID, notifications.build(text, paused, mediaSession))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        controller.shutdown()
        focusController.abandon()
        unregisterNoisyReceiver()
        scope.cancel()
        mediaSession.isActive = false
        mediaSession.release()
    }
}
