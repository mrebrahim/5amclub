package com.fiveam.club.goals

import com.fiveam.club.data.db.GoalDao
import com.fiveam.club.data.db.GoalEntity
import com.fiveam.club.gamification.RewardEngine
import com.fiveam.club.gamification.RewardType
import java.time.LocalDate

class GoalRepository(private val dao: GoalDao, private val rewards: RewardEngine) {
    fun forDate(date: LocalDate) = dao.observeForDate(date.toString())

    suspend fun forDateNow(date: LocalDate) = dao.forDate(date.toString())

    /** MOT-02: 1-3 main goals per day. Returns false when the day is full. */
    suspend fun save(goal: GoalEntity): Boolean {
        if (goal.id == 0L && dao.forDate(goal.date).size >= MAX_PER_DAY) return false
        dao.upsert(goal)
        if (LocalDate.parse(goal.date).isAfter(LocalDate.now())) {
            rewards.grant(RewardType.GOALS_WRITTEN, "goals:${goal.date}")
        }
        return true
    }

    suspend fun toggle(goal: GoalEntity) = dao.upsert(goal.copy(isCompleted = !goal.isCompleted))

    suspend fun delete(goal: GoalEntity) = dao.delete(goal)

    /** MOT-05: carry an unfinished goal to the next day. */
    suspend fun carryOver(goal: GoalEntity): Boolean {
        val next = LocalDate.parse(goal.date).plusDays(1).coerceAtLeast(LocalDate.now()).toString()
        if (dao.forDate(next).size >= MAX_PER_DAY) return false
        dao.delete(goal)
        dao.upsert(goal.copy(id = 0, date = next, isCompleted = false))
        return true
    }

    companion object {
        const val MAX_PER_DAY = 3
    }
}

private fun LocalDate.coerceAtLeast(other: LocalDate): LocalDate = if (isBefore(other)) other else this
