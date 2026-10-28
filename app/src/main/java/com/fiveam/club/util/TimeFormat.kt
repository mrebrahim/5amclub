package com.fiveam.club.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object TimeFormat {
    private val hm = DateTimeFormatter.ofPattern("h:mm", Locale.US)
    private val dayMonth = DateTimeFormatter.ofPattern("d/M", Locale.US)

    fun time(hour: Int, minute: Int): String = time(LocalTime.of(hour, minute))

    fun time(t: LocalTime): String = hm.format(t) + if (t.hour < 12) " ص" else " م"

    fun time(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        time(Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalTime())

    fun date(d: LocalDate): String = dayMonth.format(d)

    fun dateTime(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val z = Instant.ofEpochMilli(epochMillis).atZone(zone)
        return "${relativeDay(z.toLocalDate())} ${time(z.toLocalTime())}"
    }

    fun relativeDay(d: LocalDate, today: LocalDate = LocalDate.now()): String = when (d) {
        today -> "النهارده"
        today.plusDays(1) -> "بكرة"
        today.minusDays(1) -> "امبارح"
        else -> date(d)
    }

    fun duration(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h == 0 -> "$m دقيقة"
            m == 0 -> "$h ساعة"
            else -> "$h س $m د"
        }
    }

    /** "بعد 7 س 20 د" style countdown. */
    fun until(epochMillis: Long, now: Long = System.currentTimeMillis()): String {
        val mins = ((epochMillis - now) / 60_000).coerceAtLeast(0).toInt()
        return "بعد " + duration(mins)
    }

    fun zoned(epochMillis: Long): ZonedDateTime = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())

    fun localDate(epochMillis: Long): LocalDate = zoned(epochMillis).toLocalDate()

    fun startOfDay(d: LocalDate): Long = d.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
}
