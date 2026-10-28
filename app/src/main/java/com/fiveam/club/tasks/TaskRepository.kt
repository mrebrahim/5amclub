package com.fiveam.club.tasks

import com.fiveam.club.data.db.Recurrence
import com.fiveam.club.data.db.TaskCompletionEntity
import com.fiveam.club.data.db.TaskDao
import com.fiveam.club.data.db.TaskEntity
import com.fiveam.club.data.db.TaskStatus
import com.fiveam.club.gamification.RewardEngine
import com.fiveam.club.gamification.RewardType
import com.fiveam.club.util.TimeFormat
import java.time.ZonedDateTime

class TaskRepository(
    private val dao: TaskDao,
    private val reminders: TaskReminderScheduler,
    private val rewards: RewardEngine,
) {
    val tasks = dao.observeAll()

    suspend fun get(id: Long) = dao.get(id)

    suspend fun save(task: TaskEntity): Long {
        // Editing the time invalidates an old snooze (edge case: "تغيير وقت مهمة").
        val previous = if (task.id != 0L) dao.get(task.id) else null
        val clean = if (previous != null && previous.dueAt != task.dueAt) task.copy(snoozedUntil = null) else task
        val rowId = dao.upsert(clean)
        val id = if (clean.id == 0L) rowId else clean.id
        reminders.schedule(clean.copy(id = id))
        return id
    }

    suspend fun delete(task: TaskEntity) {
        reminders.cancel(task.id)
        reminders.dismissNotification(task.id)
        dao.delete(task)
    }

    /** TSK-07/TSK-09: repeating tasks roll forward to their next occurrence. */
    suspend fun markDone(id: Long, now: Long = System.currentTimeMillis()) {
        val task = dao.get(id) ?: return
        if (task.status == TaskStatus.DONE.name) return
        val occurrence = TimeFormat.localDate(task.dueAt).toString()
        dao.insertCompletion(TaskCompletionEntity(taskId = id, occurrenceDate = occurrence, completedAt = now))
        rewards.grant(RewardType.TASK_DONE, "task:$id:$occurrence", now)
        val rule = runCatching { Recurrence.valueOf(task.recurrence) }.getOrDefault(Recurrence.NONE)
        val due = TimeFormat.zoned(task.dueAt)
        val after = if (now > task.dueAt) TimeFormat.zoned(now) else due
        val next = TaskRecurrence.next(due, rule, task.repeatDays, after)
        val updated = if (next == null) {
            task.copy(status = TaskStatus.DONE.name, completedAt = now, snoozedUntil = null)
        } else {
            task.copy(dueAt = next.toInstant().toEpochMilli(), completedAt = now, snoozedUntil = null)
        }
        dao.upsert(updated)
        reminders.dismissNotification(id)
        reminders.schedule(updated, now)
    }

    suspend fun reopen(id: Long) {
        val task = dao.get(id) ?: return
        val updated = task.copy(status = TaskStatus.PENDING.name, completedAt = null)
        dao.upsert(updated)
        reminders.schedule(updated)
    }

    suspend fun snooze(id: Long, minutes: Int = 10, now: Long = System.currentTimeMillis()) {
        val task = dao.get(id) ?: return
        if (task.status == TaskStatus.DONE.name) return
        val updated = task.copy(snoozedUntil = now + minutes * 60_000L)
        dao.upsert(updated)
        reminders.dismissNotification(id)
        reminders.schedule(updated, now)
    }

    /** Moves an overdue task to a new start time (TSK-10). */
    suspend fun reschedule(id: Long, newDueAt: Long) {
        val task = dao.get(id) ?: return
        save(task.copy(dueAt = newDueAt, snoozedUntil = null, status = TaskStatus.PENDING.name))
    }

    fun isOverdue(task: TaskEntity, now: Long = System.currentTimeMillis()): Boolean =
        task.status == TaskStatus.PENDING.name && task.dueAt + task.durationMin * 60_000L < now &&
            task.recurrence == Recurrence.NONE.name

    fun nextDay(dueAt: Long): Long = ZonedDateTime.now().plusDays(1)
        .with(TimeFormat.zoned(dueAt).toLocalTime()).toInstant().toEpochMilli()
}
