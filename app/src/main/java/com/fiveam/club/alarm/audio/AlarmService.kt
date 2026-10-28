package com.fiveam.club.alarm.audio

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.fiveam.club.FiveAmApp
import com.fiveam.club.R
import com.fiveam.club.alarm.core.AlarmReceiver
import com.fiveam.club.alarm.core.AlarmState
import com.fiveam.club.alarm.ui.AlarmActivity
import com.fiveam.club.data.db.AlarmSessionEntity
import com.fiveam.club.util.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground service that owns the ringing phase. It never decides state on its own: the
 * session manager does, and this service follows the persisted session state.
 */
class AlarmService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var player: AlarmPlayer
    private var ringingSessionId: Long? = null
    private var monitorJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private val container get() = (application as FiveAmApp).container

    override fun onCreate() {
        super.onCreate()
        player = AlarmPlayer(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopRinging()
                return START_NOT_STICKY
            }
            ACTION_FIRE -> {
                promote(buildNotification("المنبه بيرن", null))
                val alarmId = intent.getLongExtra(AlarmReceiver.EXTRA_ALARM_ID, 0L)
                val scheduledAt = intent.getLongExtra(AlarmReceiver.EXTRA_SCHEDULED_AT, System.currentTimeMillis())
                scope.launch { onFire(alarmId, scheduledAt) }
            }
            else -> {
                // Restarted by the system after the process died mid-alarm (NFR-10).
                if (!promote(buildNotification("المنبه بيرن", null))) return START_NOT_STICKY
                scope.launch { recover() }
            }
        }
        return START_STICKY
    }

    private suspend fun onFire(alarmId: Long, scheduledAt: Long) {
        val result = container.sessions.onAlarmFired(alarmId, scheduledAt)
        if (result != null) {
            startRinging(result.session, result.config)
        } else if (ringingSessionId == null) {
            recover()
        }
    }

    private suspend fun recover() {
        val pair = container.sessions.recoverRinging()
        if (pair == null) {
            stopRinging()
        } else {
            startRinging(pair.session, pair.config)
        }
    }

    private suspend fun startRinging(session: AlarmSessionEntity, cfg: RingConfig) {
        if (ringingSessionId == session.id) return
        ringingSessionId = session.id
        acquireWakeLock()
        NotificationManagerCompat.from(this).let { nm ->
            if (nm.areNotificationsEnabled()) {
                runCatching { nm.notify(Notifications.ID_ALARM, buildNotification(cfg.label, session.id)) }
            }
        }
        player.start(cfg, scope)
        if (!player.hasSound) container.diag.log(TAG, "session=${session.id} no playable ringtone, vibration only")
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            runCatching { startActivity(AlarmActivity.intent(this, session.id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
        monitorJob?.cancel()
        monitorJob = scope.launch { monitor(session.id, session.firedAt ?: System.currentTimeMillis()) }
    }

    /** Follows persisted state, keeps the volume at target, pauses for calls, enforces the safety cap. */
    private suspend fun monitor(sessionId: Long, firedAt: Long) {
        while (scope.isActive) {
            delay(1_500)
            val s = container.db.sessionDao().get(sessionId)
            if (s == null || !AlarmState.of(s.state).isRinging) {
                stopRinging()
                return
            }
            player.handleCallState()
            if (player.restoreVolumeIfLowered()) container.sessions.recordVolumeRestore(sessionId)
            if (System.currentTimeMillis() - firedAt > MAX_RING_MS) {
                container.sessions.onRingTimeout(sessionId)
                stopRinging()
                return
            }
        }
    }

    private fun stopRinging() {
        monitorJob?.cancel()
        monitorJob = null
        player.stop()
        ringingSessionId = null
        NotificationManagerCompat.from(this).cancel(Notifications.ID_ALARM)
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        player.stop()
        wakeLock?.let { if (it.isHeld) it.release() }
        scope.cancel()
        super.onDestroy()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FiveAM:alarm").apply {
            setReferenceCounted(false)
            acquire(MAX_RING_MS + 60_000)
        }
    }

    private fun promote(notification: Notification): Boolean = try {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        } else {
            0
        }
        ServiceCompat.startForeground(this, Notifications.ID_ALARM, notification, type)
        true
    } catch (e: Exception) {
        scope.launch { container.diag.log(TAG, "startForeground failed: ${e.javaClass.simpleName}") }
        stopSelf()
        false
    }

    private fun buildNotification(title: String, sessionId: Long?): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, AlarmActivity.intent(this, sessionId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, Notifications.CH_ALARM)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(title)
            .setContentText("افتح المنبه وامسح QR علشان توقفه")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(pi)
            .setFullScreenIntent(pi, true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        const val ACTION_FIRE = "com.fiveam.club.action.SERVICE_FIRE"
        const val ACTION_STOP = "com.fiveam.club.action.SERVICE_STOP"
        /** Safety cap so a phone left alone doesn't ring for hours. */
        const val MAX_RING_MS = 30 * 60_000L
        private const val TAG = "service"
    }
}
