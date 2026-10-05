# Language of the spoken phrases

The app says a few phrases of its own around the messages: "Message from X: ...",
"Voice note", and "End of messages." These are **not** chosen by the device language and
the app UI stays English. The language is decided per utterance from the content
(`SpokenPhraseLanguage`, based on `LanguageDetector`):

| Phrase | Decided by (first text that has letters wins) |
|--------|-----------------------------------------------|
| Message from / Voice note | message text (or caption), then sender name, then chat title |
| End of messages | title of the last chat read |

Hebrew text -> the Hebrew phrases (`values-he/strings.xml`); anything else (English, Russian, Arabic) or no
letters at all -> the English phrases (`values/strings.xml`). Only spoken strings have a Hebrew translation.
The text itself is then spoken with the TTS voice for its own language (per-utterance detection in `TtsManager`).

## Chat boundary cue

When playback moves to another chat, the app plays a short **language-neutral ding**
(`R.raw.chat_boundary_ding`) instead of speaking "New chat: …" / "שיחה חדשה: …". The notification
still shows the chat title. Silent intros (every message dropped by channel rules) skip the ding.

## Media-only and symbol-only messages

Photos, videos, stickers, GIFs, documents, and similar items **with no caption** are not spoken as
"Photo" / "Video" placeholders. Rows whose cleaned text is **only symbols** (e.g. `####`, `****`,
`———` — no letters or digits) are skipped the same way. Playback still marks their message ids as
read when it passes them in the queue (batched with the next spoken text via `ReadCheckpointer`).
Captioned media are spoken normally (the caption). Voice notes are still announced and played as audio.
