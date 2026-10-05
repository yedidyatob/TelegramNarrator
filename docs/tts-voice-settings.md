# Voice settings

Home screen -> gear icon opens the voice settings sheet.

## System TTS (default)

Everything uses the **system text-to-speech** engines installed on the device by default.

- **Speech engine**: the installed TTS engines (system default or a specific one). Switching restarts TextToSpeech.
  Voice choices are dropped when the engine changes (voice names belong to one engine).
- **Speech rate**: 0.5x - 2.0x in 0.1 steps, applied to all languages (also passed as OpenAI `speed` when that engine is on).
- **Voices**: per language - Hebrew first, then the device languages, then English. Pick a voice or
  "Default voice". Each language has a **Test voice** button (speaks a sample sentence in that language via system TTS).
- **Open system text-to-speech settings** (`Settings.ACTION_TTS_SETTINGS`) to install more voices.

## OpenAI TTS (optional, Bring-Your-Own-Key)

Off by default. When enabled **and** an API key is saved:

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

## How it is applied

`TtsManager` still switches the language per utterance (`LanguageDetector`) for **system** TTS. After `setLanguage(...)` it applies the
voice chosen for **that language** if there is one (and it is still installed and offline); languages without a
choice use the engine's default voice. The choice is stored in SharedPreferences (`tts_settings`: `engine`, `rate`,
`voice.<language>`, `openai_*`), see `TtsPreferences`. The OpenAI API key is in a separate encrypted prefs file
(`OpenAiKeyStore`). Pure logic (grouping, filtering, clamping, settings updates, cache keys) is in
`domain/tts/TtsVoiceLogic.kt` and `domain/openai/OpenAiTts.kt` and is unit tested.

Voice changes and test sentences are disabled while the reading is playing.

- **Mark messages as read in Telegram**: on by default. When enabled, messages are marked read in
  Telegram only after they were fully spoken (or skipped). Turn off to leave chats unread while testing.
  Details: [mark-as-read.md](mark-as-read.md).
