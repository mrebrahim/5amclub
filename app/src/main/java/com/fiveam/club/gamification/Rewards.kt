package com.fiveam.club.gamification

import com.fiveam.club.data.db.RewardDao
import com.fiveam.club.data.db.RewardEventEntity
import com.fiveam.club.util.TimeFormat
import java.time.LocalDate

/** Central XP table (PRD §9.2). Tune values here only. */
enum class RewardType(val points: Int, val label: String) {
    QR_COMPLETED(10, "إكمال QR"),
    WAKE_CONFIRMED(10, "تأكيد الصحيان"),
    HEALTHY_WAKE(20, "صحيان في الميعاد مع نوم كافي"),
    ROUTINE_COMPLETED(20, "إكمال الروتين الصباحي"),
    GOALS_WRITTEN(5, "كتابة أهداف بكرة"),
    TASK_DONE(5, "إنجاز مهمة"),
}

class RewardEngine(private val dao: RewardDao) {
    /**
     * Grants once per (type, sourceEventId) thanks to a unique index (AC-19).
     * Returns true only when new points were added.
     */
    suspend fun grant(type: RewardType, sourceEventId: String, now: Long = System.currentTimeMillis()): Boolean {
        if (type == RewardType.TASK_DONE) {
            val today = TimeFormat.localDate(now)
            val from = TimeFormat.startOfDay(today)
            val to = TimeFormat.startOfDay(today.plusDays(1)) - 1
            if (dao.countBetween(type.name, from, to) >= XpRules.TASK_DAILY_CAP) return false
        }
        val row = RewardEventEntity(type = type.name, points = type.points, sourceEventId = sourceEventId, createdAt = now)
        return dao.insert(row) != -1L
    }

    suspend fun pointsBetween(from: LocalDate, toInclusive: LocalDate): Int =
        dao.between(TimeFormat.startOfDay(from), TimeFormat.startOfDay(toInclusive.plusDays(1)) - 1).sumOf { it.points }
}
