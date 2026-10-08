package app.pratyahara.core.time

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * A "day" in Pratyahara runs from 4:00 am to 4:00 am local time, so scrolling at 1 am
 * still counts toward the day you started it.
 */
object DayBoundary {
    const val ROLLOVER_HOUR = 4

    fun dayOf(epochMillis: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(zone).minusHours(ROLLOVER_HOUR.toLong()).toLocalDate()

    fun startOf(day: LocalDate, zone: ZoneId): Long =
        day.atTime(ROLLOVER_HOUR, 0).atZone(zone).toInstant().toEpochMilli()

    fun endOf(day: LocalDate, zone: ZoneId): Long = startOf(day.plusDays(1), zone)
}
