# Mark as read (TDLib)

After a message finishes TTS (or is deliberately skipped), `PlaybackService` records it in
`ReadCheckpointer` and eventually calls `ChatRepository.markChatAsRead(chatId, messageIds)`.

## Call sequence

`TdLibChatRepository.markChatAsRead`:

1. **Gate**: no-op when the Voice-settings switch is off (`markAsReadEnabled` / `TtsSettings.markAsReadOverride`).
   Default is **on** for both debug and release builds.
2. **`OpenChat(chatId)`** (best-effort) — TDLib treats the chat as being viewed. Some channel /
   supergroup paths update `lastReadInboxMessageId` more reliably after this.
3. **`ViewMessages(chatId, messageIds, source=null, forceRead=true)`** — `forceRead=true` marks the
   ids even without an on-screen chat UI.
4. **`GetChat(chatId)`** + local cache refresh — so the home unread badge drops immediately and the
   next Play All does not rebuild the queue from a stale `lastReadInboxMessageId`.

`UpdateChatReadInbox` usually arrives afterwards and also refreshes the list; the explicit GetChat
avoids a race after pause/stop.

## Why debug APKs used to look "broken"

Until this fix, `markAsReadEnabled` defaulted to **off** in debug builds (`markAsReadOverride ?: !isDebugBuild`),
and the Voice-settings Switch was not wired into the UI despite strings/ViewModel support. Device
testing with `assembleDebug` therefore never called `ViewMessages`, the Telegram unread count stayed
unchanged, and "Play from start" reloaded the same unread history.

## Queue / checkpoint behaviour (unchanged)

- Only messages passed to `ReadCheckpointer.messagePlayed` are marked; pause/stop flush pending
  batches but do **not** mark the interrupted in-progress message.
- `PlaybackReadProgress.afterMessageCompleted` clears `currentItem` so a concurrent pause cannot
  re-queue a finished message.
- Replay-from-start skips already-heard messages because TDLib's unread window starts after
  `lastReadInboxMessageId` once mark-as-read succeeded — there is no separate local "heard ids" store.
