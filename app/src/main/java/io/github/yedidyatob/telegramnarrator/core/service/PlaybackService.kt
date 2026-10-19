package io.github.yedidyatob.telegramnarrator.core.service

import android.content.Intent
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.DefaultMediaNotificationProvider
import android.os.SystemClock
import dagger.hilt.android.AndroidEntryPoint
import io.github.yedidyatob.telegramnarrator.R
import io.github.yedidyatob.telegramnarrator.core.diagnostics.AutoLog
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
import io.github.yedidyatob.telegramnarrator.domain.repository.AuthRepository
import io.github.yedidyatob.telegramnarrator.domain.model.AuthState
import io.github.yedidyatob.telegramnarrator.domain.sponsored.SponsoredAd
import io.github.yedidyatob.telegramnarrator.domain.sponsored.SponsoredMessagesRepository
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * Media3 MediaLibraryService that hosts the reading: it adapts Android (Media3 session + notification via
 * [NarrationPlayer], audio focus, becoming-noisy, TextToSpeech, MediaPlayer) to [NarrationController], which
 * holds all playback logic (#12). Exported for Android Auto (#41): [SessionControllerPolicy] rejects every
 * controller except this app, the system and the Auto hosts, and only this app and Auto may browse / play.
 */
@AndroidEntryPoint
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
class PlaybackService : MediaLibraryService(), PlaybackHost {
    @Inject lateinit var ttsManager: TtsManager
    @Inject lateinit var chatRepository: ChatRepository
    @Inject lateinit var authRepository: AuthRepository
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
        private const val UNREAD_TIMEOUT_MS = 5_000L
        /** After LoadChats, how long to wait for the first unread chats (none unread is a valid answer). */
        private const val CHATS_SETTLE_MS = 3_000L
        private val OWN_ACTIONS = setOf(
            ACTION_PLAY_ALL, ACTION_STOP, ACTION_PAUSE, ACTION_RESUME,
            ACTION_SKIP_MSG, ACTION_SKIP_CHAT, ACTION_PREVIOUS_MSG
        )
    }

    private val logErrors = CoroutineExceptionHandler { _, e -> android.util.Log.e("PlaybackService", "Playback error", e) }
    // The narration's thread: all playback state lives on the main thread
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + logErrors)
    // Not cancelled in onDestroy(): the final mark-as-read flush and view reports must still be sent
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + logErrors)

    private lateinit var narration: NarrationController
    private lateinit var notifications: PlaybackNotifications
    private lateinit var session: PlaybackSession
    private lateinit var player: NarrationPlayer
    private lateinit var focusController: AudioFocusController
    @Volatile private var foregroundStarted = false
    private var phase = NarrationPhase.IDLE
    private var statusTitle = ""
    private var paused = false
    private var playing = false
    private lateinit var wakeLock: PlaybackWakeLock

    // Media3 has no audio focus / becoming-noisy handling for a custom (non-ExoPlayer) player, so the narration
    // keeps its own AudioFocusPolicy and BecomingNoisyReceiver
    private val noisyReceiver = BecomingNoisyReceiver(this) { narration.onBecomingNoisy() }

    override fun onCreate() {
        AutoLog.event("PlaybackService onCreate")
        super.onCreate()
        notifications = PlaybackNotifications(this).also { it.createChannel() }
        wakeLock = PlaybackWakeLock(this)
        player = NarrationPlayer({ action ->
            when (action) {
                NarrationTransport.Action.RESUME -> narration.userResume()
                NarrationTransport.Action.PAUSE -> narration.userPause()
                NarrationTransport.Action.STOP -> narration.stop()
                NarrationTransport.Action.NEXT_MESSAGE -> narration.skipMessage()
                NarrationTransport.Action.NEXT_CHAT -> narration.skipChat()
                NarrationTransport.Action.PREVIOUS_MESSAGE -> narration.previousMessage()
            }
        }, getString(R.string.app_name))
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
        session = PlaybackSession(
            this, player,
            onNextChat = narration::skipChat,
            onStop = narration::stop,
            library = {
                SettableFuture.create<AutoBrowseTree.Library>().also { result ->
                    scope.launch { result.set(loadLibrary()) }
                }
            },
            onPlayChats = ::playChats
        )
        addSession(session.session)
        // Media3 builds the media notification (on our channel) and runs the service in the foreground while
        // the player is buffering / playing; a paused session keeps it for Media3's user-engaged timeout
        setMediaNotificationProvider(DefaultMediaNotificationProvider.Builder(this)
            .setChannelId(PlaybackNotifications.CHANNEL_ID)
            .setNotificationId(PlaybackNotifications.NOTIFICATION_ID)
            .build()
            .apply { setSmallIcon(R.drawable.ic_stat_narrator) })
        onStateChanged(isPlaying = false, isPaused = false)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Media3 notification / media-button intents must go through super (play/pause/next/custom)
        val action = intent?.action
        if (action !in OWN_ACTIONS) return super.onStartCommand(intent, flags, startId)

        if (ServiceCommands.stopsIdleService(action, foregroundStarted)) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        when (action) {
            ACTION_PLAY_ALL -> {
                // startForegroundService() requires startForeground() within ~5 s and loading chats can take
                // longer: a buffering player with playWhenReady makes Media3 start the foreground right away
                playChats((intent!!.getLongArrayExtra(EXTRA_CHAT_IDS) ?: longArrayOf()).toList())
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

    /**
     * Android Auto's view of Telegram: [AutoBrowseTree.Library.SignedOut] unless TDLib is authenticated and
     * the unread chats load in time (logged out, still starting, startup error, timeout).
     */
    private suspend fun loadLibrary(): AutoBrowseTree.Library = runCatching {
        val auth = withTimeoutOrNull(UNREAD_TIMEOUT_MS) { authRepository.authState.first { it !is AuthState.Initializing } }
        AutoLog.event("loadLibrary auth=${auth?.let { it::class.simpleName } ?: "timeout"}")
        if (auth !is AuthState.Authenticated) return@runCatching AutoBrowseTree.Library.SignedOut
        // Started by Android Auto without the app's UI, nothing has asked TDLib for the chat list yet: the
        // unread list would stay empty. Ask for it, then wait briefly for the first unread chats to arrive.
        runCatching { chatRepository.loadChats() }.onFailure { AutoLog.error("loadChats failed", it) }
        val unread = withTimeoutOrNull(CHATS_SETTLE_MS) { chatRepository.getUnreadChats().first { it.isNotEmpty() } }
            ?: chatRepository.getUnreadChats().first()
        AutoBrowseTree.Library.Ready(unread)
    }.getOrElse {
        AutoLog.error("loadLibrary failed", it)
        AutoBrowseTree.Library.SignedOut
    }

    /** The app's Play and Android Auto's browse-tree items both start here. */
    private fun playChats(chatIds: List<Long>) {
        foregroundStarted = true
        if (phase == NarrationPhase.IDLE) setPhase(NarrationPhase.PREPARING, getString(R.string.playback_preparing))
        narration.playChats(chatIds.toLongArray())
    }

    // ---- PlaybackHost ----

    override fun onItemStarted(item: PlaybackItem) {
        // Renews the wake lock's timeout for the new item
        wakeLock.update(ServiceCommands.holdsWakeLock(playing, paused))
        notifications.statusFor(item)?.let(::setStatusText)
    }

    override fun onSponsoredStarted(ad: SponsoredAd, chatName: String) = setStatusText(notifications.sponsoredStatus(ad, chatName))

    override fun onStateChanged(isPlaying: Boolean, isPaused: Boolean) {
        paused = isPaused
        playing = isPlaying
        wakeLock.update(ServiceCommands.holdsWakeLock(isPlaying, isPaused))
        setPhase(NarrationTransport.phaseOf(isPlaying, isPaused, phase))
    }

    override fun onStopped() {
        foregroundStarted = false
        setPhase(NarrationPhase.IDLE)
        stopSelf()
    }

    private fun setPhase(newPhase: NarrationPhase, title: String = statusTitle) {
        phase = newPhase
        statusTitle = title
        player.update(newPhase, title)
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
        setPhase(phase, text)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session.session

    override fun onDestroy() {
        super.onDestroy()
        narration.shutdown()
        wakeLock.update(false)
        focusController.abandon()
        noisyReceiver.unregister()
        scope.cancel()
        session.release()
    }
}
