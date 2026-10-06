# Publishing to Google Play

How to build, sign, version and publish the app on Google Play. Companion docs:

- [privacy-policy.md](privacy-policy.md): the privacy policy to host (Play needs a public URL)
- [play-data-safety.md](play-data-safety.md): Data safety answers, store listing drafts, content rating and the
  Telegram API Terms of Service checklist

## 1. App identity

| Setting | Value | Notes |
|---|---|---|
| `applicationId` | `io.github.yedidyatob.telegramnarrator` | **Permanent.** Play never allows it to change after the first upload. |
| Kotlin `namespace` / package | `io.github.yedidyatob.telegramnarrator` | Can be refactored later without affecting users; kept equal to the ID. |
| Display name | `Unofficial Telegram Narrator` (`app_name`) | Telegram API ToS 2.3: the title may only contain "Telegram" when preceded by "Unofficial". See [play-data-safety.md](play-data-safety.md#telegram-api-terms-of-service-checklist). |
| `minSdk` / `targetSdk` / `compileSdk` | 26 / 36 / 36 | Play requires `targetSdk` 36 for new apps and updates since Aug 31, 2026. |

> Changing the `applicationId` from `com.example.telegramnarrator` makes the new build a **different app**:
> it installs next to an old debug build instead of updating it. Uninstall the old one (or keep it) and log in
> again in the new one.

## 2. Versioning

The version lives in [`gradle.properties`](../gradle.properties):

```properties
TN_VERSION_NAME=1.0.0
```

- `versionName` = `TN_VERSION_NAME` (`MAJOR.MINOR.PATCH`).
- `versionCode` is derived: `MAJOR * 10000 + MINOR * 100 + PATCH`, so `1.0.0` → `10000`, `1.2.3` → `10203`,
  `2.0.0` → `20000`. `MINOR` and `PATCH` must stay in `0..99` (the build fails otherwise).
- TDLib reports the same `versionName` to Telegram as `applicationVersion` (shown under *Active sessions*).

**To release a new version:** bump `TN_VERSION_NAME` (every Play upload needs a higher `versionCode`, so even a
rebuild of the same code needs at least a `PATCH` bump), commit, and tag the commit `v<version>` (e.g. `v1.0.1`)
if you use the release workflow below.

One-off overrides (rarely needed): `./gradlew bundleRelease -PTN_VERSION_NAME=1.2.3` and/or
`-PTN_VERSION_CODE=10204`.

## 3. Upload keystore (one time)

Google Play uses **Play App Signing**: Google keeps the real *app signing key*; you sign uploads with your own
*upload key*. If the upload key is ever lost or leaked, Google can reset it (Play Console → *Test and release →
App integrity*), so losing it is recoverable, but keep it safe anyway.

Create it **outside the repository** (never commit it; `*.jks`, `*.keystore` and `keystore.properties` are
git-ignored anyway):

```bash
mkdir -p ~/keys
keytool -genkeypair -v \
  -keystore ~/keys/telegramnarrator-upload.jks \
  -storetype PKCS12 \
  -alias upload \
  -keyalg RSA -keysize 4096 \
  -validity 10000 \
  -dname "CN=Yedidya Toberman"
```

`keytool` asks for the keystore password (with PKCS12 the key password is the same). Use a long random password
and store it in a password manager.

**Back it up:** put a copy of the `.jks` file and its password in two separate safe places (e.g. password
manager attachment + an encrypted offline drive). Without the upload key you have to go through Google's
upload-key reset before you can ship an update.

### Point the build at the keystore

In `local.properties` (git-ignored) **or** as environment variables with the same names:

```properties
TN_KEYSTORE_PATH=/home/you/keys/telegramnarrator-upload.jks
TN_KEYSTORE_PASSWORD=...
TN_KEY_ALIAS=upload
TN_KEY_PASSWORD=...
```

A relative `TN_KEYSTORE_PATH` is resolved against the project root. Behaviour of `app/build.gradle.kts`:

| Configuration | Release build |
|---|---|
| All four set and the file exists | Signed with the upload key |
| None set (CI, other developers) | **Unsigned** (`app-release-unsigned.apk`, unsigned `app-release.aab`); the build still succeeds |
| Only some set, or the file is missing | Unsigned, with a `Release signing is only partly configured` warning |
| None set and `TN_RELEASE_DEBUG_SIGNING=true` | Signed with the local **debug** key, only to install and try a minified release build on your own device; Play rejects it |

## 4. Build the release bundle

```bash
./gradlew testDebugUnitTest bundleRelease
# → app/build/outputs/bundle/release/app-release.aab
```

Check that it is signed with your upload key (look for `jar verified.` and your certificate):

```bash
jarsigner -verify -verbose:summary -certs app/build/outputs/bundle/release/app-release.aab | tail -5
```

Release builds use R8 (`isMinifyEnabled`), resource shrinking and `debuggable false`. Keep rules are in
[`app/proguard-rules.pro`](../app/proguard-rules.pro): all of `org.drinkless.tdlib` is kept for TDLib's JNI;
Hilt, OkHttp and AndroidX ship their own consumer rules. `Log.v/d/i` calls are stripped from release builds.
Before uploading, install a minified build once and smoke-test login, the unread list and playback with each
engine (System, Edge, OpenAI):

```bash
TN_RELEASE_DEBUG_SIGNING=true ./gradlew installRelease   # without a real keystore configured
```

Upload the R8 mapping file (`app/build/outputs/mapping/release/mapping.txt`) with the release in Play Console
(*App bundle explorer → Downloads → ReTrace mapping file*) so crash stack traces in Play Console are readable.
Play usually picks it up from the AAB automatically.

## 5. Play Console: first release

1. Create a developer account (personal) and verify your identity.
2. **Create app**: name `Unofficial Telegram Narrator`, default language English (en-US), App, Free.
3. **App content** (Policy → App content):
   - *Privacy policy*: the public URL of [privacy-policy.md](privacy-policy.md) (see §7). It must be the same
     URL as the in-app link (`privacy_policy_url` in `app/src/main/res/values/strings.xml`).
   - *Ads*: No ads.
   - *App access*: login is required; see the reviewer note in [play-data-safety.md](play-data-safety.md#app-access-instructions-for-reviewers).
   - *Content rating*, *Target audience*, *Data safety*: copy from [play-data-safety.md](play-data-safety.md).
   - *Foreground service permissions*: declare **Media playback** (text in
     [play-data-safety.md](play-data-safety.md#foreground-service-declaration)). You need a short video showing
     the feature (start "Play all", turn the screen off, controls on the lock screen).
   - *Government apps*, *Financial features*, *Health*: No / none.
4. **Store listing**: texts in [play-data-safety.md](play-data-safety.md#store-listing-draft). Graphics you have
   to make: 512×512 icon, 1024×500 feature graphic, at least 2 phone screenshots. Do not use the Telegram logo.
5. **Testing**: personal developer accounts created after Nov 13, 2023 must run a **closed test with at least
   12 testers opted in for 14 continuous days** before they can apply for production access (internal testing
   does not count). Recruit a few extra testers and start the closed test early.
6. **Upload**: Test and release → Closed testing → Create release → accept **Play App Signing** (default) →
   upload `app-release.aab`. The first upload registers your upload key certificate.
7. After the closed test: *Apply for production* → production release → staged rollout.

## 6. Optional: signed AAB from CI on a tag

The GitHub token used for automation cannot edit workflows, so this is a proposal to add by hand as
`.github/workflows/release.yml`.

Repository secrets (Settings → Secrets and variables → Actions):

| Secret | Value |
|---|---|
| `TELEGRAM_API_ID`, `TELEGRAM_API_HASH` | already used by CI |
| `TN_KEYSTORE_BASE64` | `base64 -w0 ~/keys/telegramnarrator-upload.jks` (macOS: `base64 -i … `) |
| `TN_KEYSTORE_PASSWORD` | keystore password |
| `TN_KEY_ALIAS` | `upload` |
| `TN_KEY_PASSWORD` | key password (same as the keystore password for PKCS12) |

```yaml
name: Release

on:
  push:
    tags: ['v*.*.*']

permissions:
  contents: read

jobs:
  bundle:
    runs-on: ubuntu-latest
    timeout-minutes: 45
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 17

      - uses: gradle/actions/setup-gradle@v4

      - name: Check that the tag matches TN_VERSION_NAME
        run: |
          expected="$(sed -n 's/^TN_VERSION_NAME=//p' gradle.properties)"
          if [ "${GITHUB_REF_NAME#v}" != "$expected" ]; then
            echo "::error::Tag $GITHUB_REF_NAME does not match TN_VERSION_NAME=$expected in gradle.properties"
            exit 1
          fi

      - name: Write Telegram API credentials
        env:
          TELEGRAM_API_ID: ${{ secrets.TELEGRAM_API_ID }}
          TELEGRAM_API_HASH: ${{ secrets.TELEGRAM_API_HASH }}
        run: |
          if [ -z "$TELEGRAM_API_ID" ] || [ -z "$TELEGRAM_API_HASH" ]; then
            echo "::error::TELEGRAM_API_ID / TELEGRAM_API_HASH secrets are required for a release build"
            exit 1
          fi
          {
            echo "TELEGRAM_API_ID=$TELEGRAM_API_ID"
            echo "TELEGRAM_API_HASH=$TELEGRAM_API_HASH"
          } >> local.properties

      - name: Decode upload keystore
        env:
          TN_KEYSTORE_BASE64: ${{ secrets.TN_KEYSTORE_BASE64 }}
        run: |
          if [ -z "$TN_KEYSTORE_BASE64" ]; then
            echo "::error::TN_KEYSTORE_BASE64 secret is missing"
            exit 1
          fi
          echo "$TN_KEYSTORE_BASE64" | base64 --decode > "$RUNNER_TEMP/upload.jks"
          echo "TN_KEYSTORE_PATH=$RUNNER_TEMP/upload.jks" >> "$GITHUB_ENV"

      - name: Unit tests and signed release bundle
        env:
          TN_KEYSTORE_PASSWORD: ${{ secrets.TN_KEYSTORE_PASSWORD }}
          TN_KEY_ALIAS: ${{ secrets.TN_KEY_ALIAS }}
          TN_KEY_PASSWORD: ${{ secrets.TN_KEY_PASSWORD }}
        run: >
          ./gradlew testDebugUnitTest bundleRelease --stacktrace
          -Dorg.gradle.java.home="$JAVA_HOME"
          -Dorg.gradle.jvmargs="-Xmx3g -Dfile.encoding=UTF-8"

      - name: Verify the bundle is signed
        run: |
          jarsigner -verify app/build/outputs/bundle/release/app-release.aab | tee verify.txt
          grep -q "jar verified." verify.txt

      - name: Remove keystore
        if: always()
        run: rm -f "$RUNNER_TEMP/upload.jks"

      - name: Upload AAB and R8 mapping
        uses: actions/upload-artifact@v4
        with:
          name: TelegramNarrator-${{ github.ref_name }}
          path: |
            app/build/outputs/bundle/release/app-release.aab
            app/build/outputs/mapping/release/mapping.txt
          retention-days: 30
```

Release flow: bump `TN_VERSION_NAME`, merge, then `git tag v1.0.1 && git push origin v1.0.1`; download the
artifact from the workflow run and upload the AAB in Play Console. (Uploading straight to Play from CI needs a
Google Cloud service account; not set up here.)

## 7. Hosting the privacy policy

Play needs a public, non-PDF URL that works without logging in. The repository is private, so a link into the
repo (or a raw link) is **not** public. Options:

- **GitHub Pages from this repo** (needs the repo to be public, or a paid GitHub plan for Pages on a private
  repo): Settings → Pages → *Deploy from a branch* → `main` / `/docs`. The policy is then at
  `https://yedidyatob.github.io/TelegramNarrator/privacy-policy.html`, which is the URL the app already links to.
  Note that every other file under `docs/` becomes public too.
- **A small separate public repo** (e.g. `yedidyatob/telegramnarrator-privacy`) with only the policy and Pages
  enabled. Then update `privacy_policy_url` in `app/src/main/res/values/strings.xml` to the new URL.
- Any other static host (Google Sites, a gist rendered through a Pages site, …): same, update the string.

Before publishing, replace `[CONTACT EMAIL]` in the policy with a real address you read.

## 8. Notes

- **Telegram API credentials** (`TELEGRAM_API_ID` / `TELEGRAM_API_HASH`) come only from `local.properties`
  (CI writes them from repo secrets) and end up in `BuildConfig`. They can be extracted from any APK; that is
  normal and accepted for Telegram client apps (every client ships its `api_hash`). Use an `api_id` registered
  for this app only, never reuse it in other projects, and do not commit it.
- **16 KB page size**: see the README section "TDLib / 16 KB page size".
- **AGP**: the project is on AGP 8.2.1 with `android.suppressUnsupportedCompileSdk=36`. Builds, unit tests,
  lint and R8 work with SDK 36; upgrading AGP (8.9+) is a separate, unrelated task.
