package com.fiveam.club

import com.fiveam.club.alarm.core.NextTrigger
import com.fiveam.club.util.WeekDays
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class NextTriggerTest {
    private val cairo = ZoneId.of("Africa/Cairo")

    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int, zone: ZoneId = cairo) =
        ZonedDateTime.of(LocalDateTime.of(y, m, d, h, min), zone)

    @Test
    fun `daily alarm later today rings today`() {
        val now = at(2026, 10, 28, 3, 0) // Wednesday
        assertEquals(at(2026, 10, 28, 5, 0), NextTrigger.compute(5, 0, WeekDays.ALL, now))
    }

    @Test
    fun `daily alarm already passed rings tomorrow`() {
        val now = at(2026, 10, 28, 5, 0)
        assertEquals(at(2026, 10, 29, 5, 0), NextTrigger.compute(5, 0, WeekDays.ALL, now))
    }

    @Test
    fun `weekday mask skips disabled days`() {
        val now = at(2026, 10, 29, 6, 0) // Thursday after the alarm
        val mask = WeekDays.ALL xor WeekDays.bit(DayOfWeek.FRIDAY) xor WeekDays.bit(DayOfWeek.SATURDAY)
        assertEquals(at(2026, 11, 1, 5, 0), NextTrigger.compute(5, 0, mask, now)) // Sunday
    }

    @Test
    fun `one time alarm picks next occurrence`() {
        val now = at(2026, 10, 28, 22, 0)
        assertEquals(at(2026, 10, 29, 4, 30), NextTrigger.compute(4, 30, 0, now))
    }

    @Test
    fun `same wall clock after timezone change`() {
        val now = at(2026, 10, 28, 1, 0, ZoneId.of("Asia/Dubai"))
        val next = NextTrigger.compute(5, 0, WeekDays.ALL, now)
        assertEquals(5, next.hour)
        assertEquals(ZoneId.of("Asia/Dubai"), next.zone)
    }

    @Test
    fun `alarm inside DST gap is shifted forward, not skipped`() {
        val ny = ZoneId.of("America/New_York")
        val now = at(2026, 3, 7, 23, 0, ny) // DST starts 2026-03-08 02:00
        val next = NextTrigger.compute(2, 30, WeekDays.ALL, now)
        assertEquals(8, next.dayOfMonth)
        assertEquals(3, next.hour)
    }
}
