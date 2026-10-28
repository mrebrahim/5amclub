package com.fiveam.club.gamification

import java.time.LocalDate

enum class DayStatus {
    /** Woke on time with logged sufficient sleep. Counts toward the streak. */
    SUCCESS,
    /** Woke on time but logged short sleep: no healthy-wake reward, streak neither grows nor breaks. */
    WOKE_SHORT_SLEEP,
    /** Woke on time, sleep not logged yet: neutral. */
    WOKE_SLEEP_UNKNOWN,
    /** An alarm was scheduled and the wake-up wasn't confirmed on time. Breaks the streak. */
    MISSED,
    /** Planned rest day (GAM-07). Neutral. */
    REST,
    /** No alarm that day. Neutral. */
    NONE,
}

data class DayRecord(val date: LocalDate, val status: DayStatus)

data class StreakStats(val current: Int, val best: Int, val totalSuccessDays: Int) {
    fun nextMilestone(): Int? = XpRules.milestones.firstOrNull { it > current }
    fun reachedMilestones(): List<Int> = XpRules.milestones.filter { it <= best }
}

object StreakCalculator {
    /** [days] may be in any order; history is kept even after a break (GAM-05). */
    fun compute(days: List<DayRecord>): StreakStats {
        var current = 0
        var best = 0
        var total = 0
        for (d in days.sortedBy { it.date }) {
            when (d.status) {
                DayStatus.SUCCESS -> {
                    current++
                    total++
                    best = maxOf(best, current)
                }
                DayStatus.MISSED -> current = 0
                else -> Unit
            }
        }
        return StreakStats(current, best, total)
    }

    fun classify(
        hadAlarm: Boolean,
        isRestDay: Boolean,
        wokeOnTime: Boolean,
        sleepMinutes: Int?,
        sleepGoalMin: Int,
        toleranceMin: Int = 30,
    ): DayStatus = when {
        isRestDay -> DayStatus.REST
        !hadAlarm -> DayStatus.NONE
        !wokeOnTime -> DayStatus.MISSED
        sleepMinutes == null -> DayStatus.WOKE_SLEEP_UNKNOWN
        sleepMinutes >= sleepGoalMin - toleranceMin -> DayStatus.SUCCESS
        else -> DayStatus.WOKE_SHORT_SLEEP
    }
}
