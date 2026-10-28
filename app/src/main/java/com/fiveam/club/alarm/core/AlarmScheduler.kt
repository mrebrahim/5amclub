package com.fiveam.club.alarm.core

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.fiveam.club.data.db.AlarmDao
import com.fiveam.club.data.db.AlarmEntity
import com.fiveam.club.diagnostics.DiagLog
import com.fiveam.club.ui.MainActivity
import com.fiveam.club.util.ExactAlarms
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.ZonedDateTime

/**
 * Registers wake-up alarms with [AlarmManager.setAlarmClock] (ALM-04). All reads and writes of an
 * alarm's schedule happen under one lock so a firing alarm and a reschedule can't race.
 */
class AlarmScheduler(
    private val context: Context,
    private val dao: AlarmDao,
    private val diag: DiagLog,
) {
    private val am = context.getSystemService(AlarmManager::class.java)
    private val lock = Mutex()

    fun canScheduleExact(): Boolean = ExactAlarms.canScheduleExact(context)

    suspend fun save(alarm: AlarmEntity): Long = lock.withLock {
        val rowId = dao.upsert(alarm)
        val id = if (alarm.id == 0L) rowId else alarm.id
        scheduleLocked(id)
        id
    }

    suspend fun setEnabled(id: Long, enabled: Boolean) = lock.withLock {
        dao.setEnabled(id, enabled)
        scheduleLocked(id)
    }

    suspend fun delete(alarm: AlarmEntity) = lock.withLock {
        cancelLocked(alarm.id)
        dao.delete(alarm)
        diag.log(TAG, "deleted alarm=${alarm.id}")
    }

    /** Called when an alarm has just rung: one-time alarms are disabled, repeating ones move on. */
    suspend fun onFired(id: Long) = lock.withLock {
        val alarm = dao.get(id) ?: return@withLock
        if (alarm.repeatDays == 0) {
            dao.setEnabled(id, false)
            dao.setNextTrigger(id, null)
        } else {
            scheduleLocked(id)
        }
    }

    /** After boot, time or timezone change, app update, or permission change (ALM-03). */
    suspend fun rescheduleAll() = lock.withLock {
        val now = System.currentTimeMillis()
        for (alarm in dao.getAll()) {
            val missedOneShot = alarm.enabled && alarm.repeatDays == 0 &&
                alarm.nextTriggerAt != null && alarm.nextTriggerAt <= now
            if (missedOneShot) {
                dao.setEnabled(alarm.id, false)
                dao.setNextTrigger(alarm.id, null)
                diag.log(TAG, "one-time alarm=${alarm.id} was missed (device off or killed)")
            } else {
                scheduleLocked(alarm.id)
            }
        }
        diag.log(TAG, "rescheduled all, exact=${canScheduleExact()}")
    }

    fun scheduleTest(at: Long) {
        setClock(at, fireIntent(TEST_ALARM_ID, at))
    }

    private suspend fun scheduleLocked(id: Long): Long? {
        val alarm = dao.get(id) ?: return null
        cancelLocked(id)
        if (!alarm.enabled) {
            dao.setNextTrigger(id, null)
            return null
        }
        val next = NextTrigger.compute(alarm.hour, alarm.minute, alarm.repeatDays, ZonedDateTime.now())
            .toInstant().toEpochMilli()
        setClock(next, fireIntent(id, next))
        dao.setNextTrigger(id, next)
        diag.log(TAG, "scheduled alarm=$id at=$next exact=${canScheduleExact()}")
        return next
    }

    private fun cancelLocked(id: Long) {
        val pi = PendingIntent.getBroadcast(
            context, 0, baseIntent(id), PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        ExactAlarms.cancel(context, pi)
    }

    private fun setClock(at: Long, pi: PendingIntent) {
        val show = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        try {
            if (canScheduleExact()) {
                am.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), pi)
            } else {
                // Degraded mode: the readiness screen warns that timing isn't guaranteed.
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    private fun baseIntent(id: Long) = Intent(context, AlarmReceiver::class.java)
        .setAction(AlarmReceiver.ACTION_FIRE)
        .setData(Uri.parse("fiveam://alarm/$id"))

    private fun fireIntent(id: Long, at: Long): PendingIntent {
        val intent = baseIntent(id)
            .putExtra(AlarmReceiver.EXTRA_ALARM_ID, id)
            .putExtra(AlarmReceiver.EXTRA_SCHEDULED_AT, at)
        return PendingIntent.getBroadcast(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val TEST_ALARM_ID = -1L
        private const val TAG = "scheduler"
    }
}
