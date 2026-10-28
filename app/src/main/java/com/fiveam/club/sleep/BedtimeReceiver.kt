package com.fiveam.club.sleep

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.fiveam.club.FiveAmApp
import com.fiveam.club.R
import com.fiveam.club.ui.MainActivity
import com.fiveam.club.util.Notifications
import com.fiveam.club.util.TimeFormat
import kotlinx.coroutines.launch
import java.time.LocalDate

class BedtimeReceiver : BroadcastReceiver() {
    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REMIND) return
        val offset = intent.getIntExtra(EXTRA_OFFSET, 0)
        val wakeAt = intent.getLongExtra(EXTRA_WAKE_AT, 0L)
        val container = (context.applicationContext as FiveAmApp).container
        val pending = goAsync()
        container.appScope.launch {
            try {
                val goals = container.db.goalDao().forDate(LocalDate.now().plusDays(1).toString()).size
                val (title, text) = when (offset) {
                    60 -> "ابدأ تهدّي يومك 🌙" to "فاضل ساعة على ميعاد السرير. قلّل الإضاءة والشاشات."
                    30 -> "جهّز حاجات بكرة" to if (goals == 0) {
                        "اكتب هدف واحد على الأقل: إنت هتصحى بكرة ليه؟"
                    } else {
                        "عندك $goals ${if (goals == 1) "هدف" else "أهداف"} لبكرة. جهّز اللي محتاجه."
                    }
                    else -> "حان وقت النوم 😴" to "منبهك ${TimeFormat.time(wakeAt)}. تصبح على خير."
                }
                val nm = NotificationManagerCompat.from(context)
                if (nm.areNotificationsEnabled()) {
                    val open = PendingIntent.getActivity(
                        context, 3, MainActivity.routeIntent(context, "bedtime"),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    )
                    nm.notify(
                        Notifications.bedtimeId(offset),
                        NotificationCompat.Builder(context, Notifications.CH_BEDTIME)
                            .setSmallIcon(R.drawable.ic_stat_alarm)
                            .setContentTitle(title)
                            .setContentText(text)
                            .setAutoCancel(true)
                            .setContentIntent(open)
                            .build(),
                    )
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_REMIND = "com.fiveam.club.action.BEDTIME"
        const val EXTRA_OFFSET = "offset"
        const val EXTRA_WAKE_AT = "wake_at"
    }
}
