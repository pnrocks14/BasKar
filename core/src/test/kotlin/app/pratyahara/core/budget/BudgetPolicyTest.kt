package app.pratyahara.core.budget

import app.pratyahara.core.time.Stamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BudgetPolicyTest {
    private val now = Stamp(1_000L, 1_000L, 1)
    private val goodReason = "I'm travelling for six hours tomorrow with no work to do. " +
        "I want to watch the cooking videos I saved this week instead of scrolling randomly."

    @Test fun `lowering applies immediately without a reason`() {
        val r = BudgetPolicy.requestChange(30, 20, "", now) { "id" }
        assertEquals(BudgetRequestResult.AppliedNow(20), r)
    }

    @Test fun `raising needs a real reason`() {
        val r = BudgetPolicy.requestChange(30, 40, "just bored", now) { "id" }
        assertTrue(r is BudgetRequestResult.Rejected)
    }

    @Test fun `raising with a reason is scheduled with the table's delay`() {
        val r = BudgetPolicy.requestChange(30, 45, goodReason, now) { "id" }
        r as BudgetRequestResult.Scheduled
        assertEquals("45", r.change.value)
        assertEquals(DelayTable.increaseDelay(15).toMillis(), r.change.delayMillis)
    }

    @Test fun `budget is capped at the maximum`() {
        val r = BudgetPolicy.requestChange(30, 500, goodReason, now) { "id" }
        r as BudgetRequestResult.Scheduled
        assertEquals(DelayTable.MAX_BUDGET_MINUTES.toString(), r.change.value)
        assertEquals(DelayTable.increaseDelay(90).toMillis(), r.change.delayMillis)
    }

    @Test fun `budget cannot drop below the minimum`() {
        val r = BudgetPolicy.requestChange(30, 0, "", now) { "id" }
        assertEquals(BudgetRequestResult.AppliedNow(DelayTable.MIN_BUDGET_MINUTES), r)
    }
}
