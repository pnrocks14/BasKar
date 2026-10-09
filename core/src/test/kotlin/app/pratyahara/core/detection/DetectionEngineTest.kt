package app.pratyahara.core.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectionEngineTest {
    private val W = 1080
    private val H = 2340
    private val pkg = "com.example.social"

    private val rule = DetectionRule(
        packageName = pkg,
        appName = "Example",
        sectionName = "Reels",
        signals = listOf(
            WeightedSignal(Signal.SelectedTab(setOf("Reels")), 3),
            WeightedSignal(Signal.ViewIdPresent(setOf("clips_pager")), 3),
            WeightedSignal(Signal.FullScreenPager(), 2),
            WeightedSignal(Signal.RightActionStack(setOf("like", "comment", "share")), 2),
            WeightedSignal(Signal.LabelPresent(setOf("original audio")), 1),
        ),
        threshold = 4,
    )
    private val engine = DetectionEngine(listOf(rule))

    private fun node(
        cls: String = "android.view.View", id: String? = null, text: String? = null, desc: String? = null,
        selected: Boolean = false, scrollable: Boolean = false, l: Int = 0, t: Int = 0, r: Int = 100, b: Int = 100,
    ) = UiNode(cls, id, text, desc, selected, scrollable, l, t, r, b)

    private fun snap(vararg nodes: UiNode) = UiSnapshot(pkg, W, H, nodes.toList())

    private val reelsTab = node(desc = "Reels", selected = true, l = 400, t = 2200, r = 600, b = 2340)
    private val homeTab = node(desc = "Home", selected = true, l = 0, t = 2200, r = 200, b = 2340)
    private val pager = node(cls = "androidx.viewpager.widget.ViewPager", scrollable = true, r = W, b = H)
    private val actions = arrayOf(
        node(desc = "Like", l = 950, t = 1300, r = 1050, b = 1400),
        node(desc = "Comment", l = 950, t = 1450, r = 1050, b = 1550),
        node(desc = "Share", l = 950, t = 1600, r = 1050, b = 1700),
    )

    @Test fun `reels tab with its player is detected`() {
        val r = engine.evaluate(snap(reelsTab, pager, *actions))!!
        assertTrue(r.isShortForm)
        assertEquals(7, r.score)
    }

    @Test fun `a reel opened from a message is detected without the tab`() {
        val r = engine.evaluate(snap(homeTab, pager, *actions, node(text = "Original audio")))!!
        assertTrue(r.isShortForm)
    }

    @Test fun `home feed with like buttons is not detected`() {
        val feed = node(cls = "androidx.recyclerview.widget.RecyclerView", scrollable = true, t = 200, r = W, b = 1800)
        val inlineLike = node(desc = "Like", l = 20, t = 900, r = 120, b = 1000)
        val r = engine.evaluate(snap(homeTab, feed, inlineLike))!!
        assertFalse(r.isShortForm)
    }

    @Test fun `a renamed view id alone is not enough, and its absence is survivable`() {
        assertFalse(engine.evaluate(snap(node(id = "clips_pager")))!!.isShortForm)
        assertTrue(engine.evaluate(snap(reelsTab, pager))!!.isShortForm)
    }

    @Test fun `wide grid tiles on the right are not an action stack`() {
        val tiles = (0 until 3).map { node(desc = "Reel. 1,204 likes, 33 comments, share", l = 720, t = 1200 + it * 400, r = 1078, b = 1580 + it * 400) }
        assertFalse(Signal.RightActionStack(setOf("like", "comment", "share")).matches(snap(*tiles.toTypedArray())))
        assertTrue(Signal.RightActionStack(setOf("like", "comment", "share")).matches(snap(*actions)))
    }

    @Test fun `a negative signal can veto a look-alike screen`() {
        val vetoed = rule.copy(signals = rule.signals + WeightedSignal(Signal.AllLabelsPresent(setOf("followers", "following")), -4))
        val e = DetectionEngine(listOf(vetoed))
        val profile = snap(pager, *actions, node(text = "10 followers"), node(text = "5 following"))
        assertFalse(e.evaluate(profile)!!.isShortForm)
        assertTrue(e.evaluate(snap(pager, *actions, node(text = "10 followers")))!!.isShortForm)
    }

    @Test fun `other apps are ignored`() {
        assertNull(engine.evaluate(UiSnapshot("com.other", W, H, listOf(pager))))
    }

    @Test fun `debouncer needs two positives in a row`() {
        val d = Debouncer(2)
        assertFalse(d.update(true))
        assertTrue(d.update(true))
        assertFalse(d.update(false))
        assertFalse(d.update(true))
    }
}
