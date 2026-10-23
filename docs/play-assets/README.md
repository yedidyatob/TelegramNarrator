# Play Store assets

| File | Spec | Made by |
|---|---|---|
| `<lang>/icon-512.png` | 512×512, 32-bit PNG, full-bleed square (Play applies the mask) | `tools/play-assets/generate_store_graphics.py` (launcher-icon artwork from `tools/icon/generate_icon.py`) |
| `<lang>/feature-graphic.png` | 1024×500, 24-bit PNG, no alpha. App icon, name and tagline; no Telegram logo | `tools/play-assets/generate_store_graphics.py` |
| `<lang>/screenshots/NN-*.png` | 1080×1920 phone screenshots with a caption banner | `tools/play-assets/frame_screenshots.py` |
| `<lang>/screenshots/raw/NN-*.png` | 1080×1920 un-framed screenshots (uploadable as-is) | Robolectric + Roborazzi |

`<lang>` is `en` (en-US listing) or `he` (Hebrew listing). The icon is identical in both folders.

## Screenshots
The screenshots are the **real Compose screens**: `HomeTopBar` + `ChatList` + `MiniPlayer`, `PlayerContent` with the Up next sheet, `VoiceSettingsContent` and `AboutDialog`. They are rendered on the JVM by Robolectric (native graphics) at 360×640 dp, xxhdpi, light theme.

The sample data is invented (e.g. "Tech News Daily", "חדשות הבוקר"). There are no real channels, people or photos; avatars show initials. The source is `app/src/test/java/.../screenshots/PlayStoreScreenshots.kt`.

These tests are skipped in normal `testDebugUnitTest` runs. To regenerate:

```
./gradlew testDebugUnitTest -Pscreenshots          # -> build/play-screenshots/<lang>/*.png
python3 tools/play-assets/frame_screenshots.py     # -> docs/play-assets/<lang>/screenshots/
python3 tools/play-assets/generate_store_graphics.py   # icon + feature graphic (needs google-chrome, Pillow, Rubik font)
```

Only for the `-Pscreenshots` run, the build adds a generated `values-iw` copy of `values-he`, because Robolectric only matches Hebrew under the legacy `iw` code. The About dialog renders edge to edge under Robolectric; on a phone it has side margins.
