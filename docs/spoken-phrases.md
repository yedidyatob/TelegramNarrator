# Playback cues (spoken and sonic)

Most chrome around messages is **not** spoken anymore. The queue uses short language-neutral dings
and silent skips instead of TTS announcements.

| Cue | Behavior |
|-----|----------|
| Chat boundary | Short ding (`R.raw.chat_boundary_ding`). Silent intros (every message dropped by channel rules) skip the ding. Notification still shows the chat title. |
| Sender ("Message from X") | **Removed.** Message body is spoken as-is; the notification can still show the sender. |
| Voice note | **No** spoken "Voice note" label. If a downloadable file is available, MediaPlayer plays it in the queue. If not, skip silently (still mark read), like a caption-less photo. |
| End of messages | Distinct ding (`R.raw.end_of_messages_ding`) — not speech, not the chat-boundary tone — then stop. |
| Media-only / symbol-only | Photos, videos, stickers, … with no caption, and rows that are only symbols (`####`, `****`, `———`), are skipped silently but still marked read with the next spoken text (`ReadCheckpointer`). |

Message **body** language is still detected per utterance for TTS voice selection (`LanguageDetector` in `TtsManager`). The UI stays English; a few UI/link strings have Hebrew overrides in `values-he/` when the device locale is Hebrew (e.g. link replacement "Link" / "קישור").

See also [VoiceNotePlayback](../app/src/main/java/com/example/telegramnarrator/domain/audio/VoiceNotePlayback.kt) and [MessageSpeechBody](../app/src/main/java/com/example/telegramnarrator/domain/audio/MessageSpeechBody.kt).
