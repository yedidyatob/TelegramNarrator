# Privacy Policy: Unofficial Telegram Narrator

_Last updated: October 6, 2026_

Unofficial Telegram Narrator ("the app") is an Android app, developed by Yedidya Toberman ("I", "me"), that reads
your unread Telegram messages aloud. This policy explains what data the app handles, where it goes, and how you
can delete it.

**The app is unofficial.** It is built on the [Telegram API](https://core.telegram.org/api) and is not made,
endorsed or supported by Telegram.

## Summary

- The app has **no servers of its own**. I (the developer) never receive your phone number, your messages, your
  contacts or anything else from the app.
- **No analytics, no crash reporting, no tracking SDKs, and no ads of the app's own.** Your data is not sold or
  shared for advertising and is not used to train AI models. In channels and bot chats the app shows Telegram's
  official sponsored messages, because Telegram requires it (see section 2a).
- Your Telegram data is exchanged **only with Telegram's servers** and is stored **on your device**, encrypted.
- **The default voice engine on a new install is Microsoft Edge, an online service:** with it, the text of the
  messages being read and the chat titles are sent to Microsoft for speech synthesis. You can switch to the
  **System** voice, which works entirely on your device, in the first-run introduction or at any time in the voice
  settings. (Installs updated from a version that used the System voice keep it.) OpenAI is optional and needs your
  own API key.

## What the app handles and where it goes

### 1. Your Telegram account

To log in you enter your **phone number**, the **login code** Telegram sends you and, if you use two-step
verification, your **Telegram password**. The app passes them directly to Telegram's servers through
[TDLib](https://core.telegram.org/tdlib), Telegram's official client library. The app keeps the resulting Telegram
session on your device so that you stay logged in.

### 2. Your chats and messages

To find and read your unread messages the app downloads, from Telegram, your chat list (chat names, unread
counts, chat photos shown as avatars), the text of unread messages, sender names and voice messages. This data is stored by TDLib in a
database in the app's private storage on your device. That database is **encrypted** with a random key that is
itself protected by the Android Keystore of your device.

When a message has been read aloud (or skipped), the app tells Telegram to **mark it as read**, like opening the
chat in any Telegram app would. Only messages that were actually read aloud (or skipped) are marked; messages you
haven't reached yet stay unread. This cannot be turned off, because Telegram's API terms do not allow apps to hide
that messages were read.

Your use of Telegram is also subject to [Telegram's Privacy Policy](https://telegram.org/privacy).

### 2a. Telegram sponsored messages

Telegram requires every app that shows channel content to show its official sponsored messages
([Telegram API Terms of Service](https://core.telegram.org/api/terms), section 3.3). When you play a channel or a
chat with a bot, or open its preview, the app asks Telegram for that chat's sponsored message, reads it aloud
after the chat's messages (marked "Sponsored" or "Recommended") and shows it on screen. The app tells Telegram,
through TDLib, when an ad was **viewed** (read aloud in full, or its text fully shown on screen), when you
**open its link**, and when you **report** it; these reports contain only the chat and the ad, nothing else
from your device. The ads are selected and served by Telegram
([About these ads](https://ads.telegram.org), [Telegram's Privacy Policy](https://telegram.org/privacy)); the
developer does not choose them, receives nothing about them and earns nothing from them. If you use an online
voice engine, the ad's text is sent to it like a message.

### 3. Speech synthesis (text-to-speech)

You choose the voice engine in the first-run introduction and, at any time, in the app's voice settings. A new
install starts with **Edge**; an install updated from an earlier version keeps the engine it had (the System voice
unless you had chosen another one). If Edge or OpenAI cannot be reached, that message is read with the System voice.

| Engine | What leaves your device | Recipient |
|---|---|---|
| **System** | Nothing is sent by the app. The text is handed to the text-to-speech engine installed on your phone (for example Google Speech Services). The app lists only offline voices; what the engine itself does is governed by its own provider's policy. | None |
| **Edge** (default on new installs; unofficial) | The cleaned text of each message to be spoken and the title of each chat being read, the chosen voice name and a random identifier generated for each request. Like any internet connection, your IP address is visible to the service. | Microsoft, through the online service behind the "Read aloud" feature of the Microsoft Edge browser ([Microsoft Privacy Statement](https://privacy.microsoft.com/privacystatement)). This is not an official Microsoft API for third-party apps. |
| **OpenAI** (optional, your own API key) | The cleaned text of each message to be spoken and the title of each chat being read, the chosen voice and model, and your OpenAI API key (to authorize the request). | OpenAI ([Privacy Policy](https://openai.com/policies/privacy-policy), [API data usage](https://openai.com/policies/api-data-usage-policies)). Requests are billed to your own OpenAI account. |

"Cleaned text" means the message text after the app removes links, ads and similar parts it does not read
aloud. Before a chat's messages the app speaks the chat's title (the group or channel name, or, for a one-on-one
chat, the other person's name, without emoji and symbols), so with an online engine that title is sent too.
Sender names of individual messages are not sent unless they appear in the message text.
While one message plays, the app may already send the next few queued messages so that playback has no gaps.

To save data and money, audio produced by Edge or OpenAI is **cached on your device** (in the app's cache
folder), named by a one-way hash of the text, so that the same message is not sent again.

### 4. Settings and your OpenAI API key

Voice and playback settings, and whether you have finished the first-run introduction, are stored on your device. If you enter an OpenAI API key it is stored on your
device **encrypted** (Android EncryptedSharedPreferences) and is sent only to OpenAI. You can remove it at any
time with **Remove key** in the voice settings.

### 5. Backups

The app opts out of Android cloud backup and device-to-device transfer, so the Telegram session, the message
database, your settings and your API key are never copied to Google Drive or to another phone.

## Permissions

| Permission | Why |
|---|---|
| Internet | Connect to Telegram (including its sponsored messages) and to the online voice service in use (Edge by default, or OpenAI) |
| Foreground service (media playback) | Keep reading aloud while the screen is off or another app is open |
| Notifications (optional) | Show playback controls (pause, skip, stop) in the notification and on the lock screen |

The app does not access your contacts, location, camera, microphone, photos or files.

## Security

All network connections are encrypted in transit: Telegram traffic uses Telegram's MTProto protocol, the Edge
and OpenAI services use TLS (HTTPS / secure WebSocket). Data at rest is kept in the app's private storage; the
Telegram database and the OpenAI key are encrypted as described above.

## Keeping and deleting your data

- **Log out** (in the ⋮ menu of the main screen) ends the app's Telegram session and deletes the Telegram database
  (session, chats and cached messages) from your device.
- **Remove key** in the voice settings deletes your OpenAI API key.
- **Clear storage** (Android Settings → Apps → Unofficial Telegram Narrator → Storage) or **uninstalling** the
  app deletes everything the app stored on your device, including settings and the audio cache.
- Data held by Telegram (your account and messages) is managed in Telegram; see Telegram's Privacy Policy. You
  can also end this app's session from another Telegram app under *Settings → Devices*.
- Text you sent to Microsoft or OpenAI for speech synthesis is handled under their policies (OpenAI states that
  API data is not used for training by default and may be retained for up to 30 days for abuse monitoring).

Because I never receive your data, there is nothing for me to delete on a server; if you have questions, contact
me (below).

## Children

The app is not directed at children under 13 and does not knowingly process data of children under 13.

## Changes

If this policy changes, the new version will be published at this address with a new "Last updated" date.
Significant changes will also be mentioned in the app's release notes.

## Contact

Yedidya Toberman, [CONTACT EMAIL]
