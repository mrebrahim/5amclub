package com.fiveam.club.alarm.core

import android.content.Context
import android.content.Intent
import com.fiveam.club.alarm.audio.AlarmService
import com.fiveam.club.alarm.audio.RingConfig
import com.fiveam.club.data.db.AppDatabase
import com.fiveam.club.data.db.AlarmSessionEntity
import com.fiveam.club.data.db.DismissMode
import com.fiveam.club.diagnostics.DiagLog
import com.fiveam.club.gamification.ProgressRepository
import com.fiveam.club.gamification.RewardEngine
import com.fiveam.club.gamification.RewardType
import com.fiveam.club.qr.QrValidator
import com.fiveam.club.wake.WakeCheckScheduler
import com.fiveam.club.wake.WakeNotifier
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class RingingSession(val session: AlarmSessionEntity, val config: RingConfig)

enum class ScanResult { ACCEPTED, REJECTED, IGNORED }

/**
 * Single owner of alarm session state (PRD §4). Every transition is validated against
 * [AlarmState] and persisted before side effects run, so a process death never leaves two
 * conflicting sessions for the same alarm.
 */
class AlarmSessionManager(
    private val context: Context,
    private val db: AppDatabase,
    private val alarms: AlarmRepository,
    private val wakeChecks: WakeCheckScheduler,
    private val wakeNotifier: WakeNotifier,
    private val rewards: RewardEngine,
    private val progress: ProgressRepository,
    private val diag: DiagLog,
) {
    private val dao = db.sessionDao()
    private val mutex = Mutex()

    suspend fun onAlarmFired(alarmId: Long, scheduledAt: Long): RingingSession? = mutex.withLock {
        val now = System.currentTimeMillis()
        val isTest = alarmId == AlarmScheduler.TEST_ALARM_ID
        val alarm = if (isTest) null else db.alarmDao().get(alarmId)
        if (!isTest) alarms.onFired(alarmId)
        if (!isTest && alarm == null) {
            diag.log(TAG, "fire for missing alarm=$alarmId ignored")
            return@withLock null
        }
        val config = alarm?.let(RingConfig::from) ?: RingConfig.test()

        val ringing = dao.findInStates(AlarmState.RINGING.map { it.name })
        if (ringing != null) {
            // Overlap policy: the alarm already ringing keeps priority; the new one is logged only.
            dao.insert(
                AlarmSessionEntity(
                    alarmId = alarmId, isTest = isTest, label = config.label, scheduledAt = scheduledAt,
                    firedAt = now, state = AlarmState.CANCELLED.name, endState = AlarmState.CANCELLED.name,
                    fallbackReason = "overlap:${ringing.id}",
                ),
            )
            diag.log(TAG, "alarm=$alarmId overlapped session=${ringing.id}")
            return@withLock null
        }

        val session = AlarmSessionEntity(
            alarmId = alarmId,
            isTest = isTest,
            label = config.label,
            scheduledAt = scheduledAt,
            firedAt = now,
            state = AlarmState.FIRING.name,
            verificationDelayMin = if (isTest) 0 else alarm!!.verificationDelayMin,
            maxFollowUps = alarm?.maxFollowUps?.coerceAtLeast(1) ?: 0,
        )
        val id = dao.insert(session)
        diag.log(TAG, "fired session=$id alarm=$alarmId test=$isTest lateMs=${now - scheduledAt}")
        RingingSession(session.copy(id = id), config)
    }

    /** Ringing session to resume after the service was recreated, if still recent. */
    suspend fun recoverRinging(): RingingSession? = mutex.withLock {
        val s = dao.findInStates(AlarmState.RINGING.map { it.name }) ?: return@withLock null
        val age = System.currentTimeMillis() - (s.firedAt ?: s.scheduledAt)
        if (age > AlarmService.MAX_RING_MS) {
            transitionLocked(s.id, AlarmState.CANCELLED) { it.copy(fallbackReason = "stale_after_restart") }
            return@withLock null
        }
        diag.log(TAG, "recovering session=${s.id}")
        RingingSession(s, configFor(s))
    }

    suspend fun startScan(id: Long) = mutex.withLock {
        transitionLocked(id, AlarmState.QR_SCANNING) { s ->
            s.copy(qrStartedAt = s.qrStartedAt ?: System.currentTimeMillis())
        }
    }

    /** Scanner closed before a valid code: the alarm stays incomplete (QR-08). */
    suspend fun cancelScan(id: Long) = mutex.withLock {
        transitionLocked(id, AlarmState.FIRING)
    }

    /** Camera denied, broken or unable to read: switch to the fallback challenge (QR-09). */
    suspend fun scanFailed(id: Long, reason: String) = mutex.withLock {
        transitionLocked(id, AlarmState.QR_FAILED) { it.copy(fallbackReason = reason) }
        diag.log(TAG, "session=$id fallback reason=$reason")
    }

    suspend fun onQrScanned(id: Long, payload: String?): ScanResult = mutex.withLock {
        val s = dao.get(id) ?: return@withLock ScanResult.IGNORED
        if (AlarmState.of(s.state) != AlarmState.QR_SCANNING) return@withLock ScanResult.IGNORED
        val alarm = if (s.isTest) null else db.alarmDao().get(s.alarmId)
        val mode = alarm?.dismissMode?.let { runCatching { DismissMode.valueOf(it) }.getOrNull() } ?: DismissMode.QR_ANY
        if (!QrValidator.isAcceptable(mode, payload, alarm?.qrReferenceHash)) {
            return@withLock ScanResult.REJECTED
        }
        completePrimaryLocked(s, method = "QR")
        ScanResult.ACCEPTED
    }

    suspend fun completeFallback(id: Long) = mutex.withLock {
        val s = dao.get(id) ?: return@withLock
        completePrimaryLocked(s, method = "MATH")
    }

    suspend fun emergencyDismiss(id: Long, reason: String) = mutex.withLock {
        val s = transitionLocked(id, AlarmState.EMERGENCY_DISMISSED) {
            it.copy(dismissMethod = "EMERGENCY", emergencyReason = reason)
        } ?: return@withLock
        stopRinging()
        diag.log(TAG, "session=${s.id} emergency dismissed")
    }

    suspend fun onWakeCheckDue(id: Long) = mutex.withLock {
        val s = dao.get(id) ?: return@withLock
        if (AlarmState.of(s.state) != AlarmState.AWAITING_CONFIRMATION) return@withLock
        if (s.followUpsSent >= s.maxFollowUps) {
            transitionLocked(id, AlarmState.VERIFICATION_MISSED) { it.copy(fallbackReason = "no_response") }
            wakeNotifier.cancelCheck()
            wakeNotifier.showMissed()
            diag.log(TAG, "session=$id verification missed after ${s.followUpsSent} follow-ups")
            return@withLock
        }
        val attempt = s.followUpsSent + 1
        dao.update(s.copy(followUpsSent = attempt, updatedAt = System.currentTimeMillis()))
        wakeNotifier.showCheck(id, attempt, s.maxFollowUps)
        wakeChecks.schedule(id, System.currentTimeMillis() + FOLLOW_UP_GRACE_MS)
    }

    suspend fun needMoreTime(id: Long) = mutex.withLock {
        val s = dao.get(id) ?: return@withLock
        if (AlarmState.of(s.state) != AlarmState.AWAITING_CONFIRMATION) return@withLock
        wakeNotifier.cancelCheck()
        wakeChecks.schedule(id, System.currentTimeMillis() + MORE_TIME_MS)
    }

    suspend fun confirmWake(id: Long): Boolean = mutex.withLock {
        val before = dao.get(id) ?: return@withLock false
        val wasAwaiting = AlarmState.of(before.state) in setOf(AlarmState.AWAITING_CONFIRMATION, AlarmState.QR_COMPLETED)
        val s = transitionLocked(id, AlarmState.CONFIRMED) {
            it.copy(confirmedAt = System.currentTimeMillis())
        } ?: return@withLock false
        wakeChecks.cancel(id)
        wakeNotifier.cancelCheck()
        if (!s.isTest && wasAwaiting) {
            rewards.grant(RewardType.WAKE_CONFIRMED, "session:$id")
            progress.evaluateDay(s.scheduledAt)
        }
        true
    }

    suspend fun reportBackToSleep(id: Long) = mutex.withLock {
        transitionLocked(id, AlarmState.VERIFICATION_MISSED) { it.copy(fallbackReason = "user_reported") }
        wakeChecks.cancel(id)
        wakeNotifier.cancelCheck()
    }

    suspend fun markRoutineStarted(id: Long) = mutex.withLock {
        transitionLocked(id, AlarmState.ROUTINE_STARTED) {
            it.copy(routineStartedAt = System.currentTimeMillis())
        }
    }

    suspend fun onRingTimeout(id: Long) = mutex.withLock {
        transitionLocked(id, AlarmState.CANCELLED) { it.copy(fallbackReason = "ring_timeout") }
    }

    suspend fun recordVolumeKey(id: Long) = mutex.withLock {
        val s = dao.get(id) ?: return@withLock
        dao.update(s.copy(volumeKeyPresses = s.volumeKeyPresses + 1))
    }

    suspend fun recordVolumeRestore(id: Long) = mutex.withLock {
        val s = dao.get(id) ?: return@withLock
        dao.update(s.copy(volumeRestores = s.volumeRestores + 1))
    }

    suspend fun setDelayReason(id: Long, reason: String?) = mutex.withLock {
        val s = dao.get(id) ?: return@withLock
        dao.update(s.copy(delayReason = reason))
    }

    /** Re-arms wake checks after reboot for sessions still waiting on confirmation. */
    suspend fun restorePendingChecks() = mutex.withLock {
        val now = System.currentTimeMillis()
        for (s in dao.allInStates(listOf(AlarmState.AWAITING_CONFIRMATION.name, AlarmState.QR_COMPLETED.name))) {
            val completed = s.qrCompletedAt ?: s.updatedAt
            if (now - completed > STALE_CONFIRMATION_MS) {
                transitionLocked(s.id, AlarmState.VERIFICATION_MISSED) { it.copy(fallbackReason = "stale_after_restart") }
            } else {
                wakeChecks.schedule(s.id, now + 5_000)
            }
        }
    }

    private suspend fun completePrimaryLocked(s: AlarmSessionEntity, method: String) {
        val now = System.currentTimeMillis()
        val done = transitionLocked(s.id, AlarmState.QR_COMPLETED) {
            it.copy(qrCompletedAt = now, dismissMethod = method)
        } ?: return
        stopRinging()
        diag.log(TAG, "session=${s.id} primary complete via $method")
        if (!done.isTest && method == "QR") rewards.grant(RewardType.QR_COMPLETED, "session:${s.id}")
        if (done.verificationDelayMin <= 0) {
            transitionLocked(s.id, AlarmState.CONFIRMED) { it.copy(confirmedAt = now) }
            return
        }
        transitionLocked(s.id, AlarmState.AWAITING_CONFIRMATION)
        wakeChecks.schedule(s.id, now + done.verificationDelayMin * 60_000L)
    }

    private suspend fun configFor(s: AlarmSessionEntity): RingConfig =
        if (s.isTest) RingConfig.test() else db.alarmDao().get(s.alarmId)?.let(RingConfig::from) ?: RingConfig.test()

    private suspend fun transitionLocked(
        id: Long,
        to: AlarmState,
        mutate: (AlarmSessionEntity) -> AlarmSessionEntity = { it },
    ): AlarmSessionEntity? {
        val s = dao.get(id) ?: return null
        val from = AlarmState.of(s.state)
        if (!from.canTransitionTo(to)) {
            diag.log(TAG, "rejected $from->$to session=$id")
            return null
        }
        val endState = when (to) {
            AlarmState.QR_COMPLETED, AlarmState.CONFIRMED, AlarmState.VERIFICATION_MISSED,
            AlarmState.EMERGENCY_DISMISSED, AlarmState.CANCELLED -> to.name
            else -> s.endState
        }
        val updated = mutate(s).copy(state = to.name, endState = endState, updatedAt = System.currentTimeMillis())
        dao.update(updated)
        return updated
    }

    private fun stopRinging() {
        try {
            context.startService(Intent(context, AlarmService::class.java).setAction(AlarmService.ACTION_STOP))
        } catch (_: IllegalStateException) {
            // Background start refused; the service's monitor sees the new state within ~2s.
        }
    }

    companion object {
        private const val TAG = "session"
        const val FOLLOW_UP_GRACE_MS = 2 * 60_000L
        const val MORE_TIME_MS = 2 * 60_000L
        private const val STALE_CONFIRMATION_MS = 60 * 60_000L
    }
}
