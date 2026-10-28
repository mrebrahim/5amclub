package com.fiveam.club

import com.fiveam.club.gamification.DayRecord
import com.fiveam.club.gamification.DayStatus
import com.fiveam.club.gamification.DayStatus.MISSED
import com.fiveam.club.gamification.DayStatus.NONE
import com.fiveam.club.gamification.DayStatus.REST
import com.fiveam.club.gamification.DayStatus.SUCCESS
import com.fiveam.club.gamification.DayStatus.WOKE_SHORT_SLEEP
import com.fiveam.club.gamification.StreakCalculator
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class StreakTest {
    private fun days(vararg s: DayStatus): List<DayRecord> {
        val start = LocalDate.of(2026, 10, 1)
        return s.mapIndexed { i, st -> DayRecord(start.plusDays(i.toLong()), st) }
    }

    @Test
    fun `consecutive successes build the streak`() {
        val stats = StreakCalculator.compute(days(SUCCESS, SUCCESS, SUCCESS))
        assertEquals(3, stats.current)
        assertEquals(3, stats.best)
        assertEquals(7, stats.nextMilestone())
    }

    @Test
    fun `a miss resets current but keeps history`() {
        val stats = StreakCalculator.compute(days(SUCCESS, SUCCESS, SUCCESS, SUCCESS, MISSED, SUCCESS))
        assertEquals(1, stats.current)
        assertEquals(4, stats.best)
        assertEquals(5, stats.totalSuccessDays)
        assertEquals(listOf(3), stats.reachedMilestones())
    }

    @Test
    fun `rest days, days off and short sleep are neutral`() {
        val stats = StreakCalculator.compute(days(SUCCESS, REST, NONE, WOKE_SHORT_SLEEP, SUCCESS))
        assertEquals(2, stats.current)
    }

    @Test
    fun `short sleep never earns the healthy status`() {
        assertEquals(WOKE_SHORT_SLEEP, StreakCalculator.classify(true, false, true, 300, 480))
        assertEquals(SUCCESS, StreakCalculator.classify(true, false, true, 455, 480))
        assertEquals(DayStatus.WOKE_SLEEP_UNKNOWN, StreakCalculator.classify(true, false, true, null, 480))
        assertEquals(MISSED, StreakCalculator.classify(true, false, false, 480, 480))
        assertEquals(REST, StreakCalculator.classify(true, true, false, null, 480))
    }
}
