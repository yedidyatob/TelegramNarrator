# Screen off, battery and process death (#25)

How narration keeps going with the screen locked, under Doze and battery saver, and what happens when the
process is killed.

| Concern | What the app does |
|---|---|
| Keeping the process alive | `PlaybackService` is a foreground service of type `mediaPlayback` (`FOREGROUND_SERVICE_MEDIA_PLAYBACK`). It is started with `startForegroundService` only from a user tap in the app (Play / Play all) and calls `startForeground` immediately, before loading chats. A start that races with leaving the app (`ForegroundServiceStartNotAllowedException`, Android 12+) is logged and ignored. |
| CPU while the screen is off | `PlaybackWakeLock`: one `PARTIAL_WAKE_LOCK` held only while narration is **playing** (released on pause / stop / end), with a 15-minute timeout renewed per item. Audio output keeps the CPU awake by itself, but the gaps between items (Edge / Gemini synthesis, voice-note download, sponsored-message fetch) have no audio and the CPU could suspend there, so the next message would start late or never. Same reasoning as ExoPlayer's `WAKE_MODE_NETWORK`. Hence the `WAKE_LOCK` permission. |
| Network while the screen is off | A process with a foreground service is exempt from the Doze / App Standby / battery-saver network restrictions, so Edge, Gemini and TDLib keep working. No Wi-Fi lock: requests are short bursts. |
| Battery optimization exemption | Not requested. `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` is not allowed by Play policy for this use case (media playback is covered by the foreground service). If an OEM (some Samsung / Xiaomi builds) still kills playback, the user can exempt the app in system settings; this is a troubleshooting hint, not an in-app prompt. |
| Audio focus | Calls / navigation / other media pause narration; a transient loss resumes automatically when focus returns, a permanent loss does not (`AudioFocusPolicy`). |
| Headphones unplugged / Bluetooth disconnected | `ACTION_AUDIO_BECOMING_NOISY` pauses; regaining focus afterwards does not resume by itself. |
| Process death | The service returns `START_NOT_STICKY`: playback never restarts by itself. A control command that reaches a freshly recreated, idle service (stale notification button) stops it again instead of starting anything. The reading position is restored through Telegram itself: played messages are marked as read as they finish (read checkpoints, see [mark-as-read.md](mark-as-read.md)), so the next Play continues from the first message that was not heard. At most the last ~2 s of pending read checkpoints can be lost, in which case that message is read again. |
| Pause / resume position | Edge / Gemini resume exactly; system TTS resumes a few words before where it stopped (see [architecture.md](architecture.md)). Not kept across process death (the message is read again from its start). |

## Device test (acceptance of #25)
On at least two OEM devices (e.g. Pixel and Samsung or Xiaomi), with the engine set to Edge and then System:
1. Play all with ~30 minutes of unread messages, lock the screen, leave the phone still on a table for
   30 minutes. Narration must continue without gaps longer than the synthesis time.
2. Turn on battery saver and repeat for 5 minutes.
3. Unplug wired headphones / disconnect Bluetooth: narration pauses; it does not resume by itself.
4. Receive a call: narration pauses and resumes after the call.
5. Pause and leave paused for 10 minutes with the screen off; resume from the notification.
6. Kill the app from Developer options ("Stop app" / `adb shell am kill`) while playing: playback stops and
   does not restart. Open the app and Play all: reading continues from the first unheard message.
7. Check `adb shell dumpsys power | grep TelegramNarrator`: the wake lock is held while playing and not while
   paused or stopped.
