package com.fiveam.club.sleep

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.fiveam.club.data.db.AlarmDao
import com.fiveam.club.data.prefs.SettingsRepository
import com.fiveam.club.util.ExactAlarms
import com.fiveam.club.util.TimeFormat
import java.time.ZoneId

/** Bedtime coach reminders (SLP-02), derived from the next scheduled alarm. Inexact on purpose (NFR-04). */
class BedtimeScheduler(
    private val context: Context,
    private val alarmDao: AlarmDao,
    private val settings: SettingsRepository,
) {
    suspend fun reschedule() {
        OFFSETS.forEach { cancel(it) }
        val s = settings.current()
        if (!s.bedtimeRemindersEnabled || !s.onboardingDone) return
        val next = alarmDao.getNext()?.nextTriggerAt ?: return
        val wake = TimeFormat.zoned(next).toLocalDateTime()
        val plan = SleepPlanner.plan(wake, s.sleepGoalMin, s.fallAsleepMin)
        val bedMs = plan.bedAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val now = System.currentTimeMillis()
        val enabled = mapOf(60 to s.remind60, 30 to s.remind30, 0 to s.remindAtBedtime)
        for (offset in OFFSETS) {
            val at = bedMs - offset * 60_000L
            if (enabled[offset] == true && at > now) {
                ExactAlarms.setWindowed(context, at, WINDOW_MS, pending(offset, next, PendingIntent.FLAG_UPDATE_CURRENT)!!)
            }
        }
    }

    private fun cancel(offset: Int) {
        ExactAlarms.cancel(context, pending(offset, 0L, PendingIntent.FLAG_NO_CREATE))
    }

    private fun pending(offset: Int, wakeAt: Long, flag: Int): PendingIntent? {
        val intent = Intent(context, BedtimeReceiver::class.java)
            .setAction(BedtimeReceiver.ACTION_REMIND)
            .setData(Uri.parse("fiveam://bedtime/$offset"))
            .putExtra(BedtimeReceiver.EXTRA_OFFSET, offset)
            .putExtra(BedtimeReceiver.EXTRA_WAKE_AT, wakeAt)
        return PendingIntent.getBroadcast(context, 0, intent, flag or PendingIntent.FLAG_IMMUTABLE)
    }

    companion object {
        val OFFSETS = listOf(60, 30, 0)
        private const val WINDOW_MS = 5 * 60_000L
    }
}
