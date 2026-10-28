package com.fiveam.club.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.fiveam.club.util.WeekDays

enum class DismissMode { QR_ANY, QR_FIXED, MATH }

@Entity(tableName = "alarms")
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String = "صحيان الفجر",
    val hour: Int = 5,
    val minute: Int = 0,
    /** Bitmask of ISO weekdays (Mon = bit 0). 0 means a one-time alarm. */
    val repeatDays: Int = WeekDays.ALL,
    val enabled: Boolean = true,
    val ringtoneUri: String? = null,
    /** Percent of the alarm stream max. Never exceeded by the app. */
    val targetVolume: Int = 80,
    val rampSeconds: Int = 30,
    val vibrationEnabled: Boolean = true,
    val dismissMode: String = DismissMode.QR_ANY.name,
    /** SHA-256 of the fixed QR payload; the payload itself is never stored. */
    val qrReferenceHash: String? = null,
    val verificationDelayMin: Int = 5,
    val maxFollowUps: Int = 3,
    /** Restore the alarm stream to [targetVolume] if it drops while ringing. */
    val protectVolume: Boolean = true,
    /** Wall-clock time in the device's current zone; recomputed on zone change. */
    val timezonePolicy: String = "LOCAL_WALL_CLOCK",
    val nextTriggerAt: Long? = null,
)

@Entity(
    tableName = "alarm_sessions",
    indices = [Index("alarmId"), Index("scheduledAt"), Index("state")],
)
data class AlarmSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val alarmId: Long,
    val isTest: Boolean = false,
    val label: String = "",
    val scheduledAt: Long,
    val firedAt: Long? = null,
    val qrStartedAt: Long? = null,
    val qrCompletedAt: Long? = null,
    val confirmedAt: Long? = null,
    val routineStartedAt: Long? = null,
    val state: String,
    val endState: String? = null,
    /** QR, MATH or EMERGENCY. */
    val dismissMethod: String? = null,
    val fallbackReason: String? = null,
    val emergencyReason: String? = null,
    val verificationDelayMin: Int = 5,
    val maxFollowUps: Int = 3,
    val followUpsSent: Int = 0,
    val volumeKeyPresses: Int = 0,
    val volumeRestores: Int = 0,
    val delayReason: String? = null,
    val updatedAt: Long = System.currentTimeMillis(),
)

enum class DataSource { USER, ALARM, ESTIMATED }

@Entity(tableName = "sleep_entries", indices = [Index(value = ["date"], unique = true)])
data class SleepEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** ISO date of the morning the user woke up. */
    val date: String,
    val bedtime: Long? = null,
    /** User-estimated time they actually fell asleep. Never derived from bedtime alone. */
    val estimatedSleepStart: Long? = null,
    val wakeTime: Long? = null,
    val estimatedDurationMin: Int? = null,
    /** 1..5 */
    val morningEnergyScore: Int? = null,
    val dataSource: String = DataSource.USER.name,
)

enum class TaskStatus { PENDING, DONE }
enum class Recurrence { NONE, DAILY, WEEKLY, CUSTOM }

@Entity(tableName = "tasks", indices = [Index("dueAt")])
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "",
    /** Start of the task (date + start time), epoch millis. */
    val dueAt: Long,
    val durationMin: Int = 30,
    /** 0 = low, 1 = normal, 2 = high */
    val priority: Int = 1,
    val category: String = "عام",
    val recurrence: String = Recurrence.NONE.name,
    val repeatDays: Int = 0,
    /** Minutes before [dueAt]; null disables the reminder. */
    val reminderOffsetMin: Int? = 15,
    val snoozedUntil: Long? = null,
    val status: String = TaskStatus.PENDING.name,
    val completedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "task_completions",
    indices = [Index(value = ["taskId", "occurrenceDate"], unique = true)],
)
data class TaskCompletionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    val occurrenceDate: String,
    val completedAt: Long,
)

@Entity(tableName = "goals", indices = [Index("date")])
data class GoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val title: String,
    val description: String = "",
    val motivationReason: String = "",
    val priority: Int = 1,
    val expectedDurationMin: Int? = null,
    val linkedToRoutine: Boolean = false,
    val linkedTaskId: Long? = null,
    val isCompleted: Boolean = false,
)

@Entity(tableName = "routines")
data class RoutineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val isShort: Boolean = false,
    val enabled: Boolean = true,
    val startAfterWake: Boolean = true,
)

@Entity(tableName = "routine_steps", indices = [Index("routineId")])
data class RoutineStepEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val routineId: Long,
    val title: String,
    val durationMin: Int,
    val sortOrder: Int,
    val reminderEnabled: Boolean = false,
)

@Entity(tableName = "routine_runs", indices = [Index("date")])
data class RoutineRunEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val routineId: Long,
    val date: String,
    val sessionId: Long? = null,
    val startedAt: Long,
    val finishedAt: Long? = null,
    val completedSteps: Int = 0,
    val skippedSteps: Int = 0,
    val totalSteps: Int,
)

@Entity(
    tableName = "reward_events",
    indices = [Index(value = ["type", "sourceEventId"], unique = true), Index("createdAt")],
)
data class RewardEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val points: Int,
    val sourceEventId: String,
    val createdAt: Long,
)

@Entity(tableName = "rest_days")
data class RestDayEntity(
    @PrimaryKey val date: String,
)

@Entity(tableName = "diagnostic_log", indices = [Index("ts")])
data class DiagnosticEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ts: Long,
    val tag: String,
    val message: String,
)
