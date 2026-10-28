package com.fiveam.club.alarm.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.fiveam.club.alarm.audio.AlarmService

/** Entry point when AlarmManager fires. Hands off to the foreground service immediately. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        val service = Intent(context, AlarmService::class.java)
            .setAction(AlarmService.ACTION_FIRE)
            .putExtra(EXTRA_ALARM_ID, intent.getLongExtra(EXTRA_ALARM_ID, 0L))
            .putExtra(EXTRA_SCHEDULED_AT, intent.getLongExtra(EXTRA_SCHEDULED_AT, System.currentTimeMillis()))
        ContextCompat.startForegroundService(context, service)
    }

    companion object {
        const val ACTION_FIRE = "com.fiveam.club.action.ALARM_FIRE"
        const val EXTRA_ALARM_ID = "alarm_id"
        const val EXTRA_SCHEDULED_AT = "scheduled_at"
    }
}
