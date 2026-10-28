package com.fiveam.club.tasks

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationManagerCompat
import com.fiveam.club.data.db.TaskDao
import com.fiveam.club.data.db.TaskEntity
import com.fiveam.club.util.ExactAlarms
import com.fiveam.club.util.Notifications

/** Normal reminders, never a continuous alarm sound (TSK-11). */
class TaskReminderScheduler(private val context: Context, private val dao: TaskDao) {

    fun schedule(task: TaskEntity, now: Long = System.currentTimeMillis()) {
        cancel(task.id)
        val at = TaskReminderPolicy.reminderTime(task, now) ?: return
        ExactAlarms.setExactWake(context, at, pending(task.id, PendingIntent.FLAG_UPDATE_CURRENT)!!)
    }

    fun cancel(taskId: Long) {
        ExactAlarms.cancel(context, pending(taskId, PendingIntent.FLAG_NO_CREATE))
    }

    fun dismissNotification(taskId: Long) {
        NotificationManagerCompat.from(context).cancel(Notifications.taskId(taskId))
    }

    suspend fun rescheduleAll() {
        dao.getAll().forEach { schedule(it) }
    }

    private fun pending(taskId: Long, flag: Int): PendingIntent? {
        val intent = Intent(context, TaskReminderReceiver::class.java)
            .setAction(TaskReminderReceiver.ACTION_SHOW)
            .setData(Uri.parse("fiveam://task/$taskId"))
            .putExtra(TaskReminderReceiver.EXTRA_TASK_ID, taskId)
        return PendingIntent.getBroadcast(context, 0, intent, flag or PendingIntent.FLAG_IMMUTABLE)
    }
}
