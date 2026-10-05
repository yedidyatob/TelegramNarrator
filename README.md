# Telegram Narrator

An Android app that reads your **unread Telegram messages aloud** (text-to-speech), with a focus on
Hebrew and mixed Hebrew/English channels. It logs in with your own Telegram account through
[TDLib](https://core.telegram.org/tdlib), lists the chats with unread messages, and plays them one after
another; messages are marked as read in Telegram only after they were actually spoken.

> Personal project, not published on Google Play (see [Publishing](#publishing-status)).

## Features

- Telegram login (phone number, code, optional 2FA password) via TDLib
- Unread chat list, "Play all" or play a single chat, pause/resume, skip message, skip chat
- Playback controls in the notification, on the lock screen, and with headset / Bluetooth media buttons
- Voice notes are played as audio, announced by sender
- Language is detected **per message** (Hebrew/English); the app's own phrases ("Message from ...") are spoken in
  Hebrew only when the content is Hebrew - see [docs/spoken-phrases.md](docs/spoken-phrases.md)
- Per-channel cleaning rules (ads, outros, signatures, link handling) - see [docs/channel-rules.md](docs/channel-rules.md)
- Voice engine / voice per language / speech rate - see [docs/tts-voice-settings.md](docs/tts-voice-settings.md)
- Links are skipped by default (spoken as "Link")

The UI is English only by design; only the spoken phrases have a Hebrew version.

## Build

Requirements: JDK 17 or 21, Android SDK (platform 34). Android Studio will set both up.

1. Get a Telegram **API id / API hash** at <https://my.telegram.org> (API development tools).
2. Add them to `local.properties` in the project root (this file is git-ignored):

   ```properties
   sdk.dir=/path/to/Android/sdk
   TELEGRAM_API_ID=1234567
   TELEGRAM_API_HASH=0123456789abcdef0123456789abcdef
   ```

   Without them the app shows an error on the login screen instead of starting.
3. Build / run:

   ```
   ./gradlew assembleDebug          # debug APK in app/build/outputs/apk/debug
   ./gradlew testDebugUnitTest      # JVM unit tests
   ./gradlew lintDebug              # Android lint (existing findings are in app/lint-baseline.xml)
   ./gradlew assembleRelease        # minified, unsigned (needs a signing config to install)
   ```

### Mark as read

Played (or deliberately skipped) messages are marked as read in Telegram by default in **both** debug and
release builds, so the unread badge drops and "Play from start" does not re-narrate already-heard messages.
Use the **"Mark messages as read in Telegram"** switch in *Voice settings* to turn this off while testing.
See [docs/mark-as-read.md](docs/mark-as-read.md) for the TDLib call sequence and quirks.

## Project layout

```
app/src/main/java/com/example/telegramnarrator
  core/       Android glue: PlaybackService (foreground service, notification, media session),
              TtsManager, DI modules
  data/       TDLib client + repositories (auth, chats), channel rules loader, TTS preferences
  domain/     Pure Kotlin: models, repository interfaces, audio queue, message cleaning, language detection,
              channel-rules engine (unit tested on the JVM)
  ui/         Jetpack Compose screens (auth, home, settings), ViewModels, theme
app/src/main/assets/channel_rules.json   per-channel cleaning rules
docs/                                     feature documentation
```

Stack: Kotlin, Jetpack Compose (Material 3), Hilt, Navigation Compose, Coroutines/Flow, TDLib 1.8.x,
Android `TextToSpeech`, `MediaSessionCompat`.

`TelegramReader_TDLib_Architecture.md` is the original design spec; the code has diverged from it (no encrypted
database, no use-case layer yet, no dedicated Player screen). The open polish work is tracked in the
"Polish roadmap" issue.

## Privacy & security

- Everything runs on the device: messages are read through TDLib and spoken by the local TTS engine; the app has
  no server and sends no analytics. Only Telegram itself is contacted.
- The Telegram session and message cache live in the app's private storage (`filesDir/tdlib`). Android backup is
  disabled, so the session is never uploaded or transferred; after a reinstall you log in again.
- The TDLib database is **not** encrypted at rest yet (tracked in the roadmap).
- Message text is never written to the log; debug logging is stripped from release builds.

## Publishing status

Not published. Before a Play release see the publishing checklist in the roadmap (applicationId is still
`com.example.telegramnarrator`, no signing config, no privacy policy, Telegram API terms).



## TDLib / 16 KB page size

Native TDLib comes from the JitPack artifact [`com.github.tdlibx:td:1.8.56`](https://github.com/tdlibx/td)
(`libtdjni.so` inside the AAR). There is no separately vendored copy under `app/libs`.

Google Play / Android 15+ require 16 KB page-size support for native code on 64-bit devices:

| ABI | ELF `LOAD` align (tdlibx 1.8.56) | Status |
|-----|----------------------------------|--------|
| arm64-v8a | `0x4000` (16 KB) | OK |
| x86_64 | `0x4000` (16 KB) | OK |
| armeabi-v7a | `0x1000` (4 KB) | Not 16 KB (32-bit; Play's 16 KB rule targets 64-bit) |
| x86 | `0x1000` (4 KB) | Same as above |

This project sets `packaging.jniLibs.useLegacyPackaging = true` so native libs are stored compressed
and extracted at install time (avoids the APK mmap ZIP 16 KB alignment requirement). That is not a
substitute for a correctly aligned `.so` on 16 KB devices — the 64-bit builds from tdlibx already
are aligned; a future TDLib rebuild with NDK r28+ (`-Wl,-z,max-page-size=16384`) is the durable fix
if Play or a device still rejects the package. Tracking: https://github.com/yedidyatob/TelegramNarrator/issues/45 .

## License

No license has been chosen yet, so all rights are reserved by the author until one is added.
