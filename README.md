# Telegram Narrator

**Unofficial Telegram Narrator** (the app's display name) is an Android app that reads your **unread Telegram messages aloud** (text-to-speech), with a focus on
Hebrew and mixed Hebrew/English channels. It logs in with your own Telegram account through
[TDLib](https://core.telegram.org/tdlib), lists the chats with unread messages, and plays them one after
another; messages are marked as read in Telegram only after they were actually spoken.

> Unofficial app: it uses the Telegram API but is not made, endorsed or supported by Telegram.
> Not published on Google Play yet (see [Publishing](#publishing)).

## Features

- Telegram login (phone number with country picker, code, optional 2FA password) via TDLib; local formats like `052-123-4567` are accepted
- Unread chat list, "Play all" or play a single chat, pause/resume, skip message, skip chat
- Playback controls in the notification, on the lock screen, and with headset / Bluetooth media buttons
- Voice notes are played as audio in the queue (no spoken "Voice note" / sender label); if unplayable, skipped silently but still marked read
- Language is detected **per message** for TTS voice selection (Hebrew/English) - see [docs/spoken-phrases.md](docs/spoken-phrases.md)
- Chat boundaries play a ding followed by the spoken chat title (in the title's own language); end-of-queue plays a distinct ding (no spoken "New chat" / "End of messages")
- Media-only and symbol-only messages (photo/video without caption, or rows like `####`) are skipped silently but still marked read with the next spoken text
- Per-channel cleaning rules (ads, outros, signatures, link handling) - see [docs/channel-rules.md](docs/channel-rules.md)
- Voice engine / voice per language / speech rate - see [docs/tts-voice-settings.md](docs/tts-voice-settings.md)
- Optional Bring-Your-Own-Key **OpenAI TTS** (`tts-1` / `tts-1-hd`, cached on device; system TTS remains default) - see [docs/tts-voice-settings.md](docs/tts-voice-settings.md)
- **Experimental** Microsoft Edge neural voices (just pick Male or Female; Hebrew and other languages switch automatically): free, no key, unofficial - see [below](#experimental-microsoft-edge-neural-tts)
- Links are skipped by default (spoken as "Link")

The UI is English only by design; a few labels (e.g. link replacement) have Hebrew overrides when the device locale is Hebrew.

## Build

Requirements: JDK 17 or 21, Android SDK (platform 36). Android Studio will set both up.

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
   ./gradlew assembleRelease        # minified APK, unsigned unless a keystore is configured
   ./gradlew bundleRelease          # Play bundle: app/build/outputs/bundle/release/app-release.aab
   ```

   Release signing and versioning (`TN_VERSION_NAME` in `gradle.properties`) are described in
   [docs/publishing.md](docs/publishing.md).

   Unit tests are pure JVM tests (no device or emulator needed). The live Edge TTS network test is skipped
   unless you opt in with `EDGE_TTS_LIVE=1 ./gradlew testDebugUnitTest`. CI (`.github/workflows/ci.yml`) runs
   `testDebugUnitTest assembleDebug` on every push to `main` and every pull request.

### Optional: OpenAI TTS (Bring-Your-Own-Key)

System TTS is the default. No OpenAI key is required or shipped in the repo.

1. Create an API key at <https://platform.openai.com/api-keys> (you pay OpenAI directly).
2. In the app: **Voice settings** → **Engine** → **OpenAI**. Paste the key (tap the eye icon to check it) → **Save key**
   (the sheet then shows `Saved: sk-…abcd`) → pick **Standard (tts-1)** or **HD (tts-1-hd)** and a voice →
   **Test voice** plays a sample in the language picked next to it. **Remove key** deletes it from the device.
3. Pricing: <https://openai.com/api/pricing/> (tts-1 ≈ $15 per 1M characters, tts-1-hd ≈ $30 per 1M).
4. **Warning:** when synthesizing, the key and cleaned message text leave the device to `api.openai.com`. The key is stored in EncryptedSharedPreferences and never logged. Identical messages reuse an on-device audio cache so they are not billed again.

### Experimental: Microsoft Edge neural TTS

> ⚠️ **Experimental and unofficial.** This uses the endpoint behind Microsoft Edge's "Read aloud" feature,
> the same approach as the open-source [`edge-tts`](https://github.com/rany2/edge-tts) Python library. It is
> **not a supported Microsoft API**: it can break or be blocked at any time without notice.

- **Cost: $0.** No account, no API key (the only token involved is the public constant built into Edge).
- **Needs a network connection.** Cleaned message text is sent to Microsoft (`speech.platform.bing.com`).
- In the app: **Voice settings** → **Engine** → **Edge**, then choose **Male** or **Female** and tap **Test voice**
  (pick the sample language next to it; it defaults to your phone's language).
  Hebrew messages use Avri (Hila for female); messages in other languages switch automatically to the Andrew
  (Ava for female) multilingual voices. To force one specific Edge voice for every message, open **Advanced** and
  type its short name (`edge-tts --list-voices`); this overrides Male / Female until you clear it.
- Audio is cached on device by hash of text + voice (LRU, ~100 MB), so replays do not hit the network.
  The speech-rate slider is applied at playback.
- **Fallback:** on any failure (offline, timeout, service change) the app shows a short toast and reads the
  message with system TTS; Edge is then skipped for a minute so an offline phone does not wait for a timeout
  before every message.
- System TTS remains the default.

## Install on phone without USB

Every green CI run on `main` (and on pull requests) uploads a debug APK as a workflow artifact.

1. Open the repo on GitHub → **Actions**.
2. Open the latest successful **CI** run on `main` (green check).
3. Scroll to **Artifacts** → download **TelegramNarrator-debug**.
4. Unzip the download; you get `TelegramNarrator-debug.apk`.
5. Copy the APK to the phone (Drive, Nearby Share, email, etc.) and open it to install.
6. If Android blocks the install, enable **Install unknown apps** for the app you used to open the APK (Files, Chrome, Drive, …).

Artifacts are kept for about **14 days**.

For a working login, set repository Actions secrets `TELEGRAM_API_ID` and `TELEGRAM_API_HASH` (from <https://my.telegram.org>). CI writes them into `local.properties` before `assembleDebug`. Without those secrets the APK still builds, but the login screen shows an API-credentials error.

### Mark as read

Played (or deliberately skipped) messages are marked as read in Telegram, so the unread badge drops and
"Play from start" does not re-narrate already-heard messages. Messages not reached yet stay unread.
In **release** builds this is always on and there is no switch (Telegram API Terms of Service 1.4 forbid a
"ghost mode"); any "off" value stored by an older build is ignored. **Debug** builds keep the
**"Mark messages as read in Telegram"** switch in *Voice settings* so you can leave chats unread while testing.
See [docs/mark-as-read.md](docs/mark-as-read.md) for the TDLib call sequence and quirks.

## Project layout

```
app/src/main/java/io/github/yedidyatob/telegramnarrator
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

See [docs/architecture.md](docs/architecture.md) for the layers, the main flows (login, unread list, playback,
mark-as-read), how the code differs from the original design spec, and the target architecture. The open polish
work is tracked in the [Polish roadmap](https://github.com/yedidyatob/TelegramNarrator/issues/40) issue.

## Documentation

| Doc | Topic |
|-----|-------|
| [docs/architecture.md](docs/architecture.md) | Current structure, data flow, known gaps, target architecture |
| [docs/channel-rules.md](docs/channel-rules.md) | Per-channel cleaning rules (`assets/channel_rules.json`) |
| [docs/tts-voice-settings.md](docs/tts-voice-settings.md) | Engines, voice per language, speech rate, OpenAI / Edge TTS |
| [docs/spoken-phrases.md](docs/spoken-phrases.md) | Playback cues (dings, silent skips) and per-message language detection |
| [docs/mark-as-read.md](docs/mark-as-read.md) | Mark-as-read behaviour and the TDLib call sequence |
| [docs/publishing.md](docs/publishing.md) | Google Play release: application ID, versioning, upload keystore, Play App Signing, signed AAB in CI |
| [docs/privacy-policy.md](docs/privacy-policy.md) | Privacy policy (to host publicly for the Play listing and the in-app link) |
| [docs/play-data-safety.md](docs/play-data-safety.md) | Play Data safety answers, store listing drafts (EN/HE), content rating, Telegram API terms checklist |

## Privacy & security

- By default everything runs on the device: messages are read through TDLib and spoken by the local TTS engine; the app has
  no server and sends no analytics. Only Telegram itself is contacted.
- **Optional OpenAI TTS**: if you enable it and paste your own API key, cleaned message text is sent to
  `api.openai.com` to synthesize speech. The key is stored in EncryptedSharedPreferences on device and is never
  logged or shipped in the repo. Get a key at <https://platform.openai.com/api-keys>. Pricing:
  <https://openai.com/api/pricing/> (tts-1 ≈ $15/1M characters, tts-1-hd ≈ $30/1M). Synthesized audio is cached
  on disk by hash of text+voice+model so the same message is not billed twice.
- **Experimental Edge TTS**: if you select it, cleaned message text is sent to Microsoft's unofficial Edge
  "Read aloud" endpoint (`speech.platform.bing.com`); no key or account is involved. Audio is cached on device.
- The Telegram session and message cache live in the app's private storage (`filesDir/tdlib`). Android backup is
  disabled, so the session is never uploaded or transferred; after a reinstall you log in again.
- The TDLib database (session, chats, message cache) is **encrypted at rest** with a random 32-byte key that is
  wrapped by a non-exportable Android Keystore key and stored in `noBackupFilesDir`. Existing unencrypted
  databases are re-keyed in place on first start, so you stay logged in. If the Keystore key is ever lost, the
  database is reset and you log in again.
- Message text is never written to the log; debug logging is stripped from release builds. The OpenAI API key is never logged.
- Full details: [docs/privacy-policy.md](docs/privacy-policy.md).

## Publishing

Not published yet. The app is prepared for Google Play (`applicationId` `io.github.yedidyatob.telegramnarrator`,
`targetSdk` 36, release signing from `local.properties` / env vars, R8 + resource shrinking, privacy policy and
Data safety answers). The remaining manual steps (upload keystore, hosting the privacy policy, Play Console forms,
closed test) and the open Telegram API terms item (sponsored messages in channels) are listed in
[docs/publishing.md](docs/publishing.md) and [docs/play-data-safety.md](docs/play-data-safety.md).

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

[MIT](LICENSE) © Yedidya Toberman.
