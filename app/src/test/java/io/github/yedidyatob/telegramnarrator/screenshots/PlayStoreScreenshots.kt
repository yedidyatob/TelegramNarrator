package io.github.yedidyatob.telegramnarrator.screenshots

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.captureScreenRoboImage
import io.github.yedidyatob.telegramnarrator.R
import io.github.yedidyatob.telegramnarrator.core.service.UpNext
import io.github.yedidyatob.telegramnarrator.domain.about.AppVersionInfo
import io.github.yedidyatob.telegramnarrator.domain.audio.NowPlaying
import io.github.yedidyatob.telegramnarrator.domain.audio.PlaybackPosition
import io.github.yedidyatob.telegramnarrator.domain.edge.EdgeVoiceGender
import io.github.yedidyatob.telegramnarrator.domain.model.Chat
import io.github.yedidyatob.telegramnarrator.domain.model.MessageContentType
import io.github.yedidyatob.telegramnarrator.domain.tts.SpeechProvider
import io.github.yedidyatob.telegramnarrator.domain.tts.TestLanguage
import io.github.yedidyatob.telegramnarrator.ui.components.AboutDialog
import io.github.yedidyatob.telegramnarrator.ui.screens.home.ChatList
import io.github.yedidyatob.telegramnarrator.ui.screens.home.HomeTopBar
import io.github.yedidyatob.telegramnarrator.ui.screens.player.MiniPlayer
import io.github.yedidyatob.telegramnarrator.ui.screens.player.PlayerActions
import io.github.yedidyatob.telegramnarrator.ui.screens.player.PlayerAdActions
import io.github.yedidyatob.telegramnarrator.ui.screens.player.PlayerContent
import io.github.yedidyatob.telegramnarrator.ui.screens.settings.VoiceSettingsActions
import io.github.yedidyatob.telegramnarrator.ui.screens.settings.VoiceSettingsContent
import io.github.yedidyatob.telegramnarrator.ui.theme.TelegramNarratorTheme
import io.github.yedidyatob.telegramnarrator.ui.viewmodel.EdgeVoiceUiState
import io.github.yedidyatob.telegramnarrator.ui.viewmodel.EngineIndicator
import io.github.yedidyatob.telegramnarrator.ui.viewmodel.EngineLabel
import io.github.yedidyatob.telegramnarrator.ui.viewmodel.PlayerUiState
import io.github.yedidyatob.telegramnarrator.ui.viewmodel.TtsSettingsUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Clearly invented sample data: no real channels, people or logos. Avatars fall back to initials. */
data class SampleData(
    val chats: List<Chat>,
    val sender: String?,
    val message: String,
    val testLanguage: TestLanguage
)

private val EN = SampleData(
    chats = listOf(
        Chat(1, "Tech News Daily", 12),
        Chat(2, "Morning Briefing", 5),
        Chat(3, "Recipes & Cooking", 8),
        Chat(4, "Weekend Hiking Group", 3),
        Chat(5, "חדשות הבוקר", 6),
        Chat(6, "Book Club", 2),
        Chat(7, "Daily Fitness Tips", 4),
        Chat(8, "Neighborhood Updates", 7)
    ),
    sender = null,
    message = "A new open-source speech engine was released today. It runs fully offline, supports more than " +
        "thirty languages and gives each of them a natural-sounding voice.",
    testLanguage = TestLanguage.ENGLISH
)

private val HE = SampleData(
    chats = listOf(
        Chat(1, "חדשות הבוקר", 12),
        Chat(2, "טכנולוגיה היום", 5),
        Chat(3, "מתכונים טעימים", 8),
        Chat(4, "קבוצת הריצה", 3),
        Chat(5, "Tech News Daily", 4),
        Chat(6, "מועדון הספר", 2),
        Chat(7, "טיפים לכושר", 4),
        Chat(8, "עדכוני השכונה", 7)
    ),
    sender = null,
    message = "הבוקר נפתחה עונת הרחצה בחופי הארץ. מצילים יהיו בתפקיד משבע בבוקר ועד שבע בערב, " +
        "ומומלץ לרחוץ רק בחופים מוכרזים ולשמור על הילדים.",
    testLanguage = TestLanguage.HEBREW
)

