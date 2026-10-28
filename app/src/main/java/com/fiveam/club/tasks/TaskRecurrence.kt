package com.fiveam.club.tasks

import com.fiveam.club.data.db.Recurrence
import com.fiveam.club.data.db.TaskEntity
import com.fiveam.club.data.db.TaskStatus
import com.fiveam.club.util.WeekDays
import java.time.LocalDate
import java.time.ZonedDateTime

object TaskRecurrence {
    /**
     * The first occurrence after [after], keeping the task's wall-clock start time.
     * Returns null for non-repeating tasks or an empty custom day set.
     */
    fun next(dueAt: ZonedDateTime, rule: Recurrence, repeatDays: Int, after: ZonedDateTime): ZonedDateTime? {
        val time = dueAt.toLocalTime()
        val zone = dueAt.zone
        var date = dueAt.toLocalDate()
        fun at(d: LocalDate) = ZonedDateTime.of(d, time, zone)
        when (rule) {
            Recurrence.NONE -> return null
            Recurrence.DAILY -> {
                do {
                    date = date.plusDays(1)
                } while (!at(date).isAfter(after))
            }
            Recurrence.WEEKLY -> {
                do {
                    date = date.plusWeeks(1)
                } while (!at(date).isAfter(after))
            }
            Recurrence.CUSTOM -> {
                if (repeatDays and WeekDays.ALL == 0) return null
                do {
                    date = date.plusDays(1)
                } while (!at(date).isAfter(after) || !WeekDays.contains(repeatDays, date.dayOfWeek))
            }
        }
        return at(date)
    }

    fun describe(rule: Recurrence, repeatDays: Int): String = when (rule) {
        Recurrence.NONE -> "مرة واحدة"
        Recurrence.DAILY -> "كل يوم"
        Recurrence.WEEKLY -> "كل أسبوع"
        Recurrence.CUSTOM -> WeekDays.describe(repeatDays)
    }
}

object TaskReminderPolicy {
    /** Snooze wins over the regular offset; past times are skipped. */
    fun reminderTime(task: TaskEntity, now: Long): Long? {
        if (task.status == TaskStatus.DONE.name) return null
        task.snoozedUntil?.let { if (it > now) return it }
        val offset = task.reminderOffsetMin ?: return null
        return (task.dueAt - offset * 60_000L).takeIf { it > now }
    }
}
