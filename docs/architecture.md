# Architecture

This document describes how Telegram Narrator is **actually built today** and the **target architecture** it
is moving towards. It replaces the original design spec (`TelegramReader_TDLib_Architecture.md`), which
described components that were never built (`TtsRepository`, DataStore-backed preferences, "Encrypted File
Storage", a use-case layer; the Onboarding and Player screens came later with #20) and claimed an encrypted TDLib database long before #15 added one.

## At a glance

| | |
|---|---|
| Platform | Android, `minSdk` 26, `targetSdk`/`compileSdk` 36; `applicationId` `io.github.yedidyatob.telegramnarrator` |
| Language / UI | Kotlin, Jetpack Compose (Material 3), Navigation Compose, single `MainActivity` |
| DI | Hilt (`core/di/AppModule.kt` + `@Inject` constructors) |
| Telegram | TDLib 1.8.56 from JitPack (`com.github.tdlibx:td`), wrapped by `TdLibClient` |
| Speech | Android `TextToSpeech` (default); optional OpenAI TTS (BYOK) and experimental Microsoft Edge neural TTS |
| Playback | Foreground `PlaybackService` + `MediaSessionCompat`, `MediaPlayer` for voice notes, dings and cloud TTS audio |
| Persistence | TDLib's own database in `filesDir/tdlib`, encrypted with a Keystore-wrapped key (`TdLibDatabaseKeyStore`); settings in `SharedPreferences`; OpenAI key in `EncryptedSharedPreferences`; cloud-TTS audio in an LRU disk cache |
| Backend | None. The app talks only to Telegram, plus OpenAI / Microsoft if the user opts into those engines |

## Layers and packages

```
io.github.yedidyatob.telegramnarrator
├── MainActivity, TelegramNarratorApp      Single activity + @HiltAndroidApp
├── ui/        Presentation (Compose)
│   ├── navigation/AppRoute                routes + where the auth state redirects (onboarding once, login, home)
│   ├── screens/onboarding/OnboardingScreen first run: what the app does, privacy, voice engine, notifications
│   ├── screens/auth/LoginScreen           phone (country picker, PhoneNumberField) → code → 2FA password
│   ├── screens/home/HomeScreen            unread chat list (avatars), multi-select, Play all / per chat,
│   │                                      loading / empty / error / offline states, connection banner, preview sheet
│   ├── screens/player/PlayerScreen        full now-playing screen; MiniPlayer (bottom of Home)
│   ├── screens/settings/VoiceSettingsSheet engine / voice per language / rate / mark-as-read
│   ├── components/NotificationPermissionGate, SponsoredCard (+ report option dialog), ChatAvatar
│   ├── text/       ContentDirection (direction of user content), bidiSafe
│   ├── viewmodel/  AuthViewModel, AppNavigationViewModel, OnboardingViewModel, HomeViewModel,
│   │               PlayerViewModel (+ PlayerUiState), TtsSettingsViewModel, SponsoredViewModel
│   └── theme/
├── domain/    Pure Kotlin (no Android types), unit-tested on the JVM
│   ├── model/        Chat, Message, AuthState, AudioState, MessageContentType, ApiCredentials
│   ├── repository/   AuthRepository, ChatRepository (interfaces)
│   ├── audio/        AudioQueue + PlaybackItem, MessageCleaner, MessageSpeechBody, LanguageDetector,
│   │                 ReadCheckpointer, PlaybackReadProgress, AudioFocusPolicy, VoiceNotePlayback,
│   │                 CloudTtsPrefetch, PlaybackSpeedCycle, PlaybackManager (shared playback UI state),
│   │                 NowPlaying, PlaybackPlan (message / chat positions), ChatPlaybackHistory (previous)
│   ├── sponsored/    SponsoredMessagesRepository (cache, view-once, click / report), SponsoredAd, SponsoredSpeech,
│   │                 SponsoredLinkPolicy, SponsoredReportFlow, SponsoredAdCache, SponsoredViewTracker
│   ├── cleaning/     ChannelRules model + ChannelRulesEngine
│   ├── home/         UnreadChatFilter, ChatSelectionLogic, HomeStateMapper (loading / empty / error / offline)
│   ├── connection/   ConnectionStatus + ConnectionMonitor (TDLib connection state)
│   ├── onboarding/   OnboardingFlow (steps, shown once, Hebrew voice check)
│   ├── auth/         PhoneCountryResolver, PhoneNumberNormalizer (libphonenumber), PhoneCountries, PhoneFieldValidation
│   ├── tts/          TtsSettings/TtsVoiceLogic, SpeechProvider, SpeechSynthesisOutcome
│   ├── openai/       OpenAiTts (models, voices, cost estimate)
│   ├── security/     DatabaseEncryptionPolicy (which key to try / re-key / reset), WrappedKeyCodec
│   └── edge/         EdgeTts (SSML/protocol helpers, voice mapping)
├── data/      Implementations that touch TDLib, Android or the network
│   ├── tdlib/TdLibClient            one TDLib client; updates as a SharedFlow, suspend send(), latest connection state
│   ├── connection/TdLibConnectionMonitor  UpdateConnectionState → ConnectionStatus
│   ├── onboarding/OnboardingPreferences   "onboarding done" flag (SharedPreferences)
│   ├── tdlib/TdLibDatabaseKeyStore  database key wrapped by an Android Keystore AES-GCM key
│   ├── repository/                  TdLibAuthRepository, TdLibChatRepository, TdLibUserCache, UnreadHistoryPager
│   ├── sponsored/                   TdLibSponsoredMessagesSource (getChatSponsoredMessages, viewMessages, click, report)
│   ├── rules/                       ChannelRulesParser + ChannelRulesRepository (assets/channel_rules.json)
│   ├── auth/DeviceCountrySignals    SIM / network / locale country for the login default country
│   ├── tts/TtsPreferences           SharedPreferences (read synchronously by TtsManager)
│   ├── openai/                      OpenAiKeyStore, OpenAiSpeechClient/Synthesizer/Cache
│   ├── edge/                        EdgeTtsClient (OkHttp WebSocket), EdgeSpeechSynthesizer/Cache
│   └── speech/DiskAudioCache        shared LRU file cache for synthesized audio
└── core/      Android infrastructure
    ├── service/PlaybackService      foreground service: queue processing, notification, media session
    ├── playback/PlaybackController  UI → service commands (play, pause, previous / next message, next chat, stop)
    ├── audio/TtsManager             wraps TextToSpeech: engine, per-language voice, rate, utterance callbacks
    ├── audio/AudioFocusController   audio focus + becoming-noisy handling
    ├── di/AppModule
    └── MessageContentLabels
```

There is **no use-case layer**: ViewModels and `PlaybackService` call repositories and domain helpers directly.

## Main flows

**Login.** `AuthViewModel` observes `AuthRepository.authState` (driven by TDLib `updateAuthorizationState`).
The phone step (#56) has a searchable country picker (flag + dial code); the default country comes from
`PhoneCountryResolver` (SIM country, then network country if the device has telephony, then the locale region, else
none; read by `data/auth/DeviceCountrySignals`, no permission). `PhoneNumberNormalizer` (libphonenumber) turns a local
number with or without the trunk prefix, or a pasted `+…` number (which selects its own country), into E.164 for
`setAuthenticationPhoneNumber`, and drives the inline length validation. The number row stays left-to-right in RTL
locales (dial code + digits read as one phone number).
`TdLibAuthRepository` sends `setTdlibParameters` with `TELEGRAM_API_ID` / `TELEGRAM_API_HASH` from
`BuildConfig` (injected from `local.properties`, or from Actions secrets in CI), a database directory in
`filesDir/tdlib`, and the database encryption key from `TdLibDatabaseKeyStore`. `DatabaseEncryptionPolicy`
decides the order of keys to try: a database from before encryption is opened with the empty key once and
re-keyed with `setDatabaseEncryptionKey`. If no key opens it, the database is reset and the user logs in again;
it is never reset while the Keystore is unavailable. `AppNavigation` in `MainActivity` switches between the
`onboarding`, `login`, `home` and `player` routes based on the auth state (`AppRoute.redirect`).

**Onboarding (#20).** Shown once, before the login, to users who are not signed in (someone already signed in, e.g.
after an update, never sees it): what the app does; the unofficial-app notice and what stays on the device, with the
privacy policy link; the voice engine (System, recommended, with a check for an offline Hebrew voice and a shortcut to
the system TTS settings; Edge; OpenAI with an inline API-key field), written to `TtsPreferences`; and on Android 13+
the notification permission (the step is left out when it is already granted). Skip, or finishing, sets the
`OnboardingPreferences` flag and the login follows.

**Unread list.** `TdLibChatRepository.getUnreadChats()` turns TDLib chat updates into a `Flow<List<Chat>>`;
`UnreadChatFilter` and `ChatSelectionLogic` (via `PlaybackManager`) decide what is shown and selected.
Tapping a chat opens a preview sheet (`HomeViewModel.selectChat`) from which it can be played, or its messages
marked read without playing them. `HomeStateMapper` turns the list, the `LoadChats` result (a 20 s timeout counts as
a failure; TDLib's 404 "everything loaded" does not) and the `ConnectionMonitor` status into one body state:
chats whenever there are any, otherwise offline (waiting for network), loading (also while connecting, so "no
unread chats" is never claimed too early), error (with Telegram's message and Try again) or empty. Connection
problems that last more than 1.5 s show a banner above the list; when the connection is back the list reloads by
itself. A failed refresh with chats on screen only shows a snackbar.

**Player (#20).** `PlaybackService` publishes a `NowPlaying` (chat, avatar, the message being read, its position from
`PlaybackPlan` — only chats and messages that are actually heard count —, the engine speaking) through
`PlaybackManager`; `PlayerUiState` adds pause / preparing / the sponsored ad / the engine indicator (a system voice
standing in for Edge or OpenAI is shown as a fallback). The mini player at the bottom of Home opens the Player
screen (slide up; closes with ⌄, the predictive back gesture or when playback ends): avatar, chat position, the
message in its own direction, message position, previous / play-pause / next message (laid out left-to-right in RTL
too, like media controls), next chat and stop. Previous (`ACTION_PREVIOUS_MSG`, `ChatPlaybackHistory`) stays within
the chat: it replays the message before the current one, or restarts the chat's first message. Speed stays in Voice
settings only.

**Playback.** Home starts `PlaybackService` with `ACTION_PLAY_ALL` and the selected chat ids. The service:

1. loads each chat's oldest unread messages via `ChatRepository.getChatMessages` (paged by
   `UnreadHistoryPager`, up to 100 per chat),
2. applies the per-channel rules (`ChannelRulesEngine`: drop / cut / replace) and builds an `AudioQueue` of
   `PlaybackItem`s (`Intro` = chat-boundary ding, `ChatTitle` = spoken chat title, `MessageItem`, `SponsoredSlot` =
   the chat's official sponsored message, `Silence`, `Outro` = end ding),
3. plays each item, running the generic `MessageCleaner` and `MessageSpeechBody` (silent skip of media-only /
   symbol-only rows) just before speaking: voice notes and dings through `MediaPlayer`; text through the selected `SpeechProvider`
   (`SYSTEM` → `TtsManager`; `OPENAI` / `EDGE` → synthesize to an MP3 in the disk cache, prefetching the next
   messages while the current one plays, falling back to system TTS on failure),
4. records progress in `ReadCheckpointer` and marks messages read in batches via
   `ChatRepository.markChatAsRead` (only messages that were actually played or deliberately skipped; always on in
   release builds, a debug-only switch can turn it off — see [mark-as-read.md](mark-as-read.md)).

Pause/resume/skip come from the mini player and Player screen, the notification, the lock screen and headset buttons
(`MediaSessionCompat` callbacks), and audio focus changes (`AudioFocusPolicy`).

## Sponsored messages

Telegram API Terms of Service 3.3: "If your app allows accessing content from Telegram channels, you must include
support for official sponsored messages in Telegram channels and may not interfere with this functionality."
Implementation details follow <https://core.telegram.org/api/sponsored-messages>, mapped to TDLib 1.8.56.

| Piece | What it does |
|---|---|
| `TdLibSponsoredMessagesSource` (data) | `GetChat` / `GetUser` to tell channels (`ChatTypeSupergroup.isChannel`) and bots (`UserTypeBot`) from other chats; `GetChatSponsoredMessages`; views via `ViewMessages(chatId, [id], null, forceRead = false)` after a best-effort `OpenChat` (this TDLib has no `viewSponsoredMessage`); `ClickChatSponsoredMessage`; `ReportChatSponsoredMessage`; `DownloadFile` for the sponsor photo / media |
| `SponsoredMessagesRepository` (domain, singleton) | 5-minute per-chat cache (`SponsoredAdCache`; each fetch gets a fetch id), chat kind remembered, errors not cached; `reportViewed` at most once per ad per fetch (`SponsoredViewTracker`, retried if the request failed); click / report wrappers (a reported or expired ad leaves the cache, "ads hidden" clears it) |
| Queue (`PlaybackService`) | `SponsoredSlotPlacement` adds a `SponsoredSlot` after a non-silent chat's last message, before the next chat's ding. The `Intro` of such a chat prefetches its ads. At the slot: `adFor(chatId)` (5 s timeout; no ad / not a channel or bot → passed silently), the card is shown (`PlaybackManager.sponsoredAd`), the notification says "Sponsored · {title}" (or "Recommended · …") and `SponsoredSpeech` speaks the cue in the ad's language ("Sponsored" / "ממומן", "Recommended" / "מומלץ"; `sponsored_cue_*` strings, kept in `values` so App Bundle language splits cannot drop the Hebrew ones) followed by the `MessageCleaner`-cleaned title and text — never the button text, never the channel rules. When the utterance finishes, the view is reported (also with the screen off). Skip message / skip chat / pause behave like a message; nothing is marked as read |
| `SponsoredCard` + `SponsoredViewModel` (ui) | Card above the now-playing bar from when the ad is spoken until the next chat, a new Play All, a report or 5 minutes; also at the bottom of the chat's preview sheet (opening the sheet "opens" the chat and fetches). Label ("Sponsored" / "Recommended", "Ad" in bot chats) and title in the TDLib accent color (built-in ids 0-6, else the theme color), sponsor photo, text with formatting entities, media only once downloaded (photo; JPEG thumbnail / cover of GIFs and videos), button. Opening the link reports a click (`isMediaClick` for photo / GIF media; videos have no fullscreen player here, so their thumbnail is not clickable); non-Telegram hosts need confirmation (`SponsoredLinkPolicy`), `t.me` / `tg:` links open in a Telegram app. ⋮ menu: Sponsor info (when present), About these ads (<https://ads.telegram.org>), Report (when `canBeReported`) with Telegram's option dialog(s) and result toasts (`SponsoredReportFlow`). A view is reported when the label, title and text are entirely inside the window while the app is resumed |

Test with <https://t.me/SecretAdTestChannel> (join it in Telegram first): Telegram always returns an ad there.

## Known gaps vs. the original spec

| Original spec said | Reality |
|---|---|
| TDLib database "encrypted by default" | Encrypted since #15 with a random key wrapped by the Android Keystore (TDLib's default empty key is not a secret). Android backup is disabled. |
| `Preferences: DataStore` | `SharedPreferences` (`TtsPreferences`), because `TtsManager` needs values synchronously. The unused DataStore dependency was removed. |
| `Security: Encrypted File Storage` | TDLib database encrypted (above); the optional OpenAI key uses `EncryptedSharedPreferences`. |
| `TtsRepository` | `TtsManager` (core) + `SpeechProvider` synthesizers (data). |
| Use cases (`GetUnreadChats`, `PlayChatMessages`, `LoginUser`) | None; logic lives in `PlaybackService`, ViewModels and domain helpers. |
| Screens: Onboarding, Login, ChatList, Player | Onboarding, Login, Home (chat list + mini player), Player, Voice settings sheet (#20). |
| Spoken intros/outros | Dings; after the chat-boundary ding only the chat title is spoken; sender labels are not spoken ([spoken-phrases.md](spoken-phrases.md)). |

## Target architecture

The direction (details and plans live in the linked issues, tracked by the "Polish roadmap" #40):

- **Split `PlaybackService`** (#12): it currently owns queue building, cleaning, speech, focus, read
  checkpoints and the notification. Move queue/playback orchestration into a testable controller (and,
  where it helps, small use cases such as "build narration queue" and "mark played messages read"), leaving
  the service as thin Android glue; then migrate to **Media3** (`MediaSessionService`).
- **TdLibClient robustness** (#13): no lost updates, cancellable sends, thread safety.
- **Keep less data on disk**: consider `useMessageDatabase = false` (follow-up from #15).
- **UI** (#20): the Player screen, onboarding and Home states are in. Still open there: sleep timer, an up-next queue
  list, chat filters (muted / archived) and opening the Player from the notification.
- **Tooling currency** (#21): AGP/Kotlin/Compose BOM upgrades, Hilt via KSP (SDK 36 is already in, on AGP 8.2.1 with
  `android.suppressUnsupportedCompileSdk`).
- **Publishing readiness** (#24): done in code and docs (`applicationId`, release signing, versioning, privacy
  policy, Data safety answers, see [publishing.md](publishing.md)). Still open: Telegram sponsored messages in
  channels (API terms 3.3) and the tag-triggered release workflow.

Until those land, keep new logic in `domain/` as pure Kotlin with JVM unit tests, and keep Android/TDLib/network
code in `data/` or `core/` behind interfaces, so the eventual split is mechanical.
