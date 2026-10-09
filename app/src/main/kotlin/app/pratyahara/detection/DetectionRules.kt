package app.pratyahara.detection

import app.pratyahara.core.detection.DetectionRule
import app.pratyahara.core.detection.Signal.ActionRow
import app.pratyahara.core.detection.Signal.AllLabelsPresent
import app.pratyahara.core.detection.Signal.AllOf
import app.pratyahara.core.detection.Signal.FullScreenPager
import app.pratyahara.core.detection.Signal.LabelPresent
import app.pratyahara.core.detection.Signal.RightActionStack
import app.pratyahara.core.detection.Signal.SelectedTab
import app.pratyahara.core.detection.Signal.ViewIdContains
import app.pratyahara.core.detection.Signal.ViewIdPresent
import app.pratyahara.core.detection.WeightedSignal

/**
 * THE file to edit when an app changes its UI. See docs/detection-rules.md.
 *
 * Each rule adds up the weights of the signals it finds on screen; at or above `threshold`,
 * the screen counts as Reels/Shorts. Keep at least two independent signals able to reach the
 * threshold on their own, so one renamed view never breaks detection.
 *
 * Negative weights mark screens that are clearly something else (a profile page), so look-alike layouts
 * such as a profile's grid of reels don't add up to a block.
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
            WeightedSignal(ViewIdPresent(setOf("clips_viewer_view_pager", "clips_viewer_container", "clips_video_container")), 4),
            // Instagram's code calls Reels "clips"; the player keeps that name wherever it opens (Explore,
            // a shared reel in DMs, a profile). Only full-screen views count, so reels inside the home feed
            // or a chat don't.
            WeightedSignal(ViewIdContains(setOf("clips_"), exclude = setOf("clips_tab"), minHeight = 0.8), 2),
            // Posts opened from Explore scroll on forever too: the Explore tab plus a post's like/comment row.
            WeightedSignal(AllOf(listOf(SelectedTab(setOf("explore")), ActionRow(setOf("like", "comment", "share", "send")))), 4),
            WeightedSignal(FullScreenPager(), 2),
            WeightedSignal(RightActionStack(setOf("like", "comment", "share", "send", "remix")), 2),
            WeightedSignal(LabelPresent(setOf("original audio", "reels audio", "audio ·", "• audio")), 1),
            // Someone's profile, including your own: header counts are on screen. A reel opened from a profile
            // still scores enough through the player's view ID and its buttons.
            WeightedSignal(AllLabelsPresent(setOf("posts", "followers", "following")), -4),
            WeightedSignal(ViewIdPresent(setOf("profile_header_container", "row_profile_header", "profile_header_bio_text", "profile_tab_layout")), -4),
            // Reported from a real phone: the home feed was paused. A reel opened from it still scores enough.
            WeightedSignal(SelectedTab(setOf("home")), -2),
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
