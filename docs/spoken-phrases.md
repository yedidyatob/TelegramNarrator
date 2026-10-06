# Playback cues (spoken and sonic)

Most chrome around messages is **not** spoken anymore. The queue uses short language-neutral dings
and silent skips instead of TTS announcements.

| Cue | Behavior |
|-----|----------|
| Chat boundary | Short ding (`R.raw.chat_boundary_ding`), then **only the chat title** is spoken (no "New chat" words) with the selected engine (System / Edge / OpenAI), in the language of the title text (`LanguageDetector`: Hebrew voice for a Hebrew title, etc.). The title is cleaned like a message (`ChatTitleSpeech`: emoji, URLs, markdown and symbol-only parts such as `|` or `•` removed); if nothing speakable is left, only the ding plays. Silent intros (every message dropped by channel rules) skip both. Skip chat during the title skips the whole chat; skip message moves on to the first message; pause/resume behaves like a message. The title is never marked as read. Notification shows the chat title. |
| Sender ("Message from X") | **Removed.** Message body is spoken as-is; the notification can still show the sender. |
| Voice note | **No** spoken "Voice note" label. If a downloadable file is available, MediaPlayer plays it in the queue. If not, skip silently (still mark read), like a caption-less photo. |
| Sponsored message | After a channel's or bot chat's last unread message, before the next chat's ding: the official Telegram sponsored message (Telegram API ToS 3.3). A short cue, "Sponsored" / "ממומן" ("Recommended" / "מומלץ" when Telegram flags it), in the **ad's** language (Hebrew for a Hebrew ad, otherwise English; non-translatable strings `sponsored_cue_*` in `values`, so App Bundle language splits cannot drop the Hebrew cue), then the ad title and text cleaned by `MessageCleaner` (no URLs / emoji). The button text is not spoken and channel rules never apply. No ad → nothing. Never marked as read; see [architecture.md](architecture.md#sponsored-messages). |
| End of messages | Distinct ding (`R.raw.end_of_messages_ding`) — not speech, not the chat-boundary tone — then stop. |
| Media-only / symbol-only | Photos, videos, stickers, … with no caption, and rows that are only symbols (`####`, `****`, `———`), are skipped silently but still marked read with the next spoken text (`ReadCheckpointer`). |

Message **body** language is still detected per utterance for TTS voice selection (`LanguageDetector` in `TtsManager`). The UI stays English; a few UI/link strings have Hebrew overrides in `values-he/` when the device locale is Hebrew (e.g. link replacement "Link" / "קישור").

See also [VoiceNotePlayback](../app/src/main/java/io/github/yedidyatob/telegramnarrator/domain/audio/VoiceNotePlayback.kt) and [MessageSpeechBody](../app/src/main/java/io/github/yedidyatob/telegramnarrator/domain/audio/MessageSpeechBody.kt).
