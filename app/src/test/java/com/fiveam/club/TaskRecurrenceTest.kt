package com.fiveam.club

import com.fiveam.club.data.db.Recurrence
import com.fiveam.club.data.db.TaskEntity
import com.fiveam.club.data.db.TaskStatus
import com.fiveam.club.tasks.TaskRecurrence
import com.fiveam.club.tasks.TaskReminderPolicy
import com.fiveam.club.util.WeekDays
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class TaskRecurrenceTest {
    private val zone = ZoneId.of("Africa/Cairo")
    private fun at(d: Int, h: Int, m: Int = 0) = ZonedDateTime.of(LocalDateTime.of(2026, 10, d, h, m), zone)

    @Test
    fun `AC-11 reminder 15 minutes before a 10am task`() {
        val due = at(28, 10).toInstant().toEpochMilli()
        val task = TaskEntity(title = "x", dueAt = due, reminderOffsetMin = 15)
        val now = at(28, 8).toInstant().toEpochMilli()
        assertEquals(at(28, 9, 45).toInstant().toEpochMilli(), TaskReminderPolicy.reminderTime(task, now))
    }

    @Test
    fun `AC-13 snooze overrides the regular reminder`() {
        val now = at(28, 9, 45).toInstant().toEpochMilli()
        val task = TaskEntity(title = "x", dueAt = at(28, 10).toInstant().toEpochMilli(), snoozedUntil = now + 600_000)
        assertEquals(now + 600_000, TaskReminderPolicy.reminderTime(task, now))
    }

    @Test
    fun `AC-14 completed task has no reminder`() {
        val task = TaskEntity(title = "x", dueAt = at(28, 10).toInstant().toEpochMilli(), status = TaskStatus.DONE.name)
        assertNull(TaskReminderPolicy.reminderTime(task, at(28, 8).toInstant().toEpochMilli()))
    }

    @Test
    fun `daily rolls to next day`() {
        assertEquals(at(29, 10), TaskRecurrence.next(at(28, 10), Recurrence.DAILY, 0, at(28, 10)))
    }

    @Test
    fun `overdue daily task rolls past now`() {
        assertEquals(at(31, 10), TaskRecurrence.next(at(28, 10), Recurrence.DAILY, 0, at(30, 12)))
    }

    @Test
    fun `AC-15 custom days respect the weekday set`() {
        // 2026-10-28 is a Wednesday; next allowed day is Saturday the 31st.
        val mask = WeekDays.bit(DayOfWeek.SATURDAY) or WeekDays.bit(DayOfWeek.MONDAY)
        assertEquals(at(31, 10), TaskRecurrence.next(at(28, 10), Recurrence.CUSTOM, mask, at(28, 10)))
    }

    @Test
    fun `non repeating has no next`() {
        assertNull(TaskRecurrence.next(at(28, 10), Recurrence.NONE, 0, at(28, 10)))
        assertNull(TaskRecurrence.next(at(28, 10), Recurrence.CUSTOM, 0, at(28, 10)))
    }
}
