package com.fiveam.club.wake

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.fiveam.club.R
import com.fiveam.club.ui.MainActivity
import com.fiveam.club.util.Notifications

/** Short follow-up prompts after QR (WAK-03..06). One notification id, replaced each attempt. */
class WakeNotifier(private val context: Context) {
    private val nm = NotificationManagerCompat.from(context)

    @SuppressLint("MissingPermission")
    fun showCheck(sessionId: Long, attempt: Int, max: Int) {
        if (!nm.areNotificationsEnabled()) return
        val open = PendingIntent.getActivity(
            context, 0, MainActivity.wakeIntent(context, sessionId, confirm = false),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val confirm = PendingIntent.getActivity(
            context, 1, MainActivity.wakeIntent(context, sessionId, confirm = true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = if (attempt == 1) {
            "دوس «أنا صاحي» علشان نسجل صحيانك ☀️"
        } else {
            "لسه مستنيين تأكيدك (تذكير $attempt من $max)"
        }
        val n = NotificationCompat.Builder(context, Notifications.CH_WAKE_CHECK)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle("صحيت ولا لسه؟")
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(open)
            .addAction(0, "أنا صاحي", confirm)
            .addAction(0, "محتاج دقيقة", broadcast(WakeCheckReceiver.ACTION_MORE_TIME, sessionId))
            .addAction(0, "رجعت نمت", broadcast(WakeCheckReceiver.ACTION_BACK_TO_SLEEP, sessionId))
            .build()
        nm.notify(Notifications.ID_WAKE_CHECK, n)
    }

    @SuppressLint("MissingPermission")
    fun showMissed() {
        if (!nm.areNotificationsEnabled()) return
        val open = PendingIntent.getActivity(
            context, 2, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, Notifications.CH_STATUS)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle("ماوصلناش تأكيد الصحيان")
            .setContentText("مفيش مشكلة. لو صحيت، افتح التطبيق وكمّل يومك.")
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        nm.notify(Notifications.ID_WAKE_INFO, n)
    }

    fun cancelCheck() = nm.cancel(Notifications.ID_WAKE_CHECK)

    private fun broadcast(action: String, sessionId: Long): PendingIntent {
        val intent = Intent(context, WakeCheckReceiver::class.java)
            .setAction(action)
            .setData(Uri.parse("fiveam://wake/$sessionId"))
            .putExtra(WakeCheckReceiver.EXTRA_SESSION_ID, sessionId)
        return PendingIntent.getBroadcast(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
