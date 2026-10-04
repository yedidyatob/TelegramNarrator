> **Note:** this is the original design spec and is partly outdated (e.g. the app does not encrypt the TDLib database yet, has no use-case layer, onboarding or Player screen, and uses SharedPreferences, not DataStore). See README.md for the current state.

# Telegram Reader -- Production Architecture Specification

**Platform:** Android
**Architecture:** Clean Architecture + MVVM
**UI Framework:** Jetpack Compose (Material3)
**Dependency Injection:** Hilt
**Telegram Library:** TDLib (Pre-built Android Library)
**TTS Engine:** Android Native TextToSpeech
**Security Model:** Local-only, encrypted storage

------------------------------------------------------------------------

# 1. Product Overview

Telegram Reader is a fully local Android application that:

-   Logs the user into Telegram via TDLib
-   Detects unread messages
-   Builds structured narration (Multi-language support, focused on Hebrew)
-   Plays messages using on-device TTS
-   Optionally marks chats as read
-   Works without any backend server

All Telegram communication is handled locally via TDLib.

------------------------------------------------------------------------

# 2. High-Level Architecture

## Layers

1.  **Presentation (UI)**
    *   `MainActivity` (Single Activity)
    *   Navigation: Compose Navigation
    *   Screens: `Onboarding`, `Login`, `ChatList`, `Player`
    *   Theme: Material3
2.  **Domain (Business Logic)**
    *   `UseCases`: `GetUnreadChats`, `PlayChatMessages`, `LoginUser`
    *   `Models`: `Chat`, `Message`, `AudioState`
    *   `AudioQueue`: Core logic for sequencing audio
3.  **Data (Repository Impl)**
    *   `TdLibRepository`: Wraps TDLib client
    *   `TtsRepository`: Wraps Android TTS
    *   `Preferences`: DataStore for settings
4.  **Core (Infrastructure)**
    *   `Service`: `PlaybackService` (Foreground Service)
    *   `DI`: Hilt Modules
    *   `Security`: Encrypted File Storage

------------------------------------------------------------------------

# 3. TDLib Integration Design

## Library Source
Use a pre-built Gradle dependency (e.g., `org.thunderdog.challegram:tdlib` or similar stable build) to avoid complex NDK builds.

## Initialization Flow
1.  Create `TdLibClient` (Singleton, managed by Hilt @Singleton)
2.  Observe `AuthorizationState` flow
3.  Handle parameters (API_ID, API_HASH from BuildConfig)
4.  Expose reactive streams (Flows) for `Updates`

------------------------------------------------------------------------

# 4. Authentication Flow (UI)

Screens implemented in Compose:
1.  **Welcome/Onboarding**
2.  **Phone Input** (Format validation)
3.  **Code Input** (SMS/Telegram code)
4.  **Password Input** (2FA, if required)
5.  **Loading/Sync**

------------------------------------------------------------------------

# 5. Data Flow

`UI Event` -> `ViewModel` -> `UseCase` -> `Repository` -> `TDLib/TTS`
`TDLib Update` -> `Repository` -> `Flow` -> `UseCase` -> `ViewModel` -> `UI`

------------------------------------------------------------------------

# 6. Operational Mode & Unread Strategy

**Mode:** On-Demand (User Initiated)

-   User opens app -> App connects to TDLib.
-   App syncs current state (GetChats).
-   User sees list of unread chats.
-   **Actions:**
    -   **Play All:** Sequentially plays all unread chats.
    -   **Select Chat:** Plays specific chat.
-   **No Background Sync (WorkManager)** needed for core functionality.
-   **Foreground Service** is used ONLY during active playback to keep TTS alive and handle media buttons.

------------------------------------------------------------------------

# 7. Narration Engine & AudioQueue

## Message Cleaning (Text Normalizer)
Before TTS, messages pass through a `MessageCleaner` (Domain Layer):
-   **Regex-based replacement** (Classic algorithm, low latency).
-   Removes URLs (`http://...` -> "Link").
-   Strips Markdown formatting (`**`, `__`).
-   Normalizes widely used emojis or symbols if needed.

## AudioQueue (Domain)
Responsible for managing the playlist of "Items" to speak.
*   **Item Types**: `Intro(ChatName)`, `Message(Sender, Text)`, `Silence(Duration)`, `Outro`.
*   **Navigation**:
    *   `Next Message`: Skips current text segment.
    *   `Next Chat`: Jumps to the `Intro` of the next chat in the queue.

## TextToSpeech Wrapper
*   Handles language switching (if message language detection is added later).
*   Manages speed/pitch preferences.
*   Handles `onDone` callbacks to trigger next item in `AudioQueue`.

------------------------------------------------------------------------

# 8. Security Strategy

-   **Storage**: TDLib internal database (encrypted by default).
-   **Secrets**:
    -   `API_ID` / `API_HASH` are application-level credentials.
    -   Stored in `local.properties` during dev.
    -   Injected via `BuildConfig` into the released APK.
    -   **End-users do NOT provide these.**
-   **Logs**: Strip sensitive data in Release.

------------------------------------------------------------------------

# 9. Performance & Scalability

-   **Pagination**: Load messages in chunks (e.g., last 50 unread).
-   **Concurrency**: usage of `Dispatchers.IO` for all TDLib calls.
-   **Memory**: Don't load all strings at once; stream to TTS if possible (though TTS takes strings). Keep message history distinct from playback queue.

------------------------------------------------------------------------

# 10. Future Extensions

-   **Android Auto**: MediaBrowserService implementation.
-   **Summarization**: Local LLM (e.g., Gemini Nano) to summarize long chats.
-   **Smart Filtering**: Mute specific groups.
