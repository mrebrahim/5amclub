package com.fiveam.club.sleep

import java.time.Duration
import java.time.LocalDateTime

data class SleepPlan(
    val wakeAt: LocalDateTime,
    /** When the user should be asleep to hit the goal. */
    val sleepAt: LocalDateTime,
    /** When to get into bed, leaving time to fall asleep (SLP-04: bed != sleep). */
    val bedAt: LocalDateTime,
)

/** Pure arithmetic for the bedtime coach (SLP-01, SLP-05, SLP-06). Never edits alarms (SLP-07). */
object SleepPlanner {
    fun plan(wakeAt: LocalDateTime, sleepGoalMin: Int, fallAsleepMin: Int): SleepPlan {
        val sleepAt = wakeAt.minusMinutes(sleepGoalMin.toLong())
        return SleepPlan(wakeAt, sleepAt, sleepAt.minusMinutes(fallAsleepMin.toLong()))
    }

    /** Sleep the user would get if they get into bed at [bedAt]. */
    fun projectedSleepMin(bedAt: LocalDateTime, wakeAt: LocalDateTime, fallAsleepMin: Int): Int =
        Duration.between(bedAt.plusMinutes(fallAsleepMin.toLong()), wakeAt).toMinutes().toInt().coerceAtLeast(0)

    /** Minutes short of the goal, or 0 if the goal is met. */
    fun shortfallMin(bedAt: LocalDateTime, wakeAt: LocalDateTime, sleepGoalMin: Int, fallAsleepMin: Int): Int =
        (sleepGoalMin - projectedSleepMin(bedAt, wakeAt, fallAsleepMin)).coerceAtLeast(0)

    /** Wake time that would still give a full night after a late bedtime. Only a suggestion. */
    fun suggestedWake(bedAt: LocalDateTime, sleepGoalMin: Int, fallAsleepMin: Int): LocalDateTime =
        bedAt.plusMinutes((fallAsleepMin + sleepGoalMin).toLong())

    fun durationMin(sleepStartMs: Long, wakeMs: Long): Int =
        ((wakeMs - sleepStartMs) / 60_000).toInt().coerceAtLeast(0)
}
