package com.fiveam.club.alarm.audio

import com.fiveam.club.data.db.AlarmEntity

data class RingConfig(
    val label: String,
    val ringtoneUri: String?,
    val targetVolumePercent: Int,
    val rampSeconds: Int,
    val vibrate: Boolean,
    val protectVolume: Boolean,
) {
    companion object {
        fun from(alarm: AlarmEntity) = RingConfig(
            label = alarm.label,
            ringtoneUri = alarm.ringtoneUri,
            targetVolumePercent = alarm.targetVolume,
            rampSeconds = alarm.rampSeconds,
            vibrate = alarm.vibrationEnabled,
            protectVolume = alarm.protectVolume,
        )

        /** Short, moderate test ring used by onboarding and the readiness screen (ONB-06). */
        fun test(volumePercent: Int = 60) = RingConfig(
            label = "منبه تجريبي",
            ringtoneUri = null,
            targetVolumePercent = volumePercent,
            rampSeconds = 3,
            vibrate = true,
            protectVolume = true,
        )
    }
}
