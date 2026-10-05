# Voice settings (system TTS only)

Home screen -> gear icon opens the voice settings sheet. Everything uses the **system text-to-speech**
engines installed on the device; no cloud voices (voices that need a network connection or whose data is not
installed are not listed).

- **Speech engine**: the installed TTS engines (system default or a specific one). Switching restarts TextToSpeech.
  Voice choices are dropped when the engine changes (voice names belong to one engine).
- **Speech rate**: 0.5x - 2.0x in 0.1 steps, applied to all languages.
- **Voices**: per language - Hebrew first, then the device languages, then English. Pick a voice or
  "Default voice". Each language has a **Test voice** button (speaks a sample sentence in that language).
- **Open system text-to-speech settings** (`Settings.ACTION_TTS_SETTINGS`) to install more voices.

## How it is applied

`TtsManager` still switches the language per utterance (`LanguageDetector`). After `setLanguage(...)` it applies the
voice chosen for **that language** if there is one (and it is still installed and offline); languages without a
choice use the engine's default voice. The choice is stored in SharedPreferences (`tts_settings`: `engine`, `rate`,
`voice.<language>`), see `TtsPreferences`. Pure logic (grouping, filtering, clamping, settings updates) is in
`domain/tts/TtsVoiceLogic.kt` and is unit tested.

Voice changes and test sentences are disabled while the reading is playing.

- **Mark messages as read in Telegram**: on by default. When enabled, messages are marked read in
  Telegram only after they were fully spoken (or skipped). Turn off to leave chats unread while testing.
  Details: [mark-as-read.md](mark-as-read.md).
