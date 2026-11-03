# Languages (#89)

## Detection
- Each chat's language is detected **on the device** with ML Kit Language ID (bundled model, ~2.5 MB in the release APK; nothing is downloaded or sent).
- `ChatLanguageResolver` runs before a chat is read, on its most recent texts: 10 for groups / channels, 5 for private chats. Guesses are weighted by confidence and length.
- **Confirmed** needs at least 3 identified samples (2 for private chats), at least 40 letters, at least 60 % of the vote and at least 0.7 average confidence.
- **Unconfirmed** results come from the 4-script check (Hebrew / Latin→English / Cyrillic→Russian / Arabic), else the device language. They are detected again on the next read.
- **Re-check:** a confirmed language is re-checked every 50 messages, or when most new messages are clearly in another script. An inconclusive re-check keeps it.
- **Per-message override:** a message with at least 12 letters, at least 90 % of them in one of the four scripts and different from the chat language's script, is read in that script's language.
- **Storage:** chat id → `lang|confidence|samples|confirmed|sinceCheck` in EncryptedSharedPreferences (`chat_languages_secure`), memory-only when secure storage is unavailable. No text and no titles are stored. It is wiped on logout, and *Voice settings → Reset detected chat languages* clears it.
- **Debug log** (TN-Auto): `lang chat=<id> lang=<code> conf=<0.00> samples=<n> confirmed=<bool>`, never any text.

## Engines
| Engine | Voice |
|---|---|
| System | Chat language via `setLanguage`. If it is not installed, the device locale is used and a one-time toast per language suggests installing the voice. A per-language voice pick in settings still applies. |
| Gemini | Unchanged: Gemini voices are multilingual. |

**Spoken extras:** the sponsored cue ("Sponsored" / "Recommended") is spoken in the chat language for `ar de en es fa fr he it nl pl pt ru tr uk` and in English otherwise. Chat titles are read with the chat's voice.

**App UI:** English and Hebrew (full translation). The per-app language can be set in Android 13+ Settings → Apps → Narri → Language (`localeConfig` generated from `values-*`).
