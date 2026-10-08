package app.pratyahara.core.tasks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TaskLoopTest {
    private val d1 = LocalDate.of(2026, 10, 1)
    private val d2 = d1.plusDays(1)
    private val d3 = d1.plusDays(2)

    private fun task(day: LocalDate, written: Long, check: CheckIn? = null, checkedAt: Long? = null, late: Boolean = false) =
        DayTask(day, "Call the bank before noon", written, check, checkedAt, late)

    @Test fun `nothing owed before the first task`() {
        assertEquals(TaskGate.OPEN, TaskLoop.gate(d1, emptyList()))
        assertEquals(TaskGate.OPEN, TaskLoop.gate(d2, listOf(task(d2, written = 10))))
    }

    @Test fun `yes plus a new task keeps everything open`() {
        val tasks = listOf(task(d1, 1, CheckIn.DONE, 100), task(d2, 101))
        assertEquals(TaskGate.OPEN, TaskLoop.gate(d2, tasks))
    }

    @Test fun `no locks until the next task is written`() {
        val answered = listOf(task(d2, 1, CheckIn.NOT_DONE, checkedAt = 500))
        assertEquals(TaskGate.NEEDS_NEW_TASK, TaskLoop.gate(d2, answered))
        val rewritten = answered + task(d3, written = 600)
        assertEquals(TaskGate.OPEN, TaskLoop.gate(d2, rewritten))
        assertEquals(TaskGate.OPEN, TaskLoop.gate(d3, rewritten))
    }

    @Test fun `silence locks the next day until answered and rewritten`() {
        val silent = listOf(task(d1, 1), task(d2, 2))
        assertEquals(TaskGate.MISSED_CHECKIN, TaskLoop.gate(d2, silent))
        assertFalse(TaskLoop.squatsAllowedToday(d2, silent))

        val lateAnswer = TaskLoop.answer(silent[0], CheckIn.DONE, today = d2, nowMillis = 900)
        assertTrue(lateAnswer.late)
        val answered = listOf(lateAnswer, silent[1])
        assertEquals(TaskGate.NEEDS_NEW_TASK, TaskLoop.gate(d2, answered))
        assertFalse(TaskLoop.squatsAllowedToday(d2, answered))

        val rewritten = answered.filter { it.day != d2 } + task(d2, written = 1_000)
        assertEquals(TaskGate.OPEN, TaskLoop.gate(d2, rewritten))
    }

    @Test fun `skipping the nightly task asks for one`() {
        val tasks = listOf(task(d1, 1, CheckIn.DONE, 100))
        assertEquals(TaskGate.NEEDS_NEW_TASK, TaskLoop.gate(d2, tasks))
        assertEquals(d2, TaskLoop.nextTaskDay(d2, tasks))
    }

    @Test fun `honest no costs less than silence`() {
        val no = listOf(task(d1, 1, CheckIn.NOT_DONE, 100))
        val silent = listOf(task(d1, 1))
        assertTrue(TaskLoop.squatsAllowedToday(d2, no))
        assertFalse(TaskLoop.squatsAllowedToday(d2, silent))
        assertEquals(TaskGate.NEEDS_NEW_TASK, TaskLoop.gate(d2, no))
        assertEquals(TaskGate.MISSED_CHECKIN, TaskLoop.gate(d2, silent))
    }

    @Test fun `next task goes to tomorrow once today has one`() {
        assertEquals(d3, TaskLoop.nextTaskDay(d2, listOf(task(d2, 1))))
    }
}
