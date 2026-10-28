package com.fiveam.club.gamification

import com.fiveam.club.alarm.core.AlarmState
import com.fiveam.club.data.db.AlarmSessionEntity
import com.fiveam.club.data.db.AppDatabase
import com.fiveam.club.data.db.SleepEntryEntity
import com.fiveam.club.data.prefs.SettingsRepository
import com.fiveam.club.util.TimeFormat
import java.time.LocalDate

data class WeeklyReport(
    val from: LocalDate,
    val to: LocalDate,
    val days: List<DayRecord>,
    val scheduledDays: Int,
    val onTimeDays: Int,
    val avgDeviationMin: Int?,
    val avgSleepMin: Int?,
    val sleepLoggedDays: Int,
    val qrRate: Float?,
    val confirmRate: Float?,
    val routineRate: Float?,
    val tasksCompleted: Int,
    val xp: Int,
    val streak: StreakStats,
    val emergencyCount: Int,
    val volumeKeyPresses: Int,
    val lateSessions: List<AlarmSessionEntity>,
)

class ProgressRepository(
    private val db: AppDatabase,
    private val settings: SettingsRepository,
    private val rewards: RewardEngine,
) {
    fun isRealSession(s: AlarmSessionEntity) = !s.isTest && s.fallbackReason?.startsWith("overlap") != true

    fun wokeOnTime(s: AlarmSessionEntity, windowMin: Int): Boolean {
        val completed = s.qrCompletedAt ?: return false
        return s.confirmedAt != null && s.dismissMethod != "EMERGENCY" &&
            completed - s.scheduledAt <= windowMin * 60_000L
    }

    suspend fun dayRecords(from: LocalDate, to: LocalDate): List<DayRecord> {
        val s = settings.current()
        val sessions = db.sessionDao()
            .between(TimeFormat.startOfDay(from), TimeFormat.startOfDay(to.plusDays(1)) - 1)
            .filter(::isRealSession)
            .groupBy { TimeFormat.localDate(it.scheduledAt) }
        val sleep = db.sleepDao().between(from.toString(), to.toString()).associateBy { it.date }
        val rest = db.restDayDao().all().map { it.date }.toSet()
        return generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.map { date ->
            val daySessions = sessions[date].orEmpty()
            val stillActive = daySessions.any { AlarmState.of(it.state) in AlarmState.ACTIVE }
            val onTime = daySessions.any { wokeOnTime(it, s.wakeWindowMin) }
            val status = if (stillActive && !onTime) {
                DayStatus.NONE
            } else {
                StreakCalculator.classify(
                    hadAlarm = daySessions.isNotEmpty(),
                    isRestDay = date.toString() in rest,
                    wokeOnTime = onTime,
                    sleepMinutes = sleep[date.toString()]?.estimatedDurationMin,
                    sleepGoalMin = s.sleepGoalMin,
                )
            }
            DayRecord(date, status)
        }.toList()
    }

    suspend fun streak(): StreakStats {
        val first = db.sessionDao().allReal().firstOrNull() ?: return StreakStats(0, 0, 0)
        return StreakCalculator.compute(dayRecords(TimeFormat.localDate(first.scheduledAt), LocalDate.now()))
    }

    /** Grants the healthy-wake reward once the day qualifies (after confirm or sleep log). */
    suspend fun evaluateDay(anyTimeThatDay: Long) = evaluateDate(TimeFormat.localDate(anyTimeThatDay))

    suspend fun evaluateDate(date: LocalDate) {
        val record = dayRecords(date, date).single()
        if (record.status == DayStatus.SUCCESS) rewards.grant(RewardType.HEALTHY_WAKE, "day:$date")
    }

    suspend fun weeklyReport(to: LocalDate = LocalDate.now()): WeeklyReport {
        val from = to.minusDays(6)
        val s = settings.current()
        val fromMs = TimeFormat.startOfDay(from)
        val toMs = TimeFormat.startOfDay(to.plusDays(1)) - 1
        val sessions = db.sessionDao().between(fromMs, toMs).filter(::isRealSession)
            .filter { AlarmState.of(it.state) !in AlarmState.ACTIVE }
        val days = dayRecords(from, to)
        val sleep: List<SleepEntryEntity> = db.sleepDao().between(from.toString(), to.toString())
        val sleepMinutes = sleep.mapNotNull { it.estimatedDurationMin }
        val runs = db.routineDao().runsBetween(from.toString(), to.toString())
        val deviations = sessions.mapNotNull { it.qrCompletedAt?.let { c -> ((c - it.scheduledAt) / 60_000).toInt() } }
        val fired = sessions.filter { it.firedAt != null }
        return WeeklyReport(
            from = from,
            to = to,
            days = days,
            scheduledDays = days.count { it.status !in setOf(DayStatus.NONE, DayStatus.REST) },
            onTimeDays = days.count { it.status in setOf(DayStatus.SUCCESS, DayStatus.WOKE_SHORT_SLEEP, DayStatus.WOKE_SLEEP_UNKNOWN) },
            avgDeviationMin = deviations.takeIf { it.isNotEmpty() }?.average()?.toInt(),
            avgSleepMin = sleepMinutes.takeIf { it.isNotEmpty() }?.average()?.toInt(),
            sleepLoggedDays = sleepMinutes.size,
            qrRate = fired.takeIf { it.isNotEmpty() }?.let { f -> f.count { it.dismissMethod == "QR" }.toFloat() / f.size },
            confirmRate = fired.takeIf { it.isNotEmpty() }?.let { f -> f.count { it.confirmedAt != null }.toFloat() / f.size },
            routineRate = runs.takeIf { it.isNotEmpty() }?.let { r ->
                r.map { if (it.totalSteps == 0) 0f else it.completedSteps.toFloat() / it.totalSteps }.average().toFloat()
            },
            tasksCompleted = db.taskDao().completionsBetween(fromMs, toMs).size,
            xp = rewards.pointsBetween(from, to),
            streak = streak(),
            emergencyCount = sessions.count { it.dismissMethod == "EMERGENCY" },
            volumeKeyPresses = sessions.sumOf { it.volumeKeyPresses },
            lateSessions = sessions.filter { !wokeOnTime(it, s.wakeWindowMin) },
        )
    }
}
