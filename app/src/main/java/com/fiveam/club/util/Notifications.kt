package com.fiveam.club.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.core.app.NotificationManagerCompat

object Notifications {
    const val CH_ALARM = "alarm_ringing"
    const val CH_WAKE_CHECK = "wake_check"
    const val CH_TASKS = "task_reminders"
    const val CH_BEDTIME = "bedtime"
    const val CH_STATUS = "status"

    const val ID_ALARM = 1001
    const val ID_WAKE_CHECK = 1002
    const val ID_WAKE_INFO = 1003
    private const val ID_BEDTIME_BASE = 2000
    private const val ID_TASK_BASE = 100_000

    fun taskId(taskId: Long): Int = ID_TASK_BASE + (taskId % 800_000).toInt()
    fun bedtimeId(offset: Int): Int = ID_BEDTIME_BASE + offset

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val alarm = NotificationChannel(CH_ALARM, "المنبه وهو بيرن", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "إشعار المنبه أثناء الرنين. الصوت نفسه بيشتغل من التطبيق."
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }
        val wake = NotificationChannel(CH_WAKE_CHECK, "تأكيد الصحيان", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "سؤال قصير بعد مسح QR علشان نتأكد إنك صاحي"
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            enableVibration(true)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }
        val tasks = NotificationChannel(CH_TASKS, "تذكيرات المهام", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "تذكير قبل مواعيد مهامك"
        }
        val bedtime = NotificationChannel(CH_BEDTIME, "تذكيرات النوم", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "تذكير قبل ميعاد النوم"
        }
        val status = NotificationChannel(CH_STATUS, "حالة التطبيق", NotificationManager.IMPORTANCE_LOW)
        nm.createNotificationChannels(listOf(alarm, wake, tasks, bedtime, status))
    }

    fun enabled(context: Context): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()
}
