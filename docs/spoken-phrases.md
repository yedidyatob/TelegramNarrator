# Language of the spoken phrases

The app says a few phrases of its own around the messages: "New chat: X", "Message from X: ...",
"Voice note", "Photo", ... and "End of messages." These are **not** chosen by the device language and
the app UI stays English. The language is decided per utterance from the content
(`SpokenPhraseLanguage`, based on `LanguageDetector`):

| Phrase | Decided by (first text that has letters wins) |
|--------|-----------------------------------------------|
| New chat | chat title |
| Message from / Voice note / Photo / Sticker ... | message text (or caption), then sender name, then chat title |
| End of messages | title of the last chat read |

Hebrew text -> the Hebrew phrases (`values-he/strings.xml`); anything else (English, Russian, Arabic) or no
letters at all -> the English phrases (`values/strings.xml`). Only spoken strings have a Hebrew translation.
The text itself is then spoken with the TTS voice for its own language (per-utterance detection in `TtsManager`).
