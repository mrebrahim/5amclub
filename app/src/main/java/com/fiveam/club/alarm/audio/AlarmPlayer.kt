package com.fiveam.club.alarm.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Plays the alarm on the alarm stream (ALM-07). The stream is set to exactly the user's target
 * level (never above it) and restored afterwards. It does not touch any other stream.
 */
class AlarmPlayer(private val context: Context) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java).defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)
    }
    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private var player: MediaPlayer? = null
    private var rampJob: Job? = null
    private var focusRequest: AudioFocusRequest? = null
    private var config: RingConfig? = null
    private var targetIndex = 0
    private var originalIndex: Int? = null
    private var pausedForCall = false

    /** False if no ringtone could be played (vibration only); the session logs it. */
    var hasSound: Boolean = false
        private set

    fun start(cfg: RingConfig, scope: CoroutineScope) {
        stop()
        config = cfg
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        targetIndex = (max * cfg.targetVolumePercent / 100.0).roundToInt().coerceIn(1, max)
        val current = audio.getStreamVolume(AudioManager.STREAM_ALARM)
        if (current != targetIndex) {
            originalIndex = current
            setAlarmVolume(targetIndex)
        }
        requestFocus()
        player = createPlayer(cfg.ringtoneUri)
        hasSound = player != null
        rampJob = scope.launch { ramp(cfg.rampSeconds) }
        if (cfg.vibrate) vibrate()
    }

    /** Returns true if the alarm stream had dropped below target and was restored. */
    fun restoreVolumeIfLowered(): Boolean {
        if (config?.protectVolume != true || player == null) return false
        val current = audio.getStreamVolume(AudioManager.STREAM_ALARM)
        if (current >= targetIndex) return false
        return setAlarmVolume(targetIndex)
    }

    /** ALM-06: step aside while a phone call is ringing or active. */
    fun handleCallState() {
        val inCall = audio.mode == AudioManager.MODE_IN_CALL ||
            audio.mode == AudioManager.MODE_IN_COMMUNICATION ||
            audio.mode == AudioManager.MODE_RINGTONE
        val p = player
        if (inCall && !pausedForCall) {
            pausedForCall = true
            runCatching { p?.pause() }
            vibrator.cancel()
        } else if (!inCall && pausedForCall) {
            pausedForCall = false
            runCatching { p?.start() }
            if (config?.vibrate == true) vibrate()
        }
    }

    fun stop() {
        rampJob?.cancel()
        rampJob = null
        player?.let {
            runCatching { it.stop() }
            it.release()
        }
        player = null
        vibrator.cancel()
        focusRequest?.let { audio.abandonAudioFocusRequest(it) }
        focusRequest = null
        originalIndex?.let { setAlarmVolume(it) }
        originalIndex = null
        pausedForCall = false
        config = null
    }

    private suspend fun ramp(seconds: Int) {
        val p = player ?: return
        if (seconds <= 0) {
            p.setVolume(1f, 1f)
            return
        }
        val steps = seconds * 2
        for (i in 1..steps) {
            val v = 0.15f + 0.85f * i / steps
            runCatching { p.setVolume(v, v) }
            delay(500)
        }
    }

    private fun createPlayer(custom: String?): MediaPlayer? {
        val candidates = listOfNotNull(
            custom?.let(Uri::parse),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
        )
        for (uri in candidates) {
            val mp = MediaPlayer()
            try {
                mp.setAudioAttributes(attrs)
                mp.setDataSource(context, uri)
                mp.isLooping = true
                mp.setVolume(0.15f, 0.15f)
                mp.prepare()
                mp.start()
                return mp
            } catch (_: Exception) {
                mp.release()
            }
        }
        return null
    }

    private fun vibrate() {
        if (!vibrator.hasVibrator()) return
        val effect = VibrationEffect.createWaveform(longArrayOf(0, 700, 500), 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(effect, attrs)
        }
    }

    private fun requestFocus() {
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attrs)
            .setOnAudioFocusChangeListener { }
            .build()
        audio.requestAudioFocus(req)
        focusRequest = req
    }

    private fun setAlarmVolume(index: Int): Boolean = try {
        audio.setStreamVolume(AudioManager.STREAM_ALARM, index, 0)
        true
    } catch (_: SecurityException) {
        // Some DND policies forbid volume changes; we respect that.
        false
    }

    companion object {
        fun alarmVolumePercent(context: Context): Int {
            val audio = context.getSystemService(AudioManager::class.java)
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM).coerceAtLeast(1)
            return audio.getStreamVolume(AudioManager.STREAM_ALARM) * 100 / max
        }
    }
}