/**
 * Play Store phone screenshots of the real Compose screens with fake data (1080x1920: 360x640 dp at xxhdpi).
 * Run with `./gradlew testDebugUnitTest -Pscreenshots`; then tools/play-assets/frame_screenshots.py.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class PlayStoreScreenshots(private val lang: String, private val data: SampleData) {
    @get:Rule val compose = createComposeRule()

    private fun capture(name: String) {
        val root = System.getProperty("screenshots.out") ?: "build/play-screenshots"
        compose.waitForIdle()
        captureScreenRoboImage(File(root, "$lang/$name.png").path)
    }

    private val nowPlaying get() = NowPlaying(
        chatId = data.chats[0].id,
        chatTitle = data.chats[0].title,
        content = NowPlaying.Content.Message(101, data.sender, data.message, MessageContentType.TEXT, isVoiceNote = false),
        chatPosition = PlaybackPosition(1, 5),
        messagePosition = PlaybackPosition(3, data.chats[0].unreadCount),
        messageCount = data.chats[0].unreadCount,
        engine = SpeechProvider.EDGE
    )

    private val playerState get() = PlayerUiState(
        isActive = true,
        nowPlaying = nowPlaying,
        engine = EngineIndicator(EngineLabel.EDGE)
    )

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun Home(playing: Boolean) {
        val chats = data.chats
        Scaffold(
            topBar = { HomeTopBar(onOpenVoiceSettings = {}, menuOpen = false, onMenuOpenChange = {}) },
            floatingActionButton = {
                if (!playing) {
                    ExtendedFloatingActionButton(
                        text = { Text(stringResource(R.string.home_btn_play_all)) },
                        icon = { Icon(Icons.Rounded.PlayArrow, contentDescription = null) },
                        onClick = {}
                    )
                }
            },
            bottomBar = {
                if (playing) MiniPlayer(playerState, onOpen = {}, onTogglePause = {}, onNextMessage = {}, onStop = {}, loadPhoto = { null })
            }
        ) { padding ->
            Column(Modifier.padding(padding).fillMaxSize()) {
                ChatList(
                    chats = chats,
                    selectedIds = chats.map { it.id }.toSet(),
                    playingChatId = if (playing) chats[0].id else null,
                    onToggle = {}, onToggleAll = {}, onOpen = {}, onPlay = {},
                    loadPhoto = { null }
                )
            }
        }
    }

    private fun show(content: @Composable () -> Unit) {
        compose.setContent { TelegramNarratorTheme(darkTheme = false) { Surface(Modifier.fillMaxSize()) { content() } } }
    }

    @Test fun s1_home() {
        show { Home(playing = false) }
        capture("01-home")
    }

    @Test fun s2_player() {
        show {
            PlayerContent(state = playerState, actions = PlayerActions(), adActions = PlayerAdActions(), loadPhoto = { null })
        }
        capture("02-player")
    }

    @Test fun s3_up_next() {
        val upNext = data.chats.take(5).mapIndexed { i, c -> UpNext.Item(i * 10, c.id, c.title, null, c.unreadCount, isCurrent = i == 0) }
        show {
            PlayerContent(
                state = playerState, actions = PlayerActions(), adActions = PlayerAdActions(), loadPhoto = { null },
                upNext = upNext, initiallyShowUpNext = true
            )
        }
        capture("03-up-next")
    }

    @Test fun s4_home_playing() {
        show { Home(playing = true) }
        capture("04-home-playing")
    }

    @Test fun s5_voice_settings() {
        show {
            VoiceSettingsContent(
                state = TtsSettingsUiState(
                    provider = SpeechProvider.EDGE,
                    edge = EdgeVoiceUiState(gender = EdgeVoiceGender.FEMALE),
                    testLanguage = data.testLanguage
                ),
                isPlaying = false,
                actions = VoiceSettingsActions(),
                modifier = Modifier.padding(top = 24.dp)
            )
        }
        capture("05-voice-settings")
    }

    @Test fun s6_about() {
        show {
            Home(playing = false)
            AboutDialog(onDismiss = {}, info = AppVersionInfo("1.0.0", 1, "release", "", ""))
        }
        capture("06-about")
    }
}

private val Int.dp get() = androidx.compose.ui.unit.Dp(this.toFloat())

@Config(sdk = [34], application = Application::class, qualifiers = "en-rUS-w360dp-h640dp-normal-long-notround-port-xxhdpi")
class EnglishPlayStoreScreenshots : PlayStoreScreenshots("en", EN)

@Config(sdk = [34], application = Application::class, qualifiers = "he-rIL-ldrtl-w360dp-h640dp-normal-long-notround-port-xxhdpi")
class HebrewPlayStoreScreenshots : PlayStoreScreenshots("he", HE)
