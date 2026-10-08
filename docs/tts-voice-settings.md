# Voice settings

Home screen -> tune icon (and the Player's engine chip) opens the voice settings sheet (Material 3 bottom sheet). Layout, top to bottom:

```
Voice settings
──────────────────────────────────────────────
Engine                                (heading)
 (•) Edge     Free natural voices, needs internet
 ( ) System   On your phone, works offline
 ( ) Gemini   Your own key, paid
──────────────────────────────────────────────
<only the selected engine's section>
──────────────────────────────────────────────
Playback                              (heading)
 Speech rate                           1.0×
 ───────────●──────────
 Mark messages as read in Telegram     [on]   (debug builds only)
```

| Selected engine | Section shown | Contents |
|-----------------|---------------|----------|
| **System** | System voice | Speech engine dropdown, one voice dropdown per language, "only offline voices" line + **Open system TTS settings**, Test voice |
| **Edge** | Edge voice | **Male \| Female** toggle, helper line, Test voice, small info line, collapsed **Advanced** (custom voice name) |
| **Gemini** | Gemini voice | API key field (masked, show/hide), **Get an API key** link (opens aistudio.google.com/apikey in the browser), "Saved: AI…abcd" / "No key saved", Save / Remove key, voice dropdown, Test voice, privacy line |

The system engine/voice pickers are never shown for Edge or Gemini (they do not use them; system TTS is only
their silent fallback). While the system TTS engine (re)starts, only the System section shows a loading row;
the engine choice and Playback work immediately. Voice changes and tests are disabled while the reading plays.

### Test voice

Every engine section ends with a compact **language dropdown** next to **Test voice**: English, עברית, العربية,
Русский, Español, Français (sample sentences are string resources, `test_sentence_*`, never translated). The default
is the device UI language when it is in that list (legacy `iw` counts as Hebrew), otherwise English
(`TestLanguage.defaultFor`, `TestLanguageTest`). The choice is shared by all engines while the sheet is open.
Each engine speaks the sample with the voice it would really use for a message in that language:

| Engine | Voice used for the test |
|--------|-------------------------|
| System | The voice chosen for that language (or the engine default), via the same per-language switching as playback |
| Edge | Hebrew: Avri / Hila; any other language: the same-gender multilingual voice (Andrew / Ava); a custom voice always wins |
| Gemini | The selected voice (Gemini voices are multilingual) |

While the sample loads, the button is disabled and shows a small spinner: for Edge / Gemini until the audio is
synthesized (or found in the cache), for System until the engine starts speaking.

### "Preparing audio…" during playback

`PlaybackManager.isPreparingAudio` (`StateFlow<Boolean>`) is true while the current message's audio is not ready:
Edge / Gemini synthesis, a voice-note download, or system TTS before the utterance starts (`TtsManager.speak(onStart)`).
The Home now-playing bar then shows a small spinner and "Preparing audio…" instead of "Now playing". The flag only
turns on after 300 ms (`DelayedLoading`, `DelayedLoadingTest`), so cache hits and fast starts never flash it; it is
cleared on every new queue item, pause, skip and stop, and a superseded load's late completion is ignored.

Section visibility and Test-voice enablement are pure functions in `domain/tts/VoiceSettingsLogic.kt`
(`VoiceSettingsLogicTest`). The sheet is a stateless `VoiceSettingsContent(state, isPlaying, actions)` with
`@Preview`s in `VoiceSettingsPreviews.kt` (System, System loading, Edge, Edge testing in French, Edge + custom voice, Gemini, Gemini dark).

## Text-to-speech engine

| Engine | Default | Network | Cost | Notes |
|--------|---------|---------|------|-------|
| **Edge** (Microsoft neural voices) | ✅ new installs | yes | $0 | **Unofficial**, sends text and chat names to Microsoft, see below |
| **System** | updated installs | no | free | On-device Android TTS (also the fallback for the others) |
| **Gemini** | | yes | paid (your key) | Bring-Your-Own-Key, see below |

The choice is stored as `provider` (`system` / `gemini` / `edge`) in `tts_settings`. Builds from the OpenAI era
stored an `openai_enabled` flag; it is migrated to `provider = gemini` on first read.

**No stored choice** (`TtsPreferences`, `SpeechProvider.fromStored`): a fresh install (the package manager's
`firstInstallTime == lastUpdateTime`) starts on **Edge** (`SpeechProvider.DEFAULT`); an install updated from an
earlier version, where "nothing stored" meant the system voice, keeps **System** (`UPGRADE_DEFAULT`), so updating
never starts sending someone's messages to Microsoft. The resolved engine is saved immediately, so later updates
don't change it. Unknown stored ids fall back to System. The onboarding's voice step lists Edge first and
preselects the resolved engine.

## System TTS

The **system text-to-speech** engines installed on the device: offline, nothing is sent. Also the fallback
whenever Edge or Gemini fails.

- **Speech engine**: dropdown of the installed TTS engines (system default or a specific one). Switching restarts
  TextToSpeech (the section shows its loading row meanwhile). Voice choices are dropped when the engine changes
  (voice names belong to one engine).
- **Voices**: one dropdown per language - Hebrew first, then the device languages, then English. Pick a voice or
  "Default voice"; each voice shows its locale and quality. Only installed offline voices are listed.
- **Open system TTS settings** (`com.android.settings.TTS_SETTINGS`) to install more voices.
- **Test voice** speaks the sample in the selected test language with the voice chosen for that language and the
  current speech rate.

## Microsoft Edge neural TTS (unofficial)

> ⚠️ Unofficial: this is the endpoint behind Edge's "Read aloud", used the same way as the open-source
> [`edge-tts`](https://github.com/rany2/edge-tts) library. It is not a supported Microsoft API and may break or
> be blocked at any time. $0, no key, needs network. In the sheet this is one small info line ("Unofficial
> Microsoft service, free, needs internet; falls back to the system voice if unavailable").

**Voices: just Male | Female.** The app already switches language per message, so there is no Hebrew/English
voice choice:

| Message language (`LanguageDetector`) | Male (default) | Female |
|---------------------------------------|----------------|--------|
| Hebrew | `he-IL-AvriNeural` | `he-IL-HilaNeural` |
| Anything else | `en-US-AndrewMultilingualNeural` | `en-US-AvaMultilingualNeural` |

**Advanced** (collapsed by default): a custom Edge voice short name (`edge-tts --list-voices`, validated). When set,
it reads every message, overrides Male / Female (the toggle is disabled) and Advanced opens automatically.
**Clear** returns to Male / Female.

Stored as `edge_gender` (`male` / `female`) and `edge_custom_voice` in `tts_settings`. Migration from the old single
`edge_voice` key (read once when the new keys are absent, dropped on the next save): Avri / Andrew -> Male,
Hila / Ava -> Female, any other valid voice -> custom voice, missing / invalid -> Male. The voice a migrated user
hears does not change (`EdgeTtsTest`).

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
4. **Fallback:** connection error, handshake rejection, 15 s without data, or no audio → toast (throttled to one
   per 30 s) and system TTS for that message; Edge is then skipped for 60 s. An unplayable cached file is
   deleted and the message is read by system TTS.
5. **Test voice** fetches the sample in the selected test language with the voice Edge would use for it (Avri /
   Hila for Hebrew, Andrew / Ava multilingual otherwise, or the custom voice) and plays it.

Pure logic (URL / token / SSML builders, text splitting, frame parsing, cache key, gender -> voice, prefs migration) is in
`domain/edge/EdgeTts.kt` and unit tested (`EdgeTtsTest`). An opt-in live smoke test hits the real service:
`EDGE_TTS_LIVE=1 ./gradlew testDebugUnitTest --tests '*EdgeTtsClientLiveTest*'`.

Known limitations: audio is fetched before playback starts (no streaming), so a very long *first* message still
waits for its own synthesis (roughly 4-5x faster than real time). While a cloud (Gemini / Edge) item plays, the
next `CloudTtsPrefetch.COUNT` (default 2) speakable messages are synthesized into the existing disk cache so the
queue does not stall on each subsequent item. Prefetch is cancelled on skip / stop / pause / engine change and
is not used for the system-TTS path; network failures still fall back to system TTS as before.

## Gemini TTS (optional, Bring-Your-Own-Key)

Off by default. When Gemini is the selected engine **and** an API key is saved:

1. Cleaned message text is sent to `https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-preview-tts:generateContent`.
2. The WAV is cached on disk under a SHA-256 of `(model + voice + text)` so replaying the same message is not re-synthesized.
3. Playback uses the existing `MediaPlayer` queue path (same as voice notes).

If there is no key, the request fails, or the text exceeds Gemini's 4000-character limit (split automatically), the app shows a short toast and **falls back to system TTS**.
(All fallback toasts are string resources, `tts_fallback_*`; the synthesizers return a `FallbackReason`.)

Settings (Gemini section):

- **API key**: masked field with a show/hide button, then **Save key** / **Remove key**. The key is stored in
  EncryptedSharedPreferences (`GeminiKeyStore`) and never logged. The sheet only shows
  whether a key is saved and a masked hint (`Saved: AI…abcd`, first 3 + last 4 characters; `••••` for short
  keys). The raw key never enters the ViewModel's UI state; the text being typed lives only in the field and is
  not saved in instance state.
- **Voice**: Kore (default), Charon, Puck, Aoede, Fenrir, Leda, Orus, Zephyr
- **Test voice** synthesizes the sample in the selected test language with the selected voice; it is
  enabled only once a key is saved.

There is **no cloud key in the repo**. Get a key at [aistudio.google.com/apikey](https://aistudio.google.com/apikey) (free tier with quotas).
**Warning:** the key leaves the device to `generativelanguage.googleapis.com` when synthesizing.

## Playback (all engines)

- **Speech rate**: 0.5× - 2.0× in 0.1 steps, value shown next to the label; applied to all languages (system
  TTS rate, Gemini has fixed rate, Edge MediaPlayer speed).
- **Mark messages as read in Telegram** (debug builds only): on by default. When enabled, messages are
  marked read in Telegram only after they were fully spoken (or skipped). Turn off to leave chats unread
  while testing. Release builds do not show the switch and always mark played messages as read, ignoring a
  stored `mark_as_read = false` (Telegram API ToS 1.4, no "ghost mode"). Details: [mark-as-read.md](mark-as-read.md).

## How it is applied

`TtsManager` still switches the language per utterance (`LanguageDetector`) for **system** TTS. After `setLanguage(...)` it applies the
voice chosen for **that language** if there is one (and it is still installed and offline); languages without a
choice use the engine's default voice. The choices are stored in SharedPreferences (`tts_settings`: `engine`, `rate`,
`voice.<language>`, `mark_as_read`, `provider`, `gemini_voice`, `edge_gender`, `edge_custom_voice`), see
`TtsPreferences`. The Gemini API key is in a separate encrypted prefs file (`GeminiKeyStore`). Pure logic (grouping,
filtering, clamping, settings updates, cache keys, section visibility) is in `domain/tts/TtsVoiceLogic.kt`,
`domain/tts/VoiceSettingsLogic.kt`, `domain/edge/EdgeTts.kt` and `domain/gemini/GeminiTts.kt` and is unit tested.
