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
import io.github.yedidyatob.telegramnarrator.core.artwork.ChatArtworkProvider
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
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
    private val noisyReceiver = BecomingNoisyReceiver(this) {
        AutoLog.event("becoming noisy (audio route lost) phase=$phase: pause")
        narration.onBecomingNoisy()
    }

    override fun onCreate() {
        AutoLog.event("PlaybackService onCreate")
        super.onCreate()
        notifications = PlaybackNotifications(this).also { it.createChannel() }
        wakeLock = PlaybackWakeLock(this)
        player = NarrationPlayer(object : NarrationPlayer.Commands {
            override fun perform(action: NarrationTransport.Action) {
                // Who asked: the controller of the Media3 request being handled (onPlayerCommandRequest)
                if (action != NarrationTransport.Action.RESUME) {
                    AutoLog.event("transport $action from=${session.lastCommandFrom ?: "internal"} phase=$phase")
                }
                when (action) {
                    NarrationTransport.Action.RESUME -> narration.userResume()
                    NarrationTransport.Action.PAUSE -> narration.userPause()
                    NarrationTransport.Action.STOP -> narration.stop()
                    NarrationTransport.Action.NEXT_MESSAGE -> narration.skipMessage()
                    NarrationTransport.Action.NEXT_CHAT -> narration.skipChat()
                    NarrationTransport.Action.PREVIOUS_MESSAGE -> narration.previousMessage()
                }
            }

            override fun jumpToQueueIndex(index: Int) {
                val ids = NarrationQueue.chatsFrom(queue, index) ?: return
                AutoLog.event("queue jump index=$index of=${queue.size} from=${session.lastCommandFrom}")
                startRun(ids, keepQueue = true)
            }
        }, getString(R.string.app_name))
        player.artworkForChat = { ChatArtworkProvider.uriFor(this, it) }
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
        focusController = AudioFocusController(this) {
            AutoLog.event("audio focus change=$it phase=$phase")
            narration.onAudioFocusChange(it)
        }
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
        // Auto caches its browse list: refresh it whenever Home's unread list changes (new chat, chat read)
        scope.launch {
            chatRepository.getUnreadChats()
                .map { list -> list.map { it.id to it.unreadCount } }
                .distinctUntilChanged()
                .drop(1)
                .collect { list ->
                    AutoLog.event("unread list changed items=${list.size}: notifyChildrenChanged")
                    session.notifyUnreadChanged(list.size)
                }
        }
        // Media3 builds the media notification (on our channel) and runs the service in the foreground while
        // the player is buffering / playing; a paused session keeps it for Media3's user-engaged timeout
        setMediaNotificationProvider(DefaultMediaNotificationProvider.Builder(this)
            .setChannelId(PlaybackNotifications.CHANNEL_ID)
            .setNotificationId(PlaybackNotifications.NOTIFICATION_ID)
            .build()
            .apply { setSmallIcon(R.drawable.ic_notification) })
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
        if (action != ACTION_PLAY_ALL) AutoLog.event("app command $action phase=$phase")
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
        // The whole list, not the first chats TDLib delivers (#84 showed a partial list on a cold start)
        val unread = ChatListSettle.await(chatRepository.getUnreadChats())
        AutoBrowseTree.Library.Ready(unread)
    }.getOrElse {
        AutoLog.error("loadLibrary failed", it)
        AutoBrowseTree.Library.SignedOut
    }

    /** The app's Play and Android Auto's browse-tree items both start here. */
    private fun playChats(chatIds: List<Long>) = startRun(chatIds, keepQueue = false)

    /** The run's chats as Media3's queue (Android Auto "Up next"); a queue jump keeps the full list. */
    private var queue: List<NarrationQueue.Entry> = emptyList()

    private fun startRun(chatIds: List<Long>, keepQueue: Boolean) {
        foregroundStarted = true
        if (phase == NarrationPhase.IDLE) setPhase(NarrationPhase.PREPARING, getString(R.string.playback_preparing))
        if (!keepQueue) {
            val fallback = getString(R.string.playback_unknown_chat)
            queue = NarrationQueue.entries(chatIds, emptyMap(), fallback)
            player.setQueue(queue, chatIds.firstOrNull())
            scope.launch {
                val titles = chatIds.associateWith { id -> runCatching { chatRepository.getChat(id)?.title }.getOrNull().orEmpty() }
                val resolved = NarrationQueue.entries(chatIds, titles, fallback)
                if (queue.map { it.chatId } == resolved.map { it.chatId }) {
                    queue = resolved
                    player.setQueue(queue)
                }
            }
        } else {
            player.setCurrentChat(chatIds.firstOrNull())
        }
        narration.playChats(chatIds.toLongArray())
    }

    // ---- PlaybackHost ----

    override fun onItemStarted(item: PlaybackItem) {
        chatIdOf(item)?.let {
            player.setArtwork(ChatArtworkProvider.uriFor(this, it))
            player.setCurrentChat(it)
        }
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

    private fun chatIdOf(item: PlaybackItem): Long? = when (item) {
        is PlaybackItem.Intro -> item.chatId
        is PlaybackItem.ChatTitle -> item.chatId
        is PlaybackItem.MessageItem -> item.chatId
        is PlaybackItem.SponsoredSlot -> item.chatId
        else -> null
    }

    override fun onStopped() {
        AutoLog.event("narration stopped (end of queue or stop)")
        player.setArtwork(null)
        queue = emptyList()
        player.setQueue(emptyList(), null)
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
        val granted = focusController.request()
        AutoLog.event("audio focus request granted=$granted")
    }

    override fun abandonAudioFocus() = focusController.abandon()

    override fun registerNoisyReceiver() = noisyReceiver.register()

    override fun unregisterNoisyReceiver() = noisyReceiver.unregister()

    override fun sponsoredCue(ad: SponsoredAd): String = notifications.sponsoredCue(ad)

    private fun setStatusText(text: String) {
        playbackManager.setStatus(text)
        setPhase(phase, text)
    }

    /**
     * Closing the phone app (swiping it from Recents) must not touch playback that Android Auto (or the
     * notification) controls. Media3's default pauses every player and stops the service unless a session
     * "is playing", and our narration is not "playing" for Media3 while it prepares or moves between items
     * (BUFFERING), so swiping the app away paused Auto. Only an idle narration ends the service here.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        AutoLog.event("task removed phase=$phase playing=$playing paused=$paused")
        if (phase == NarrationPhase.IDLE) pauseAllPlayersAndStopSelf()
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
