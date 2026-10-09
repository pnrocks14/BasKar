package app.pratyahara.core.buddy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BuddyTest {

    @Test fun `first visit says hi, later ones say you again`() {
        val b = Buddy(seed = 1)
        assertEquals(Chip.Kind.HELLO, b.onEnter(0, visitsToday = 1, minutesLeft = 25)!!.kind)
        b.onLeave(10_000)
        val again = b.onEnter(200_000, visitsToday = 2, minutesLeft = 20)!!
        assertEquals(Chip.Kind.AGAIN, again.kind)
        assertTrue(again.subtitle!!.contains("20 min"))
    }

    @Test fun `a quick return is the same visit`() {
        val b = Buddy()
        b.onEnter(0, 1, 25)
        b.onLeave(10_000)
        assertNull(b.onEnter(30_000, 2, 25))
    }

    @Test fun `a quote every few minutes of watching`() {
        val b = Buddy(quoteEverySeconds = 180)
        b.onEnter(0, 1, 30)
        val chips = (1..600).mapNotNull { b.onSecond("2026-10-09", secondsLeft = 1_800L - it) }
        assertEquals(3, chips.count { it.kind == Chip.Kind.QUOTE })
        assertTrue(chips.all { it.subtitle!!.isNotBlank() })
    }

    @Test fun `warns at five minutes and at the last minute, once a day`() {
        val b = Buddy(quoteEverySeconds = 0)
        b.onEnter(0, 1, 6)
        val chips = (360 downTo 1).mapNotNull { b.onSecond("d1", it.toLong()) }
        assertEquals(2, chips.size)
        assertTrue(chips[0].title.startsWith("5 minutes left"))
        assertTrue(chips[1].title.startsWith("Last minute"))
        assertNull(b.onSecond("d1", 30))
        assertNotNull(b.onSecond("d2", 30))
    }

    @Test fun `every quote names a source`() {
        assertTrue(Quotes.all.size >= 20)
        Quotes.all.forEach { assertTrue(it.text, it.source.isNotBlank()) }
        assertEquals(Quotes.ofDay(100), Quotes.ofDay(100))
    }
}
