# Mark as read (TDLib)

After a message finishes TTS (or is deliberately skipped), `PlaybackService` records it in
`ReadCheckpointer` and eventually calls `ChatRepository.markChatAsRead(chatId, messageIds)`.

## Call sequence

`TdLibChatRepository.markChatAsRead`:

1. **Gate**: `markAsReadEnabled` (`TtsSettings.markAsReadEnabled(BuildConfig.DEBUG)`), see
   [Release vs debug builds](#release-vs-debug-builds). Always true in release builds; in debug builds it is a
   no-op only when the Voice-settings switch is off.
2. **`OpenChat(chatId)`** (best-effort) — TDLib treats the chat as being viewed. Some channel /
   supergroup paths update `lastReadInboxMessageId` more reliably after this.
3. **`ViewMessages(chatId, messageIds, source=null, forceRead=true)`** — `forceRead=true` marks the
   ids even without an on-screen chat UI.
4. **`GetChat(chatId)`** + local cache refresh — so the home unread badge drops immediately and the
   next Play All does not rebuild the queue from a stale `lastReadInboxMessageId`.

`UpdateChatReadInbox` usually arrives afterwards and also refreshes the list; the explicit GetChat
avoids a race after pause/stop.

## Release vs debug builds

| Build | Voice settings switch | Behaviour |
|-------|-----------------------|-----------|
| Release | **Not shown** | Always marks played / skipped messages as read. A stored `mark_as_read = false` (e.g. from an older build) is **ignored**, and `TtsSettingsViewModel.setMarkAsRead` does nothing. |
| Debug | Shown (*Playback → Mark messages as read in Telegram*), on by default | Turning it off leaves chats unread, for device testing. |

Why: Telegram API Terms of Service 1.4 forbid "tampering with the 'read' statuses of messages (e.g. implementing
a 'ghost mode')". A user-facing "listen without marking as read" switch could be read that way, so the
published app does not offer one. The logic is `TtsSettings.markAsReadEnabled(isDebugBuild)` and
`TtsSettings.markAsReadSwitchVisible(isDebugBuild)` (unit tested in `MarkAsReadSettingTest`); the UI hides the
row via `TtsSettingsUiState.markAsReadSwitchVisible`.

The granular checkpoint below is unchanged: only messages that were actually read aloud (or deliberately
skipped) are marked, so stopping half-way leaves the rest unread — that is honest read state, not a ghost mode.

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

## Sponsored messages are never marked as read

Telegram's official sponsored messages (see [architecture.md](architecture.md#sponsored-messages)) are not chat
history and never go through `ReadCheckpointer` / `markChatAsRead`. The only call made for them is the **view
report**: `TdLibSponsoredMessagesSource.view` sends `ViewMessages(chatId, [sponsoredMessageId], source = null,
forceRead = false)` (TDLib 1.8.56 has no `viewSponsoredMessage`), at most once per ad per fetch, when the ad was
read aloud in full or its card's full text was on screen. `forceRead = false` so this never marks an ordinary
message as read. Skipping the ad (skip message / skip chat) sends nothing.
