# Play Console: Accessibility API declaration

Pratyahara is **not** an accessibility tool (`isAccessibilityTool="false"`), so it uses the standard declaration plus the in-app prominent disclosure.

## Core functionality that uses the Accessibility API

> Pratyahara is a digital wellbeing app that helps people limit compulsive short-form video scrolling. Users choose which apps to limit (for example Instagram, YouTube or TikTok). The AccessibilityService is restricted at runtime to those packages only. Within them it inspects the window's layout (selected navigation tab, full-screen video pager, the position of like/comment/share buttons, and view IDs) to recognise when the Reels or Shorts section is on screen. It uses this to (1) count minutes spent in that section against the user's own daily limit, (2) show a full-screen pause overlay (TYPE_ACCESSIBILITY_OVERLAY) when the limit is reached or during the user's focus hours, an optional few-second breathing pause before the section opens, and small non-interactive check-in messages at the top of the screen while the section is open (time left, and research quotes the user can switch off), (3) perform the global Back action only when the user taps "Back to the feed" or "Not now" on those overlays, and (4) show a small floating button, only while one of the chosen apps is in the foreground, and today's remaining time when the user taps it or the accessibility shortcut. When the user turns on "Report a missed screen", it keeps a description of the latest screen's structure in memory, with all text other than known button words replaced, so the user can choose to share it. The rest of each app remains fully usable.

## Is personal or sensitive data collected or shared?

> No. Screen content is evaluated in memory and discarded immediately. The app stores only aggregate counters (minutes per app per day, number of pauses and unlocks) on the device. The app has no INTERNET permission, so no data is transmitted or shared. Data is excluded from cloud backup.

## Does the app use the API to change settings or prevent uninstalling?

> No. Pratyahara never blocks access to Settings, never prevents the user from disabling the service or uninstalling the app, and does not perform actions without the user's request. Delays on loosening the user's own limits are enforced only inside Pratyahara's own UI.

## Video

Record a 30–60 second screen capture: onboarding disclosure → enabling the service → opening Instagram Reels → overlay after the limit → tapping "take me back to the feed" → the home feed and DMs still working. Upload it unlisted to YouTube and paste the link.

## Data safety form

- Data collected: **None**. Data shared: **None**.
- Encrypted in transit: not applicable (no network).
- Users can request deletion: uninstalling deletes all data.
