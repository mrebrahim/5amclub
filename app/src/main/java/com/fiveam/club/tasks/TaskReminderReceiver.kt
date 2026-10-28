package com.fiveam.club.tasks

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.fiveam.club.FiveAmApp
import com.fiveam.club.R
import com.fiveam.club.data.db.TaskStatus
import com.fiveam.club.ui.MainActivity
import com.fiveam.club.util.Notifications
import com.fiveam.club.util.TimeFormat
import kotlinx.coroutines.launch

class TaskReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_TASK_ID, -1L)
        if (id < 0) return
        val container = (context.applicationContext as FiveAmApp).container
        val pending = goAsync()
        container.appScope.launch {
            try {
                when (intent.action) {
                    ACTION_SHOW -> show(context, id)
                    ACTION_DONE -> container.tasks.markDone(id)
                    ACTION_SNOOZE -> container.tasks.snooze(id, 10)
                }
            } finally {
                pending.finish()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun show(context: Context, id: Long) {
        val container = (context.applicationContext as FiveAmApp).container
        val task = container.tasks.get(id) ?: return
        if (task.status == TaskStatus.DONE.name) return
        // Clear the consumed snooze so the next reschedule uses the regular offset.
        if (task.snoozedUntil != null) container.db.taskDao().upsert(task.copy(snoozedUntil = null))
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) {
            container.diag.log("tasks", "reminder for task=$id skipped: notifications disabled")
            return
        }
        val open = PendingIntent.getActivity(
            context, 4, MainActivity.routeIntent(context, "task/$id").setData(Uri.parse("fiveam://open-task/$id")),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, Notifications.CH_TASKS)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(task.title)
            .setContentText("الساعة ${TimeFormat.time(task.dueAt)} · ${TimeFormat.duration(task.durationMin)}")
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(open)
            .addAction(0, "تم ✓", action(context, ACTION_DONE, id))
            .addAction(0, "أجّل 10 دقايق", action(context, ACTION_SNOOZE, id))
            .addAction(0, "افتح المهمة", open)
            .build()
        nm.notify(Notifications.taskId(id), n)
    }

    private fun action(context: Context, action: String, id: Long): PendingIntent {
        val intent = Intent(context, TaskReminderReceiver::class.java)
            .setAction(action)
            .setData(Uri.parse("fiveam://task/$id"))
            .putExtra(EXTRA_TASK_ID, id)
        return PendingIntent.getBroadcast(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val ACTION_SHOW = "com.fiveam.club.action.TASK_SHOW"
        const val ACTION_DONE = "com.fiveam.club.action.TASK_DONE"
        const val ACTION_SNOOZE = "com.fiveam.club.action.TASK_SNOOZE"
        const val EXTRA_TASK_ID = "task_id"
    }
}
