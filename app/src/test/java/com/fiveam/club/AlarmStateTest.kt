package com.fiveam.club

import com.fiveam.club.alarm.core.AlarmState
import com.fiveam.club.alarm.core.AlarmState.AWAITING_CONFIRMATION
import com.fiveam.club.alarm.core.AlarmState.CONFIRMED
import com.fiveam.club.alarm.core.AlarmState.EMERGENCY_DISMISSED
import com.fiveam.club.alarm.core.AlarmState.FIRING
import com.fiveam.club.alarm.core.AlarmState.QR_COMPLETED
import com.fiveam.club.alarm.core.AlarmState.QR_FAILED
import com.fiveam.club.alarm.core.AlarmState.QR_SCANNING
import com.fiveam.club.alarm.core.AlarmState.ROUTINE_STARTED
import com.fiveam.club.alarm.core.AlarmState.SCHEDULED
import com.fiveam.club.alarm.core.AlarmState.VERIFICATION_MISSED
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmStateTest {
    @Test
    fun `happy path from PRD 4_3 is valid`() {
        val path = listOf(SCHEDULED, FIRING, QR_SCANNING, QR_COMPLETED, AWAITING_CONFIRMATION, CONFIRMED, ROUTINE_STARTED)
        path.zipWithNext().forEach { (a, b) -> assertTrue("$a -> $b", a.canTransitionTo(b)) }
    }

    @Test
    fun `closing the scanner keeps the alarm ringing`() {
        assertTrue(QR_SCANNING.canTransitionTo(FIRING))
        assertTrue(FIRING.isRinging)
    }

    @Test
    fun `cannot confirm wake without completing the primary dismissal`() {
        assertFalse(FIRING.canTransitionTo(CONFIRMED))
        assertFalse(QR_SCANNING.canTransitionTo(CONFIRMED))
        assertFalse(QR_FAILED.canTransitionTo(AWAITING_CONFIRMATION))
    }

    @Test
    fun `terminal states cannot ring again`() {
        AlarmState.TERMINAL.forEach { t ->
            AlarmState.RINGING.forEach { r -> assertFalse("$t -> $r", t.canTransitionTo(r)) }
        }
        assertFalse(EMERGENCY_DISMISSED.canTransitionTo(CONFIRMED))
    }

    @Test
    fun `late confirmation after missed verification is allowed`() {
        assertTrue(VERIFICATION_MISSED.canTransitionTo(CONFIRMED))
    }

    @Test
    fun `unknown persisted names fall back to cancelled`() {
        assertEquals(AlarmState.CANCELLED, AlarmState.of("SOMETHING_OLD"))
    }
}
