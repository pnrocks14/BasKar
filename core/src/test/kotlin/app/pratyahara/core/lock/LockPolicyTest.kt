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
