# Pratyahara

**Pause the scroll.** An Android app that limits only the Reels / Shorts part of Instagram, YouTube and TikTok, with friction that is firm in the moment and flexible after a pause.

Pratyahara (प्रत्याहार) is the fifth limb of yoga in Patanjali's Yoga Sutras (2.54): drawing the senses back from the objects that pull at them.

- **Scoped blocking.** Only Reels/Shorts pause. Feed, messages, profile and business tools stay open.
- **Daily budget.** 30 minutes by default, counted only while Reels/Shorts is on screen. Lowering is instant; raising needs a written reason and waits 4 hours to 3 days depending on the size of the jump.
- **Move to unlock.** 20 squats (10–50, configurable) earn 5 minutes, at most twice a day, followed by a 10-second cooldown.
- **Nightly task loop.** Write one task for tomorrow each night, then say honestly whether you did it. "No" only asks for a fresh task; silence also resets streaks and turns off squat unlocks.
- **Tamper resistance.** Disabling blocking, removing an app or lowering squats waits 24 hours, with a cancel button. If the accessibility permission is turned off, you get a notification within ~15 minutes.
- **Private by construction.** No internet permission, no analytics, no backups. Screen content is scored in memory and discarded.

## Project layout

```
core/   Pure Kotlin. All rules and their unit tests: budget + delays, text validation,
        rep counter, lock policy, task loop + streaks, detection engine.
app/    Android: accessibility service, overlay, DataStore storage, WorkManager, Compose UI.
  src/main/kotlin/app/pratyahara/detection/DetectionRules.kt   <- per-app detection rules
docs/   Detection rule guide and Play Store materials.
```

Key design: every part of the app asks one pure function, `LockPolicy.evaluate`, whether Reels/Shorts may play. Every change that loosens protection is a `PendingChange` that becomes due only when both the wall clock and the monotonic clock have passed the delay, so changing the phone's time doesn't skip the wait.

## Build

Requirements: Android Studio (Narwhal or newer), JDK 17, Android SDK 36.

```bash
./gradlew :core:test                 # fast JVM tests for all the rules
./gradlew :app:testDebugUnitTest     # detection rule fixtures
./gradlew :app:assembleDebug         # app/build/outputs/apk/debug/app-debug.apk
```

Every push to `main` runs the same on GitHub Actions and uploads the debug APK as a build artifact (Actions tab → latest run → `pratyahara-debug-apk`).

## Testing on a real phone

Accessibility services need a physical phone (recommended) or an emulator with Google Play and the target apps installed.

1. Install the debug build: `adb install -r app/build/outputs/apk/debug/app-debug.apk` (or download the APK from the latest Actions run). Its package is `app.pratyahara.debug`, so it can sit beside a Play release.
2. Open Pratyahara, go through onboarding and turn on the accessibility service when asked. On Android 13+ sideloaded apps need one extra step first: Settings → Apps → Pratyahara → ⋮ → **Allow restricted settings**.
3. Set battery usage to **Unrestricted** (Xiaomi, Samsung, Oppo and Vivo kill background services aggressively).
4. Open Instagram → Reels. Time should count on the home screen. To see a block without waiting 30 minutes, lower the daily limit to 5 minutes (lowering is instant).
5. **Settings → Detection check** shows the live score and which signals matched, with no screen content.

Useful commands:

```bash
adb shell settings get secure enabled_accessibility_services   # is the service on?
```

Squat counting: hold the phone against your chest with both hands. Shaking the phone, or swinging it faster than about one rep a second, is not counted.

## Updating detection rules when an app changes

See [docs/detection-rules.md](docs/detection-rules.md). In short: everything lives in `DetectionRules.kt`; check the Detection check screen on a phone, adjust labels/IDs/weights, add a fixture to `DetectionRulesTest`, and ship.

## Releasing to Google Play

1. Create an upload key once: `keytool -genkey -v -keystore upload.jks -keyalg RSA -keysize 2048 -validity 10000 -alias upload`. Keep it out of git.
2. Create `keystore.properties` in the project root (ignored by git):
   ```
   storeFile=upload.jks
   storePassword=...
   keyAlias=upload
   keyPassword=...
   ```
3. `./gradlew :app:bundleRelease` → `app/build/outputs/bundle/release/app-release.aab`.
4. In Play Console: create the app, fill in the **Accessibility API declaration** and **Data safety** form using [docs/play](docs/play), host the privacy policy at a public URL, and upload the bundle to an internal testing track first.
5. Bump `versionCode` in `app/build.gradle.kts` for every upload.

## Privacy

See [docs/play/privacy-policy.md](docs/play/privacy-policy.md). The app declares no `INTERNET` permission (and strips it if a library tries to add one), excludes all data from backup, and keeps 60 days of counters on the device.
