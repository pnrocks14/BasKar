package app.pratyahara.core.lock

import app.pratyahara.core.tasks.TaskGate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockPolicyTest {
    private val base = LockInputs(
        blockingEnabled = true,
        budgetMinutes = 30,
        usedSecondsToday = 0,
        unlocksUsedToday = 0,
        taskGate = TaskGate.OPEN,
        cooldownUntilMillis = 0,
        nowMillis = 1_000,
    )

    @Test fun `allowed with the remaining budget`() {
        assertEquals(LockState.Allowed(30 * 60L - 600), LockPolicy.evaluate(base.copy(usedSecondsToday = 600)))
    }

    @Test fun `locked once the budget is used`() {
        val s = LockPolicy.evaluate(base.copy(usedSecondsToday = 30 * 60L))
        assertEquals(LockState.BudgetLocked(unlocksLeft = 2), s)
        assertTrue(LockPolicy.canEarnUnlock(s))
    }

    @Test fun `each unlock adds five minutes, at most twice`() {
        val one = LockPolicy.evaluate(base.copy(usedSecondsToday = 30 * 60L, unlocksUsedToday = 1))
        assertEquals(LockState.Allowed(5 * 60L), one)
        val spent = LockPolicy.evaluate(base.copy(usedSecondsToday = 40 * 60L, unlocksUsedToday = 2))
        assertEquals(LockState.BudgetLocked(unlocksLeft = 0), spent)
        assertFalse(LockPolicy.canEarnUnlock(spent))
    }

    @Test fun `a reel limit closes Reels even with minutes left`() {
        val limited = base.copy(reelLimit = 50, reelsToday = 50)
        assertEquals(LockState.BudgetLocked(unlocksLeft = 2, byReels = true), LockPolicy.evaluate(limited))
        assertEquals(LockState.Allowed(30 * 60L), LockPolicy.evaluate(limited.copy(reelsToday = 49)))
        assertEquals(LockState.Allowed(30 * 60L), LockPolicy.evaluate(limited.copy(reelLimit = 0)))
    }

    @Test fun `a squat pass adds ten reels`() {
        val s = LockPolicy.evaluate(base.copy(reelLimit = 30, reelsToday = 35, unlocksUsedToday = 1))
        assertTrue(s is LockState.Allowed)
        assertTrue(LockPolicy.evaluate(base.copy(reelLimit = 30, reelsToday = 40, unlocksUsedToday = 1)) is LockState.BudgetLocked)
    }

    @Test fun `cooldown blocks even with time left`() {
        val s = LockPolicy.evaluate(base.copy(cooldownUntilMillis = 5_000))
        assertEquals(LockState.Cooldown(5_000), s)
        assertTrue(s.blocksReels)
    }

    @Test fun `task gate wins over budget, and squats cannot clear it`() {
        val s = LockPolicy.evaluate(base.copy(taskGate = TaskGate.NEEDS_NEW_TASK))
        assertEquals(LockState.TaskLocked(TaskGate.NEEDS_NEW_TASK), s)
        assertFalse(LockPolicy.canEarnUnlock(s))
    }

    @Test fun `disabled blocking blocks nothing`() {
        val s = LockPolicy.evaluate(base.copy(blockingEnabled = false, usedSecondsToday = 99_999))
        assertEquals(LockState.Disabled, s)
        assertFalse(s.blocksReels)
    }
}

class FocusHoursTest {
    private val night = FocusHours(22 * 60 + 30, 7 * 60)

    @Test fun `a window across midnight`() {
        assertTrue(night.isActive(23 * 60))
        assertTrue(night.isActive(3 * 60))
        assertFalse(night.isActive(7 * 60))
        assertFalse(night.isActive(12 * 60))
        assertEquals(60, night.minutesUntilEnd(6 * 60))
        assertEquals("22:30", FocusHours.format(night.startMinute))
    }

    @Test fun `a daytime window and an empty one`() {
        val study = FocusHours(9 * 60, 13 * 60)
        assertTrue(study.isActive(9 * 60))
        assertFalse(study.isActive(13 * 60))
        assertFalse(FocusHours(600, 600).isActive(600))
    }

    @Test fun `focus hours close reels even with time left, and squats can't open them`() {
        val s = LockPolicy.evaluate(
            LockInputs(true, 30, 0, 0, app.pratyahara.core.tasks.TaskGate.OPEN, 0, 1_000, night, 23 * 60),
        )
        assertEquals(LockState.FocusLocked(7 * 60), s)
        assertTrue(s.blocksReels)
        assertFalse(LockPolicy.canEarnUnlock(s))
    }
}
