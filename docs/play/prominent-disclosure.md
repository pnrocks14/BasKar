# In-app prominent disclosure

Shown in onboarding (step 2, `OnboardingScreen.kt`) before the user is sent to Accessibility settings. It appears during normal use, is not buried in a menu or the privacy policy, and requires an affirmative action (checkbox + "Agree and continue"; "Not now" exits).

**Title:** Pratyahara uses the Accessibility Service API

To pause Reels and Shorts, Pratyahara needs Android's accessibility permission. Here is exactly what it does with it:

- It only looks at the apps you pick (for example Instagram, YouTube or TikTok). It does not run in any other app.
- Inside those apps it checks the layout of the screen, such as which tab is selected and whether a full-screen video player is open, to tell whether you're on Reels or Shorts.
- It never reads, saves or sends your messages, posts, searches or anything else on screen. Screen content is checked in memory and immediately discarded.
- The only things it saves are counts: minutes spent on Reels and Shorts, and how often they were paused or unlocked.
- Nothing leaves your phone. Pratyahara has no internet permission at all.
- It uses the permission to show the pause screen, the breathing pause, small check-ins and the floating Pratyahara button on top of those apps, and to press Back for you when you tap "Back to the feed".

☐ I understand, and I agree to Pratyahara using the accessibility permission this way.

[Agree and continue]  [Not now]
