package app.pratyahara.detection

import app.pratyahara.core.detection.DetectionRule
import app.pratyahara.core.detection.Signal.FullScreenPager
import app.pratyahara.core.detection.Signal.LabelPresent
import app.pratyahara.core.detection.Signal.RightActionStack
import app.pratyahara.core.detection.Signal.SelectedTab
import app.pratyahara.core.detection.Signal.ViewIdPresent
import app.pratyahara.core.detection.WeightedSignal

/**
 * THE file to edit when an app changes its UI. See docs/detection-rules.md.
 *
 * Each rule adds up the weights of the signals it finds on screen; at or above `threshold`,
 * the screen counts as Reels/Shorts. Keep at least two independent signals able to reach the
 * threshold on their own, so one renamed view never breaks detection.
 *
 * Labels are matched case-insensitively against text and content descriptions, in English.
 * Use the Detection debug screen (Settings > Detection check) to see live scores on your phone.
 */
object DetectionRules {

    const val INSTAGRAM = "com.instagram.android"
    const val YOUTUBE = "com.google.android.youtube"
    const val TIKTOK = "com.zhiliaoapp.musically"
    const val TIKTOK_ASIA = "com.ss.android.ugc.trill"

    val instagram = DetectionRule(
        packageName = INSTAGRAM,
        appName = "Instagram",
        sectionName = "Reels",
        signals = listOf(
            WeightedSignal(SelectedTab(setOf("reels")), 3),
            WeightedSignal(ViewIdPresent(setOf("clips_viewer_view_pager", "clips_viewer_container", "clips_video_container")), 3),
            WeightedSignal(FullScreenPager(), 2),
            WeightedSignal(RightActionStack(setOf("like", "comment", "share", "send", "remix")), 2),
            WeightedSignal(LabelPresent(setOf("original audio", "reels audio", "audio ·", "• audio")), 1),
        ),
        threshold = 4,
    )

    val youtube = DetectionRule(
        packageName = YOUTUBE,
        appName = "YouTube",
        sectionName = "Shorts",
        signals = listOf(
            WeightedSignal(SelectedTab(setOf("shorts")), 3),
            WeightedSignal(ViewIdPresent(setOf("reel_recycler", "reel_player_page_container", "reel_watch_player", "shorts_container")), 3),
            WeightedSignal(FullScreenPager(), 2),
            WeightedSignal(RightActionStack(setOf("like", "dislike", "comment", "share", "remix")), 2),
            WeightedSignal(LabelPresent(setOf("subscribe to", "original sound", "shorts")), 1),
        ),
        threshold = 4,
    )

    private fun tiktok(pkg: String) = DetectionRule(
        packageName = pkg,
        appName = "TikTok",
        sectionName = "video feed",
        signals = listOf(
            WeightedSignal(FullScreenPager(), 2),
            WeightedSignal(RightActionStack(setOf("like", "comment", "share", "favorite", "favourite")), 2),
            WeightedSignal(LabelPresent(setOf("original sound", "for you", "following")), 1),
        ),
        threshold = 4,
    )

    val all: List<DetectionRule> = listOf(instagram, youtube, tiktok(TIKTOK), tiktok(TIKTOK_ASIA))

    val supportedPackages: Set<String> = all.map { it.packageName }.toSet()

    /** Monitored out of the box; the user can change this during onboarding. */
    val defaultPackages: Set<String> = supportedPackages

    fun appName(packageName: String): String = all.firstOrNull { it.packageName == packageName }?.appName ?: packageName
}
