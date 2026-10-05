# Voice settings

Home screen -> gear icon opens the voice settings sheet.

## Text-to-speech engine

| Engine | Default | Network | Cost | Notes |
|--------|---------|---------|------|-------|
| **System** | ✅ | no | free | On-device Android TTS (also the fallback for the others) |
| **OpenAI** | | yes | paid (your key) | Bring-Your-Own-Key, see below |
| **Microsoft Edge neural voices** | | yes | $0 | **Experimental / unofficial**, see below |

The choice is stored as `provider` (`system` / `openai` / `edge`) in `tts_settings`. Builds from the OpenAI PR
stored an `openai_enabled` flag; it is migrated to `provider = openai` on first read.

## System TTS (default)

Everything uses the **system text-to-speech** engines installed on the device by default.

- **Speech engine**: the installed TTS engines (system default or a specific one). Switching restarts TextToSpeech.
  Voice choices are dropped when the engine changes (voice names belong to one engine).
- **Speech rate**: 0.5x - 2.0x in 0.1 steps, applied to all languages (also passed as OpenAI `speed` when that engine is on).
- **Voices**: per language - Hebrew first, then the device languages, then English. Pick a voice or
  "Default voice". Each language has a **Test voice** button (speaks a sample sentence in that language via system TTS).
- **Open system text-to-speech settings** (`Settings.ACTION_TTS_SETTINGS`) to install more voices.

## OpenAI TTS (optional, Bring-Your-Own-Key)

Off by default. When OpenAI is the selected engine **and** an API key is saved:

1. Cleaned message text is sent to `https://api.openai.com/v1/audio/speech`.
2. The MP3 is cached on disk under a SHA-256 of `(model + voice + text)` so replaying the same message does not re-bill.
3. Playback uses the existing `MediaPlayer` queue path (same as voice notes).

If there is no key, the request fails, or the text exceeds OpenAI's 4096-character limit, the app shows a short toast and **falls back to system TTS**.

Settings:

- Paste / clear API key (stored in EncryptedSharedPreferences; never logged)
- Model: `tts-1` (default, cheaper) or `tts-1-hd`
- Voice: `alloy`, `ash`, `coral`, `echo`, `fable`, `onyx`, `nova`, `sage`, `shimmer` (tts-1 / tts-1-hd set)

There is **no cloud key in the repo**. Get a key at [platform.openai.com/api-keys](https://platform.openai.com/api-keys).
Pricing: [OpenAI API pricing](https://openai.com/api/pricing/) (tts-1 ≈ $15 / 1M characters, tts-1-hd ≈ $30 / 1M).
**Warning:** the key leaves the device to `api.openai.com` when synthesizing.

## Microsoft Edge neural TTS (experimental)

> ⚠️ Unofficial: this is the endpoint behind Edge's "Read aloud", used the same way as the open-source
> [`edge-tts`](https://github.com/rany2/edge-tts) library. It is not a supported Microsoft API and may break or
> be blocked at any time. $0, no key, needs network.

1. `EdgeTtsClient` opens a WebSocket to
   `wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1?TrustedClientToken=…&ConnectionId=…&Sec-MS-GEC=…&Sec-MS-GEC-Version=…`
   (Edge browser headers, random `muid` cookie), sends `speech.config` (output `audio-24khz-48kbitrate-mono-mp3`)
   and an SSML request, and collects the binary `Path:audio` frames until `Path:turn.end`.
   `Sec-MS-GEC` is SHA-256 of the Windows file time (rounded to 5 minutes) + the public client token; a 403 with a
   `Date` header corrects the clock skew and retries once (as edge-tts does).
2. Text is XML-escaped, control characters removed, and split into ≤ 4096-byte requests whose MP3s are concatenated.
3. The MP3 is cached under `cacheDir/edge_tts/<sha256(voice + text)>.mp3` (LRU-trimmed to ~100 MB) and played
   through the `MediaPlayer` queue path. The speech-rate slider is applied with `PlaybackParams.setSpeed`, so it
   does not invalidate the cache.
4. Voices: `he-IL-AvriNeural` (default), `he-IL-HilaNeural`, `en-US-AvaMultilingualNeural`,
   `en-US-AndrewMultilingualNeural`, or any other Edge short name typed in (validated). With a Hebrew voice,
   messages `LanguageDetector` classifies as non-Hebrew use the multilingual voice of the same gender.
5. **Fallback:** connection error, handshake rejection, 15 s without data, or no audio → toast (throttled to one
   per 30 s) and system TTS for that message; Edge is then skipped for 60 s. An unplayable cached file is
   deleted and the message is read by system TTS.
6. **Test voice** in the Edge section fetches a sample sentence and plays it.

Pure logic (URL / token / SSML builders, text splitting, frame parsing, cache key, voice selection) is in
`domain/edge/EdgeTts.kt` and unit tested (`EdgeTtsTest`). An opt-in live smoke test hits the real service:
`EDGE_TTS_LIVE=1 ./gradlew testDebugUnitTest --tests '*EdgeTtsClientLiveTest*'`.

Known limitations: audio is fetched before playback starts (no streaming), so a very long *first* message still
waits for its own synthesis (roughly 4-5x faster than real time). While a cloud (OpenAI / Edge) item plays, the
next `CloudTtsPrefetch.COUNT` (default 2) speakable messages are synthesized into the existing disk cache so the
queue does not stall on each subsequent item. Prefetch is cancelled on skip / stop / pause / engine change and
is not used for the system-TTS path; network failures still fall back to system TTS as before.

## How it is applied

`TtsManager` still switches the language per utterance (`LanguageDetector`) for **system** TTS. After `setLanguage(...)` it applies the
voice chosen for **that language** if there is one (and it is still installed and offline); languages without a
choice use the engine's default voice. The choice is stored in SharedPreferences (`tts_settings`: `engine`, `rate`,
`voice.<language>`, `provider`, `openai_*`, `edge_voice`), see `TtsPreferences`. The OpenAI API key is in a separate encrypted prefs file
(`OpenAiKeyStore`). Pure logic (grouping, filtering, clamping, settings updates, cache keys) is in
`domain/tts/TtsVoiceLogic.kt` and `domain/openai/OpenAiTts.kt` and is unit tested.

Voice changes and test sentences are disabled while the reading is playing.

- **Mark messages as read in Telegram**: on by default. When enabled, messages are marked read in
  Telegram only after they were fully spoken (or skipped). Turn off to leave chats unread while testing.
  Details: [mark-as-read.md](mark-as-read.md).
