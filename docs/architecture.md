# Architecture

This document describes how Telegram Narrator is **actually built today** and the **target architecture** it
is moving towards. It replaces the original design spec (`TelegramReader_TDLib_Architecture.md`), which
described components that were never built (encrypted TDLib database, `TtsRepository`, DataStore-backed
preferences, "Encrypted File Storage", Onboarding/Player screens, a use-case layer).

## At a glance

| | |
|---|---|
| Platform | Android, `minSdk` 26, `targetSdk`/`compileSdk` 34 |
| Language / UI | Kotlin, Jetpack Compose (Material 3), Navigation Compose, single `MainActivity` |
| DI | Hilt (`core/di/AppModule.kt` + `@Inject` constructors) |
| Telegram | TDLib 1.8.56 from JitPack (`com.github.tdlibx:td`), wrapped by `TdLibClient` |
| Speech | Android `TextToSpeech` (default); optional OpenAI TTS (BYOK) and experimental Microsoft Edge neural TTS |
| Playback | Foreground `PlaybackService` + `MediaSessionCompat`, `MediaPlayer` for voice notes, dings and cloud TTS audio |
| Persistence | TDLib's own database in `filesDir/tdlib` (**not** encrypted); settings in `SharedPreferences`; OpenAI key in `EncryptedSharedPreferences`; cloud-TTS audio in an LRU disk cache |
| Backend | None. The app talks only to Telegram, plus OpenAI / Microsoft if the user opts into those engines |

## Layers and packages

```
com.example.telegramnarrator
├── MainActivity, TelegramNarratorApp      Single activity + @HiltAndroidApp
├── ui/        Presentation (Compose)
│   ├── screens/auth/LoginScreen           phone → code → 2FA password
│   ├── screens/home/HomeScreen            unread chat list, multi-select, Play all / per chat, controls
│   ├── screens/settings/VoiceSettingsSheet engine / voice per language / rate / mark-as-read
│   ├── components/NotificationPermissionGate
│   ├── viewmodel/  AuthViewModel, HomeViewModel, TtsSettingsViewModel
│   └── theme/
├── domain/    Pure Kotlin (no Android types), unit-tested on the JVM
│   ├── model/        Chat, Message, AuthState, AudioState, MessageContentType, ApiCredentials
│   ├── repository/   AuthRepository, ChatRepository (interfaces)
│   ├── audio/        AudioQueue + PlaybackItem, MessageCleaner, MessageSpeechBody, LanguageDetector,
│   │                 ReadCheckpointer, PlaybackReadProgress, AudioFocusPolicy, VoiceNotePlayback,
│   │                 CloudTtsPrefetch, PlaybackSpeedCycle, PlaybackManager (shared playback UI state)
│   ├── cleaning/     ChannelRules model + ChannelRulesEngine
│   ├── home/         UnreadChatFilter, ChatSelectionLogic
│   ├── tts/          TtsSettings/TtsVoiceLogic, SpeechProvider, SpeechSynthesisOutcome
│   ├── openai/       OpenAiTts (models, voices, cost estimate)
│   └── edge/         EdgeTts (SSML/protocol helpers, voice mapping)
├── data/      Implementations that touch TDLib, Android or the network
│   ├── tdlib/TdLibClient            one TDLib client; updates as a SharedFlow, suspend send()
│   ├── repository/                  TdLibAuthRepository, TdLibChatRepository, TdLibUserCache, UnreadHistoryPager
│   ├── rules/                       ChannelRulesParser + ChannelRulesRepository (assets/channel_rules.json)
│   ├── tts/TtsPreferences           SharedPreferences (read synchronously by TtsManager)
│   ├── openai/                      OpenAiKeyStore, OpenAiSpeechClient/Synthesizer/Cache
│   ├── edge/                        EdgeTtsClient (OkHttp WebSocket), EdgeSpeechSynthesizer/Cache
│   └── speech/DiskAudioCache        shared LRU file cache for synthesized audio
└── core/      Android infrastructure
    ├── service/PlaybackService      foreground service: queue processing, notification, media session
    ├── audio/TtsManager             wraps TextToSpeech: engine, per-language voice, rate, utterance callbacks
    ├── audio/AudioFocusController   audio focus + becoming-noisy handling
    ├── di/AppModule
    └── MessageContentLabels
```

There is **no use-case layer**: ViewModels and `PlaybackService` call repositories and domain helpers directly.

## Main flows

