package com.fiveam.club.alarm.core

import com.fiveam.club.data.db.AlarmEntity
import com.fiveam.club.sleep.BedtimeScheduler

/** Alarm mutations that keep dependent schedules (bedtime coach) in sync. */
class AlarmRepository(
    private val scheduler: AlarmScheduler,
    private val bedtime: BedtimeScheduler,
) {
    suspend fun save(alarm: AlarmEntity): Long = scheduler.save(alarm).also { bedtime.reschedule() }

    suspend fun setEnabled(id: Long, enabled: Boolean) {
        scheduler.setEnabled(id, enabled)
        bedtime.reschedule()
    }

    suspend fun delete(alarm: AlarmEntity) {
        scheduler.delete(alarm)
        bedtime.reschedule()
    }

    suspend fun onFired(id: Long) {
        scheduler.onFired(id)
        bedtime.reschedule()
    }

    suspend fun rescheduleAll() {
        scheduler.rescheduleAll()
        bedtime.reschedule()
    }
}
