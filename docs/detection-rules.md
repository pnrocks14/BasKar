# Updating detection rules

Instagram, YouTube and TikTok change their layouts often. Pratyahara is built so that a change usually means editing one file: `app/src/main/kotlin/app/pratyahara/detection/DetectionRules.kt`.

## How detection works

For each monitored app, a rule lists **weighted signals**. On every screen change (throttled to ~3 times a second) the service builds an in-memory `UiSnapshot` (class names, view IDs, selected state, bounds, and short text/labels), adds up the weights of the signals that match, and calls the screen Reels/Shorts when the total reaches the rule's `threshold` on **two evaluations in a row**.

| Signal | What it looks for | Typical weight |
| --- | --- | --- |
| `SelectedTab(labels)` | A selected item in the bottom 30% of the screen whose label contains e.g. "reels" | 3 |
| `ViewIdPresent(ids)` | A known player view ID, e.g. `clips_viewer_view_pager`. Soft: apps rename these | 3 |
| `FullScreenPager()` | A scrollable pager/RecyclerView covering ≥85% of width and height | 2 |
| `RightActionStack(labels)` | At least 2 of like/comment/share... on the right quarter of the screen | 2 |
| `LabelPresent(labels)` | Text such as "Original audio" anywhere on screen | 1 |

Design rule: **at least two independent combinations must reach the threshold**, so a single renamed ID or label never breaks detection. For Instagram (threshold 4): tab + anything, or ID + anything, or pager + action stack.

## When detection breaks

1. On the phone, open **Settings → Detection check** in Pratyahara.
2. Switch to the app, open the Reels/Shorts screen, then switch back. The card shows the score, the threshold and which signal types matched.
3. Also check a normal screen (home feed, a regular video, DMs) to be sure it stays below the threshold.
4. To see the new layout's view IDs and labels, use Android Studio's **Layout Inspector** or `adb shell uiautomator dump /sdcard/ui.xml && adb pull /sdcard/ui.xml` while the screen is open. These dumps contain screen text: inspect them locally and don't commit them.
5. Edit the rule in `DetectionRules.kt`: add the new label or ID, or adjust weights.
6. Add a fixture to `app/src/test/.../DetectionRulesTest.kt` describing the new layout by structure (class, ID, label, bounds), plus a negative fixture for a screen that must stay open. Run `./gradlew :app:testDebugUnitTest`.
7. Bump `versionCode` and ship.

## Notes

- Labels are matched case-insensitively against text and content descriptions, in English. Phones set to another language need labels in that language added to the same sets.
- TikTok's whole video feed counts as short-form; inbox, profile and search stay open.
- The service only receives events from monitored apps (`packageNames` is set at runtime), so a rule for an unmonitored app costs nothing.
