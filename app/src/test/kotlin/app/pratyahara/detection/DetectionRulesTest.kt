package app.pratyahara.detection

import app.pratyahara.core.detection.DetectionEngine
import app.pratyahara.core.detection.UiNode
import app.pratyahara.core.detection.UiSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Structural fixtures for each app's screens. When an app changes its UI, add a fixture that
 * reproduces the new layout (from the Detection check screen) and adjust DetectionRules until it passes.
 */
class DetectionRulesTest {
    private val engine = DetectionEngine(DetectionRules.all)
    private val w = 1080
    private val h = 2400

    private fun n(
        cls: String = "android.widget.FrameLayout", id: String? = null, desc: String? = null, text: String? = null,
        selected: Boolean = false, scrollable: Boolean = false, l: Int = 0, t: Int = 0, r: Int = 100, b: Int = 100,
    ) = UiNode(cls, id, text, desc, selected, scrollable, l, t, r, b)

    private fun tab(label: String, selected: Boolean, index: Int) =
        n(desc = label, selected = selected, l = index * 216, t = 2260, r = index * 216 + 216, b = 2400)

    private fun rightButtons(vararg labels: String) =
        labels.mapIndexed { i, l -> n(desc = l, l = 960, t = 1200 + i * 150, r = 1060, b = 1300 + i * 150) }.toTypedArray()

    private val fullPager = n(cls = "androidx.viewpager.widget.ViewPager", scrollable = true, r = w, b = h)

    @Test fun `instagram reels tab`() {
        val s = UiSnapshot(
            DetectionRules.INSTAGRAM, w, h,
            listOf(tab("Home", false, 0), tab("Search and explore", false, 1), tab("Reels", true, 2), fullPager) +
                rightButtons("Like", "Comment", "Share").toList(),
        )
        assertTrue(engine.evaluate(s)!!.isShortForm)
    }

    @Test fun `instagram home feed and messages stay open`() {
        val feed = n(cls = "androidx.recyclerview.widget.RecyclerView", scrollable = true, t = 180, r = w, b = 2260)
        val home = UiSnapshot(
            DetectionRules.INSTAGRAM, w, h,
            listOf(tab("Home", true, 0), tab("Reels", false, 2), feed, n(desc = "Like", l = 20, t = 1500, r = 120, b = 1600)),
        )
        assertFalse(engine.evaluate(home)!!.isShortForm)

        val dms = UiSnapshot(DetectionRules.INSTAGRAM, w, h, listOf(n(text = "Messages"), feed))
        assertFalse(engine.evaluate(dms)!!.isShortForm)
    }

    @Test fun `instagram reel opened from a message, without the tab`() {
        val s = UiSnapshot(
            DetectionRules.INSTAGRAM, w, h,
            listOf(n(id = "clips_viewer_view_pager", cls = "androidx.viewpager.widget.ViewPager", scrollable = true, r = w, b = h)) +
                rightButtons("Like", "Comment", "Send").toList(),
        )
        assertTrue(engine.evaluate(s)!!.isShortForm)
    }

    /** Reported from a real phone: a profile was blocked after the limit. Its grid tiles mention likes and comments. */
    @Test fun `instagram profile with its reels grid stays open`() {
        val grid = n(cls = "androidx.viewpager.widget.ViewPager", scrollable = true, t = 120, r = w, b = 2260)
        val tiles = (0 until 3).flatMap { row ->
            (0 until 3).map { col ->
                n(desc = "Reel by someone at row ${row + 1}, column ${col + 1}. 1,204 likes, 33 comments, share", l = col * 360, t = 1200 + row * 480, r = col * 360 + 358, b = 1680 + row * 480)
            }
        }
        val s = UiSnapshot(
            DetectionRules.INSTAGRAM, w, h,
            listOf(
                n(text = "120 posts", t = 300, b = 360), n(text = "4,511 followers", t = 300, b = 360), n(text = "312 following", t = 300, b = 360),
                n(desc = "Reels", selected = true, t = 1100, b = 1180), n(text = "Original audio", t = 1500, b = 1540),
                grid, tab("Home", false, 0), tab("Reels", false, 2), tab("Profile", true, 4),
            ) + tiles,
        )
        assertFalse(engine.evaluate(s)!!.isShortForm)
    }

    @Test fun `instagram reel opened from a profile is still detected`() {
        val s = UiSnapshot(
            DetectionRules.INSTAGRAM, w, h,
            listOf(
                n(id = "clips_viewer_view_pager", cls = "androidx.viewpager.widget.ViewPager", scrollable = true, r = w, b = h),
                n(text = "Original audio", t = 2000, b = 2040), tab("Profile", true, 4),
            ) + rightButtons("Like", "Comment", "Share").toList(),
        )
        assertTrue(engine.evaluate(s)!!.isShortForm)
    }

    @Test fun `youtube shorts tab`() {
        val s = UiSnapshot(
            DetectionRules.YOUTUBE, w, h,
            listOf(tab("Home", false, 0), tab("Shorts", true, 1), n(id = "reel_recycler", cls = "androidx.recyclerview.widget.RecyclerView", scrollable = true, r = w, b = h)) +
                rightButtons("like this video", "Dislike this video", "Comments", "Share").toList(),
        )
        val r = engine.evaluate(s)!!
        assertTrue(r.isShortForm)
        assertEquals(r.threshold <= r.score, true)
    }

    @Test fun `youtube regular video is not shorts`() {
        val player = n(id = "watch_player", l = 0, t = 0, r = w, b = 610)
        val s = UiSnapshot(
            DetectionRules.YOUTUBE, w, h,
            listOf(player, n(desc = "like this video", l = 40, t = 900, r = 200, b = 980), n(desc = "Share", l = 400, t = 900, r = 560, b = 980), n(text = "Shorts", t = 1400, r = 300, b = 1460)),
        )
        assertFalse(engine.evaluate(s)!!.isShortForm)
    }

    @Test fun `tiktok feed is detected, inbox is not`() {
        val feed = UiSnapshot(DetectionRules.TIKTOK, w, h, listOf(fullPager) + rightButtons("Like", "Comment", "Favorites", "Share").toList())
        assertTrue(engine.evaluate(feed)!!.isShortForm)
        val inbox = UiSnapshot(DetectionRules.TIKTOK, w, h, listOf(n(text = "Inbox"), n(cls = "androidx.recyclerview.widget.RecyclerView", scrollable = true, t = 200, r = w, b = 2200)))
        assertFalse(engine.evaluate(inbox)!!.isShortForm)
    }
}
