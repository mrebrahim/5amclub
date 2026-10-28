package com.fiveam.club.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build

/** Wrappers that degrade gracefully when exact-alarm access is unavailable. */
object ExactAlarms {
    fun canScheduleExact(context: Context): Boolean {
        val am = context.getSystemService(AlarmManager::class.java)
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
    }

    /** For short, time-sensitive follow-ups (wake checks, task reminders). */
    fun setExactWake(context: Context, at: Long, pi: PendingIntent) {
        val am = context.getSystemService(AlarmManager::class.java)
        try {
            if (canScheduleExact(context)) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    /** For reminders where a few minutes of drift is fine (bedtime coach). Saves battery. */
    fun setWindowed(context: Context, at: Long, windowMs: Long, pi: PendingIntent) {
        val am = context.getSystemService(AlarmManager::class.java)
        am.setWindow(AlarmManager.RTC_WAKEUP, at, windowMs, pi)
    }

    fun cancel(context: Context, pi: PendingIntent?) {
        if (pi == null) return
        context.getSystemService(AlarmManager::class.java).cancel(pi)
        pi.cancel()
    }
}
