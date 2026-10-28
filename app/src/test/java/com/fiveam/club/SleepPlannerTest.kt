package com.fiveam.club

import com.fiveam.club.sleep.SleepPlanner
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class SleepPlannerTest {
    private val wake = LocalDateTime.of(2026, 10, 29, 5, 0)

    @Test
    fun `AC-17 bedtime for 8h goal and 5am wake`() {
        val plan = SleepPlanner.plan(wake, 480, 15)
        assertEquals(LocalDateTime.of(2026, 10, 28, 21, 0), plan.sleepAt)
        assertEquals(LocalDateTime.of(2026, 10, 28, 20, 45), plan.bedAt)
    }

    @Test
    fun `late bedtime warns with shortfall`() {
        val bed = LocalDateTime.of(2026, 10, 29, 0, 0)
        assertEquals(285, SleepPlanner.projectedSleepMin(bed, wake, 15))
        assertEquals(195, SleepPlanner.shortfallMin(bed, wake, 480, 15))
    }

    @Test
    fun `alternative wake gives a full night`() {
        val bed = LocalDateTime.of(2026, 10, 29, 0, 0)
        assertEquals(LocalDateTime.of(2026, 10, 29, 8, 15), SleepPlanner.suggestedWake(bed, 480, 15))
    }

    @Test
    fun `duration never negative`() {
        assertEquals(0, SleepPlanner.durationMin(2_000, 1_000))
        assertEquals(420, SleepPlanner.durationMin(0, 420 * 60_000L))
    }
}
