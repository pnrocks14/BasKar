package app.pratyahara

import android.app.UiAutomation
import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import app.pratyahara.core.lock.LockState
import app.pratyahara.data.DayUsage
import app.pratyahara.detection.DetectionRules
import app.pratyahara.detection.ReelsAccessibilityService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.io.File

/**
 * Runs on an emulator in CI, in name order against one fresh install:
 * onboarding, the accessibility service, then blocking inside a stand-in Instagram (testapps/fakeinsta).
 * Screenshots go to the app's files/screens folder; CI pulls them as an artifact.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class EndToEndTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context get() = instrumentation.targetContext
    private lateinit var device: UiDevice

    @Before
    fun setUp() {
        // By default a test's UiAutomation switches every accessibility service off, ours included.
        Configurator.getInstance().uiAutomationFlags = UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES
        device = UiDevice.getInstance(instrumentation)
        runBlocking { context.engine.store.loaded.first { it } }
    }

    private fun shell(cmd: String): String = device.executeShellCommand(cmd)

    private fun shot(name: String) {
        val dir = File(context.filesDir, "screens").apply { mkdirs() }
        device.waitForIdle()
        Thread.sleep(400)
        device.takeScreenshot(File(dir, "$name.png"))
    }

    private fun waitUntil(timeoutMs: Long = 10_000, what: String, check: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (check()) return
            Thread.sleep(250)
        }
        throw AssertionError("Timed out waiting for $what")
    }

    private fun fakeInstaInstalled() = runCatching { context.packageManager.getPackageInfo(DetectionRules.INSTAGRAM, 0) }.isSuccess

    @Test
    fun a_onboarding_reaches_home() {
        compose.onNodeWithText("Let's begin").performScrollTo()
        shot("01-welcome")
        compose.onNodeWithText("Let's begin").performClick()

        compose.onNodeWithText("Before you turn on blocking").assertIsDisplayed()
        shot("02-disclosure")
        compose.onNodeWithText("I understand", substring = true).performScrollTo().performClick()
        compose.onNodeWithText("Agree and continue").performScrollTo().performClick()

        compose.onNodeWithText("Set your limits").assertIsDisplayed()
        shot("03-limits")
        compose.onNodeWithText("Continue").performScrollTo().performClick()

        compose.onNodeWithText("Three switches").assertIsDisplayed()
        shot("04-permissions")
        compose.onNodeWithText("Start", substring = true).performScrollTo().performClick()

        compose.waitUntil(5_000) { context.engine.store.current.onboarded }
        compose.onNodeWithText("minutes left today", substring = true).assertIsDisplayed()
        shot("05-home")
        assertTrue(context.engine.store.current.disclosureAcceptedAt > 0)
    }

    @Test
    fun b_accessibility_service_starts() {
        val component = "${context.packageName}/${ReelsAccessibilityService::class.java.name}"
        shell("settings put secure enabled_accessibility_services $component")
        shell("settings put secure accessibility_enabled 1")
        waitUntil(15_000, "the accessibility service to start") { ReelsAccessibilityService.isRunning.value }
    }

    @Test
    fun c_budget_raise_needs_a_reason_and_waits() {
        compose.onNodeWithText("Settings").performScrollTo().performClick()
        compose.onNodeWithText("Change daily limit").performScrollTo().performClick()
        compose.onNodeWithText("+").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("need it")
        compose.onNodeWithText("Ask for 35 min").performScrollTo().performClick()
        compose.onNodeWithText("Give a real reason", substring = true).performScrollTo().assertIsDisplayed()
        shot("06-budget-rejected")

        compose.onNode(hasSetTextAction()).performTextReplacement(
            "My cousin's wedding videos were all posted as reels today. I promised her I would watch every one and send my favourites tonight."
        )
        compose.onNodeWithText("Ask for 35 min").performScrollTo().performClick()
        compose.onNodeWithText("Your limit becomes 35 min", substring = true).performScrollTo().assertIsDisplayed()
        shot("07-budget-scheduled")
        assertEquals(30, context.engine.store.current.budgetMinutes)
        assertEquals(1, context.engine.store.current.pending.size)
    }

    @Test
    fun d_nightly_task_validation() {
        compose.activityRule.scenario.onActivity { it.startActivity(MainActivity.intent(it, "task")) }
        compose.onNodeWithText("Save").performScrollTo().performClick()
        compose.onNodeWithText("Write something first.").assertIsDisplayed()
        compose.onNode(hasSetTextAction()).performTextInput("idk")
        compose.onNodeWithText("Save").performScrollTo().performClick()
        compose.onNodeWithText("isn't enough on its own", substring = true).assertIsDisplayed()
        shot("08-task-rejected")
    }

    @Test
    fun e_reels_are_blocked_and_feed_stays_open() {
        assumeTrue("stand-in Instagram not installed", fakeInstaInstalled())
        waitUntil(15_000, "the accessibility service") { ReelsAccessibilityService.isRunning.value }
        val engine = context.engine
        val today = engine.today().toString()
        runBlocking {
            engine.store.update {
                it.copy(
                    monitored = setOf(DetectionRules.INSTAGRAM),
                    budgetMinutes = 5,
                    pending = emptyList(),
                    tasks = emptyList(),
                    days = mapOf(today to DayUsage(secondsByApp = mapOf(DetectionRules.INSTAGRAM to 300L))),
                )
            }
        }
        waitUntil(what = "the seeded day to load") { engine.lockState() is LockState.BudgetLocked }

        context.startActivity(context.packageManager.getLaunchIntentForPackage(DetectionRules.INSTAGRAM)!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        assertTrue(device.wait(Until.hasObject(By.text("Fake feed")), 10_000))
        Thread.sleep(1_500)
        assertFalse("feed must stay open", device.hasObject(By.text("Back to the feed")))
        shot("09-feed-open")

        device.findObject(By.desc("Reels")).click()
        assertTrue("overlay should appear on Reels", device.wait(Until.hasObject(By.text("Back to the feed")), 10_000))
        assertNotNull(device.findObject(By.textContains("5 minutes for today")))
        shot("10-reels-blocked")
        assertTrue(engine.store.current.day(engine.today()).blocks >= 1)

        device.findObject(By.text("Back to the feed")).click()
        assertTrue("back should land on the feed", device.wait(Until.hasObject(By.text("Fake feed")), 10_000))
        Thread.sleep(3_000)
        assertFalse("overlay must not come back on the feed", device.hasObject(By.text("Back to the feed")))
        shot("11-back-on-feed")
    }

    @Test
    fun f_unlock_then_cooldown_then_reels_play_and_count() {
        assumeTrue("stand-in Instagram not installed", fakeInstaInstalled())
        val engine = context.engine
        runBlocking { assertTrue(engine.grantUnlock()) }
        waitUntil(what = "the cooldown to start") { engine.lockState() is LockState.Cooldown }

        context.startActivity(MainActivity.intent(context, "cooldown").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        assertTrue(device.wait(Until.hasObject(By.text("Nicely done")), 10_000))
        shot("12-cooldown")
        waitUntil(15_000, "the cooldown to end") { engine.lockState() is LockState.Allowed }

        val before = engine.store.current.day(engine.today()).totalSeconds
        context.startActivity(context.packageManager.getLaunchIntentForPackage(DetectionRules.INSTAGRAM)!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        assertTrue(device.wait(Until.hasObject(By.text("Fake feed")), 10_000))
        device.findObject(By.desc("Reels")).click()
        Thread.sleep(2_500)
        assertFalse("unlocked Reels must play", device.hasObject(By.text("Back to the feed")))
        shot("13-reels-unlocked")

        waitUntil(25_000, "Reels time to be counted") {
            engine.store.current.day(engine.today()).totalSeconds >= before + 5
        }
        device.pressBack()
    }

    @Test
    fun g_home_after_the_day() {
        context.startActivity(MainActivity.intent(context, "home").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        device.wait(Until.hasObject(By.text("Pratyahara")), 10_000)
        shot("14-home-after")
        context.startActivity(MainActivity.intent(context, "summary").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        device.wait(Until.hasObject(By.text("Minutes on Reels and Shorts")), 10_000)
        shot("15-summary")
    }
}