**Login.** `AuthViewModel` observes `AuthRepository.authState` (driven by TDLib `updateAuthorizationState`).
`TdLibAuthRepository` sends `setTdlibParameters` with `TELEGRAM_API_ID` / `TELEGRAM_API_HASH` from
`BuildConfig` (injected from `local.properties`, or from Actions secrets in CI) and a database directory in
`filesDir/tdlib` with no encryption key. `AppNavigation` in `MainActivity` switches between the `login` and
`home` routes based on the auth state.

**Unread list.** `TdLibChatRepository.getUnreadChats()` turns TDLib chat updates into a `Flow<List<Chat>>`;
`UnreadChatFilter` and `ChatSelectionLogic` (via `PlaybackManager`) decide what is shown and selected.
Tapping a chat opens a preview sheet (`HomeViewModel.selectChat`) from which its messages can also be marked
read without playing them.

**Playback.** Home starts `PlaybackService` with `ACTION_PLAY_ALL` and the selected chat ids. The service:

1. loads each chat's oldest unread messages via `ChatRepository.getChatMessages` (paged by
   `UnreadHistoryPager`, up to 100 per chat),
2. applies the per-channel rules (`ChannelRulesEngine`: drop / cut / replace) and builds an `AudioQueue` of
   `PlaybackItem`s (`Intro` = chat-boundary ding, `MessageItem`, `Silence`, `Outro` = end ding),
3. plays each item, running the generic `MessageCleaner` and `MessageSpeechBody` (silent skip of media-only /
   symbol-only rows) just before speaking: voice notes and dings through `MediaPlayer`; text through the selected `SpeechProvider`
   (`SYSTEM` → `TtsManager`; `OPENAI` / `EDGE` → synthesize to an MP3 in the disk cache, prefetching the next
   messages while the current one plays, falling back to system TTS on failure),
4. records progress in `ReadCheckpointer` and marks messages read in batches via
   `ChatRepository.markChatAsRead` (only messages that were actually played or deliberately skipped; can be
   turned off in settings — see [mark-as-read.md](mark-as-read.md)).

Pause/resume/skip come from the Home UI, the notification, the lock screen and headset buttons
(`MediaSessionCompat` callbacks), and audio focus changes (`AudioFocusPolicy`).

## Known gaps vs. the original spec

| Original spec said | Reality |
|---|---|
| TDLib database "encrypted by default" | Not encrypted at rest (`databaseEncryptionKey = null`), see #15. Android backup is disabled. |
| `Preferences: DataStore` | `SharedPreferences` (`TtsPreferences`), because `TtsManager` needs values synchronously. The DataStore dependency is still declared in `app/build.gradle.kts` but unused. |
| `Security: Encrypted File Storage` | Only the optional OpenAI key uses `EncryptedSharedPreferences`. |
| `TtsRepository` | `TtsManager` (core) + `SpeechProvider` synthesizers (data). |
| Use cases (`GetUnreadChats`, `PlayChatMessages`, `LoginUser`) | None; logic lives in `PlaybackService`, ViewModels and domain helpers. |
| Screens: Onboarding, Login, ChatList, Player | Login, Home (chat list + controls), Voice settings sheet. No onboarding or Player screen (#20). |
| Spoken intros/outros | Language-neutral dings; sender labels are not spoken ([spoken-phrases.md](spoken-phrases.md)). |

## Target architecture

The direction (details and plans live in the linked issues, tracked by the "Polish roadmap" #40):

- **Split `PlaybackService`** (#12): it currently owns queue building, cleaning, speech, focus, read
  checkpoints and the notification. Move queue/playback orchestration into a testable controller (and,
  where it helps, small use cases such as "build narration queue" and "mark played messages read"), leaving
  the service as thin Android glue; then migrate to **Media3** (`MediaSessionService`).
- **TdLibClient robustness** (#13): no lost updates, cancellable sends, thread safety.
- **Encrypt the TDLib database** (#15) with a key held in the Android Keystore.
- **UI**: a dedicated Player screen, onboarding and Home empty/error states (#20).
- **Tooling currency** (#21): AGP/Kotlin/Compose BOM upgrades, Hilt via KSP, SDK 35.
- **Publishing readiness** (#24): real `applicationId`, signing config, privacy policy, Data safety form.

Until those land, keep new logic in `domain/` as pure Kotlin with JVM unit tests, and keep Android/TDLib/network
code in `data/` or `core/` behind interfaces, so the eventual split is mechanical.
