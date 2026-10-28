package com.fiveam.club.alarm.core

/** PRD §4.3 alarm session state machine. */
enum class AlarmState {
    SCHEDULED,
    FIRING,
    QR_SCANNING,
    QR_FAILED,
    QR_COMPLETED,
    AWAITING_CONFIRMATION,
    CONFIRMED,
    ROUTINE_STARTED,
    EMERGENCY_DISMISSED,
    VERIFICATION_MISSED,
    CANCELLED;

    val isRinging: Boolean get() = this in RINGING
    val isTerminal: Boolean get() = this in TERMINAL

    fun canTransitionTo(next: AlarmState): Boolean = next in (TRANSITIONS[this] ?: emptySet())

    companion object {
        val RINGING = setOf(FIRING, QR_SCANNING, QR_FAILED)
        val TERMINAL = setOf(ROUTINE_STARTED, EMERGENCY_DISMISSED, CANCELLED)
        /** Sessions that still need the app's attention (ringing or waiting for confirmation). */
        val ACTIVE = RINGING + setOf(QR_COMPLETED, AWAITING_CONFIRMATION)

        private val TRANSITIONS: Map<AlarmState, Set<AlarmState>> = mapOf(
            SCHEDULED to setOf(FIRING, CANCELLED),
            FIRING to setOf(QR_SCANNING, QR_FAILED, QR_COMPLETED, EMERGENCY_DISMISSED, CANCELLED),
            QR_SCANNING to setOf(FIRING, QR_COMPLETED, QR_FAILED, EMERGENCY_DISMISSED, CANCELLED),
            // QR_FAILED keeps ringing; completing the fallback challenge ends the primary phase.
            QR_FAILED to setOf(QR_SCANNING, FIRING, QR_COMPLETED, EMERGENCY_DISMISSED, CANCELLED),
            QR_COMPLETED to setOf(AWAITING_CONFIRMATION, CONFIRMED, CANCELLED),
            AWAITING_CONFIRMATION to setOf(CONFIRMED, VERIFICATION_MISSED, CANCELLED),
            CONFIRMED to setOf(ROUTINE_STARTED),
            // A late "I'm awake" is still recorded; the report shows it as late.
            VERIFICATION_MISSED to setOf(CONFIRMED),
        )

        fun of(name: String): AlarmState = runCatching { valueOf(name) }.getOrDefault(CANCELLED)
    }
}
