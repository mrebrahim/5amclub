package com.fiveam.club.alarm.core

import com.fiveam.club.util.WeekDays
import java.time.LocalTime
import java.time.ZonedDateTime

object NextTrigger {
    /**
     * Next wall-clock occurrence of [hour]:[minute] strictly after [now], honoring [repeatDays].
     * Uses the zone carried by [now], so a timezone change simply means recomputing.
     * Times inside a DST gap are shifted forward by [ZonedDateTime.of].
     */
    fun compute(hour: Int, minute: Int, repeatDays: Int, now: ZonedDateTime): ZonedDateTime {
        val time = LocalTime.of(hour, minute)
        for (offset in 0L..7L) {
            val date = now.toLocalDate().plusDays(offset)
            val candidate = ZonedDateTime.of(date, time, now.zone)
            if (!candidate.isAfter(now)) continue
            if (repeatDays == 0 || WeekDays.contains(repeatDays, date.dayOfWeek)) return candidate
        }
        error("No trigger found for mask $repeatDays")
    }
}
