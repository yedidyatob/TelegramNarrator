# Google Play: Data safety, store listing and Telegram terms

Ready-to-copy answers for Play Console, based on what the code does as of this commit. Re-check them whenever a
feature changes what data leaves the device (new engine, analytics, crash reporting, …), and keep them
consistent with [privacy-policy.md](privacy-policy.md). Build and signing steps are in
[publishing.md](publishing.md).

## What the app sends off the device (facts the answers are based on)

| Data | Sent to | When | Code |
|---|---|---|---|
| Phone number, login code, 2FA password | Telegram (TDLib, MTProto) | Login | `TdLibAuthRepository` |
| Telegram chat / message / user IDs, read receipts | Telegram | Loading chats, marking played messages as read (always on in release builds) | `TdLibChatRepository` |
| Sponsored-message requests, views, clicks and reports (chat ID + sponsored message ID) | Telegram | Telegram's official sponsored messages in channels and bot chats (Telegram API ToS 3.3): fetched when a channel / bot chat is played or its preview opened; a view when the ad was read aloud in full or its text was fully on screen; a click when its link is opened; a report when the user reports it | `TdLibSponsoredMessagesSource` |
| Cleaned message text, chat titles (spoken before each chat) and the text of Telegram sponsored messages, voice name, random per-request ID | Microsoft (Edge "Read aloud" endpoint, WSS; a third-party service, not an official API) | Whenever **Edge** is the engine: it is the **default on new installs** (preselected in the onboarding's voice step), unless the user picks System or OpenAI there or in Voice settings. Installs updated from a version that defaulted to System keep System (`SpeechProvider.UPGRADE_DEFAULT`) | `EdgeTtsClient` |
| Cleaned message text, chat titles (spoken before each chat) and the text of Telegram sponsored messages, voice/model, the user's own API key | OpenAI (`api.openai.com`, HTTPS) | Only if the user selects **OpenAI** and saves a key | `OpenAiSpeechClient` |

Nothing is sent to the developer: there is no backend, no analytics, no ad SDK, no crash reporting or other SDK that
phones home (dependencies: AndroidX, Compose, Hilt, TDLib, OkHttp, security-crypto). The only ads are Telegram's
own sponsored messages, served by Telegram through TDLib; the developer earns nothing from them. Stored on the device only:
the TDLib database (encrypted), settings, the encrypted OpenAI key and the cloud-TTS audio cache; Android backup
and device transfer are disabled. The app cannot create Telegram accounts (it only logs in to existing ones).

## Data safety form

### Data collection and security

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **Yes** |
| Is all of the user data collected by your app encrypted in transit? | **Yes** (MTProto, TLS) |
| Which methods of account creation does your app support? | **My app does not allow users to create an account** (users log in to an existing Telegram account, which is created in Telegram's own apps) |
| Do you provide a way for users to request that their data is deleted? | **Yes**: Log out deletes the Telegram data on the device, *Remove key* deletes the OpenAI key, clearing the app's storage / uninstalling deletes everything. Optional URL: the "Keeping and deleting your data" section of the privacy policy |
| Independent security review | No |

### Data types

"Collected" in Play's sense means "sent off the device by the app", even when it goes to a third-party service
rather than to the developer.

| Category → type | Collected | Shared | Processed ephemerally | Required or optional | Purposes |
|---|---|---|---|---|---|
| Personal info → **Phone number** | Yes | No | No | Required | App functionality, Account management |
| Personal info → **User IDs** (Telegram user / chat IDs in API requests) | Yes | No | No | Required | App functionality |
| Messages → **Other in-app messages** (message text and chat titles for online speech) | Yes | **Yes** (Microsoft with the default Edge engine, or OpenAI if chosen) | No | **Optional** (on by default with Edge; the user can switch to the offline System voice in the onboarding or Voice settings, and nothing is sent then) | App functionality |

Everything else: **not collected** (location, contacts, photos/videos, audio, files, calendar, health, financial
info, web browsing, app activity, app info and performance / crash logs, device or other IDs).

Notes on the choices:

- Sending the login data to Telegram is the core, user-initiated function of a Telegram client, so it is not
  declared as *sharing*; it is declared as *collection* because the app transmits it.
- **The default engine (Edge) shares message text with Microsoft, a third-party service.** On a new install the
  text of the messages being read and the chat titles (for one-on-one chats the title is the other person's name)
  go to Microsoft unless the user switches to the System voice. Because this happens by default rather than after
  a deliberate user choice, Play's "user-initiated action" exemption does not apply: it **must** be declared as
  *shared* (and collected). The onboarding's privacy step and the Edge card in the voice step say so, as do the
  login notice and the privacy policy. OpenAI is shared only after the user picks it and saves a key.
- It stays **Optional** in the form because users can choose not to send it: the System voice is offered in the
  onboarding and in Voice settings, and with it nothing leaves the device.
- A one-on-one chat's title is a contact's name. It is declared under *Messages* (it is spoken as part of the
  conversation) rather than *Personal info → Name*, which describes the user's own name.
- The login code, the Telegram password and the OpenAI API key are authentication secrets passed to the
  service they belong to; Play has no data type for them. They are covered in the privacy policy.
- Voice messages and chat lists are *downloaded* from Telegram to the device, which is not collection.
- Sponsored-message views, clicks and reports go to Telegram (the service the user is logged in to), like read
  receipts, and carry no data beyond the chat and ad IDs. If Play asks, they fit *App activity → App interactions*
  (collected, not shared, purpose *Advertising or marketing* + *App functionality*); declaring them is the
  conservative choice.

## Permissions (for review questions)

| Permission | Why it is needed |
|---|---|
| `INTERNET` | TDLib (Telegram) and the online speech engines (Edge, the default, and the optional OpenAI) |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | `PlaybackService` keeps reading aloud with the screen off |
| `POST_NOTIFICATIONS` | Optional media controls in the notification / lock screen; asked in the first-run introduction (Android 13+) or before the first playback, reading works without it |

`WRITE_EXTERNAL_STORAGE` (declared by the TDLib AAR) and the `READ_EXTERNAL_STORAGE` it implies are removed from the
merged manifest; `WAKE_LOCK` was unused
and has been dropped.

## Foreground service declaration

Play Console → App content → Foreground service permissions → **Media playback**:

> Unofficial Telegram Narrator reads the user's unread Telegram messages aloud with text-to-speech and plays
> their voice messages. Playback only starts when the user taps Play (or Play all) in the app. It must continue
> when the user turns off the screen or switches to another app, for example while walking or driving, with
> pause / skip / stop controls in the media notification, on the lock screen and on headset buttons. If the
> work were deferred or stopped by the system, the narration would cut off in the middle of a message as soon
> as the user leaves the app, which breaks the app's only feature.

Video: record 20-30 s showing Play all → screen off / home → notification and lock-screen controls → Stop.

## App access (instructions for reviewers)

The app is unusable without a Telegram login, and Telegram sends the login code to the account's other Telegram
sessions or by SMS, so a reviewer cannot use a phone number you give them unless they can receive that code.
Options (pick one before submitting):

1. **Demo account**: a separate Telegram account on a spare number. Give the number in *App access*, and
   explain that the login code is sent to the account; this usually only works if you can relay the code, so
   Google may come back with questions.
2. **Reviewer's own account**: "Requires a Telegram account. Install Telegram, create an account (free), then log
   in to this app with the same phone number; the code arrives in the Telegram app. Have a few unread messages,
   tap Play all." Add a link to a demo video.

Suggested text (option 2):

> The app reads unread Telegram messages aloud and requires logging in with an existing Telegram account (free,
> created in the official Telegram app). Enter the phone number of that account; Telegram sends the login code to
> the Telegram app on the same phone. Make sure some chats have unread messages, then tap "Play all". The default
> voice is Edge (online, no account needed); the offline System voice can be chosen in the first-run introduction or
> in Voice settings → Engine. Demo video: [VIDEO LINK]

## Store listing draft

**App name** (30 max): `Unofficial Telegram Narrator` (28). Use the same title in every language: Telegram's
terms allow "Telegram" in a title only when preceded by "Unofficial", which does not work with Hebrew word order.

**Category:** Communication (alternative: Productivity). **Tags:** text to speech, messaging, accessibility.
**Contains ads:** **Yes**: the app shows Telegram's official sponsored messages in channels and bot chats, which
Telegram requires of every app that shows channel content (Telegram API ToS 3.3); Play counts any ads shown in the
app, including ones served by a third party. **In-app purchases:** No. **Contact email:** [CONTACT EMAIL]. **Privacy policy:** the
hosted URL of [privacy-policy.md](privacy-policy.md).

### English

**Short description** (80 max):

```
Your unread Telegram messages, read aloud. Unofficial app. No tracking.
```

**Full description:**

```
Unofficial Telegram Narrator reads your unread Telegram chats aloud, one message after another, so you can catch up hands-free while walking, driving or cooking.

This is an unofficial app. It uses the Telegram API and is part of the Telegram ecosystem, but it is not made, endorsed or supported by Telegram. You log in with your own Telegram account.

FEATURES
• Lists your chats with unread messages; play all of them, a selection, or a single chat
• Pause, resume, skip a message or a whole chat, from the app, the notification, the lock screen or headset buttons
• Voice messages are played in the queue
• Detects the language of every message (great for mixed Hebrew / English channels)
• Cleans up channel posts: skips links, ads, signatures and symbol-only lines
• Marks messages as read in Telegram only after they were actually read aloud
• Adjustable speech rate

VOICES
• Edge (default): free natural voices from Microsoft's online "Read aloud" service (unofficial); message text and chat names are sent to Microsoft
• System: the text-to-speech engine on your phone, works offline; nothing is sent
• OpenAI (optional): high-quality voices with your own OpenAI API key, billed by OpenAI; message text and chat names are sent to OpenAI

PRIVACY
• No analytics, no tracking, and no developer servers
• Channels and bot chats include Telegram's official sponsored messages, as Telegram requires; the app has no ads of its own and earns nothing from them
• Your messages are stored only on your device, in an encrypted database, and are exchanged only with Telegram
• With the default Edge voice, message text and chat names are sent to Microsoft to be read aloud; choose the System voice to keep everything on your phone
• Log out at any time to remove your Telegram data from the device

Privacy policy: [PRIVACY POLICY URL]
```

### Hebrew (עברית)

**Short description** (80 max):

```
ההודעות שלא קראת בטלגרם, בהקראה קולית. אפליקציה לא רשמית, בלי מעקב.
```

**Full description:**

```
Unofficial Telegram Narrator מקריאה בקול את הצ'אטים שלא קראת בטלגרם, הודעה אחרי הודעה, כדי שתוכלו להתעדכן בלי ידיים – בהליכה, בנהיגה או במטבח.

זו אפליקציה לא רשמית. היא משתמשת ב־API של Telegram והיא חלק מהאקוסיסטם של Telegram, אך היא לא פותחה, לא אושרה ולא נתמכת על ידי Telegram. ההתחברות היא עם חשבון הטלגרם שלכם.

תכונות
• רשימת הצ'אטים עם הודעות שלא נקראו: הפעלה של כולם, של בחירה או של צ'אט אחד
• השהיה, המשך, דילוג על הודעה או על צ'אט שלם – מהאפליקציה, מההתראה, ממסך הנעילה או מכפתורי האוזניות
• הודעות קוליות מושמעות בתור
• זיהוי שפה לכל הודעה (מצוין לערוצים בעברית ובאנגלית)
• ניקוי פוסטים בערוצים: דילוג על קישורים, פרסומות, חתימות ושורות של סמלים
• סימון הודעות כנקראו בטלגרם רק אחרי שהוקראו בפועל
• מהירות דיבור מתכווננת

קולות
• Edge (ברירת מחדל): קולות טבעיים בחינם משירות ההקראה המקוון של Microsoft (לא רשמי); טקסט ההודעות ושמות הצ'אטים נשלחים ל־Microsoft
• מערכת: מנוע ההקראה של הטלפון, עובד בלי אינטרנט; שום דבר לא נשלח
• OpenAI (אופציונלי): קולות באיכות גבוהה עם מפתח API משלכם, בחיוב של OpenAI; טקסט ההודעות ושמות הצ'אטים נשלחים ל־OpenAI

פרטיות
• בלי אנליטיקה, בלי מעקב ובלי שרתים של המפתח
• בערוצים ובצ'אטים עם בוטים מוצגות ההודעות הממומנות הרשמיות של Telegram, כפי ש־Telegram דורשת; לאפליקציה אין פרסומות משלה והיא לא מרוויחה מהן
• ההודעות נשמרות רק במכשיר, במסד נתונים מוצפן, ומוחלפות רק עם Telegram
• עם קול Edge (ברירת המחדל) טקסט ההודעות ושמות הצ'אטים נשלחים ל־Microsoft להקראה; בחרו בקול המערכת כדי שהכול יישאר בטלפון
• אפשר להתנתק בכל רגע כדי למחוק את נתוני הטלגרם מהמכשיר

מדיניות פרטיות: [PRIVACY POLICY URL]
```

The app UI itself is English only (only spoken labels and the unofficial notice have Hebrew translations), so
a Hebrew listing should not promise a Hebrew interface.

## Content rating (IARC questionnaire)

- **Category:** "Utility, Productivity, Communication, or Other".
- Violence, sexuality, language, controlled substances, gambling: **No** (the app itself contains no such
  content).
- **User interaction / user-generated content:** the app shows and reads aloud messages from the user's own
  Telegram chats, which are not moderated by the app; users cannot send messages or share content through the
  app. Answer those questions accordingly (no in-app chatting or sharing, but unmoderated third-party content is
  displayed). Shares location: No. Digital purchases: No. Unrestricted internet / web browser: No.
- Expect a teen-ish rating because of the unmoderated content; that is fine.

**Target audience and content:** select **18 and over** only (Telegram content is unmoderated, and this keeps
the app clearly outside the Families policy). Appeals to children: No.

## Telegram API Terms of Service checklist

Source: <https://core.telegram.org/api/terms>. Status of this commit:

| Term | Status | Notes / action |
|---|---|---|
| 1.1 Guard users' privacy (Security Guidelines) | OK | TDLib handles MTProto; the database is encrypted with a Keystore-wrapped key; secret chats disabled; backup and device transfer disabled; message text and keys are never logged; debug logs stripped from release |
| 1.2 / 1.3 Extensions must not break basic Telegram features | OK | Read-only narrator; nothing it does affects other users |
| 1.4 No interfering with basic functionality (e.g. "ghost mode", tampering with read statuses, acting without consent) | OK (fixed) | Messages are marked as read only after they were really spoken (or deliberately skipped). Release builds always mark them as read: the *Mark messages as read* switch exists only in debug builds (`BuildConfig.DEBUG`) and a stored "off" value is ignored in release, so the published app has no "ghost mode". See [mark-as-read.md](mark-as-read.md) |
| 1.5 No use of Telegram data for AI training | OK | Nothing is used for training. The Edge (default) and OpenAI engines only synthesize speech (inference) for the messages being read; the OpenAI API does not train on API data by default |
| 2.1 Own `api_id` | **Action** | Use an `api_id` registered at my.telegram.org for this app (title/description there should describe this app), only in `local.properties` / CI secrets |
| 2.2 Say prominently that the app uses the Telegram API (store description + in-app intro) | OK | Second paragraph of the store descriptions above; notice in the first-run introduction, on the login screen and in *Voice settings → About* |
| 2.3 Title must not contain "Telegram" unless preceded by "Unofficial" | OK (fixed) | Display name changed from "Telegram Narrator" (non-compliant) to "Unofficial Telegram Narrator". Alternatives without the word: "Chat Narrator", "Unread Aloud", "Narrator for chats" |
| 2.4 No official Telegram logo | OK | Own icon (white ring on purple). Do not use the paper plane in store graphics or screenshots' frames |
| 3.1 / 3.2 Monetization disclosed in store descriptions | OK | No monetization by the developer. Telegram's sponsored messages (3.3) are Telegram's, not the app's; the store descriptions still mention them. If you ever add your own monetization, list every method in the store descriptions |
| 3.3 Apps that show channel content must support official sponsored messages | OK | Channels and bot chats: `getChatSponsoredMessages` (5-minute cache) when the chat is played or its preview opened. After the chat's last unread message the ad is read aloud ("Sponsored" / "ממומן", or "Recommended" / "מומלץ", then title and text) and shown as a card (label, accent color, sponsor photo, title, text, media, button; confirmation before non-Telegram links; ⋮ Sponsor info / About these ads / Report with Telegram's option flow); it is also at the bottom of the chat's preview sheet. Views through `viewMessages` (TDLib 1.8.56 has no `viewSponsoredMessage`), at most once per ad per fetch, when read aloud in full or fully on screen; clicks via `clickChatSponsoredMessage`; reports via `reportChatSponsoredMessage`. Channel cleaning rules never touch it. See [architecture.md](architecture.md#sponsored-messages) |
| 4 Breach handling | Note | Telegram contacts the account that owns the `api_id`, with 10 days to fix. Keep that account active and read its service notifications |

Google Play side: Play's impersonation / metadata policies can also object to third-party brand names in a title,
even with "Unofficial". If review pushes back, switch to a name without "Telegram" (the alternatives above) and
keep "for Telegram" / the unofficial statement in the description.
