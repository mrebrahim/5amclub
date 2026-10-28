package com.fiveam.club.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmDao {
    @Query("SELECT * FROM alarms ORDER BY hour, minute")
    fun observeAll(): Flow<List<AlarmEntity>>

    @Query("SELECT * FROM alarms")
    suspend fun getAll(): List<AlarmEntity>

    @Query("SELECT * FROM alarms WHERE id = :id")
    suspend fun get(id: Long): AlarmEntity?

    @Query("SELECT * FROM alarms WHERE id = :id")
    fun observe(id: Long): Flow<AlarmEntity?>

    @Upsert
    suspend fun upsert(alarm: AlarmEntity): Long

    @Delete
    suspend fun delete(alarm: AlarmEntity)

    @Query("UPDATE alarms SET nextTriggerAt = :at WHERE id = :id")
    suspend fun setNextTrigger(id: Long, at: Long?)

    @Query("UPDATE alarms SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("SELECT * FROM alarms WHERE enabled = 1 AND nextTriggerAt IS NOT NULL ORDER BY nextTriggerAt LIMIT 1")
    fun observeNext(): Flow<AlarmEntity?>

    @Query("SELECT * FROM alarms WHERE enabled = 1 AND nextTriggerAt IS NOT NULL ORDER BY nextTriggerAt LIMIT 1")
    suspend fun getNext(): AlarmEntity?
}

@Dao
interface SessionDao {
    @Insert
    suspend fun insert(session: AlarmSessionEntity): Long

    @Update
    suspend fun update(session: AlarmSessionEntity)

    @Query("SELECT * FROM alarm_sessions WHERE id = :id")
    suspend fun get(id: Long): AlarmSessionEntity?

    @Query("SELECT * FROM alarm_sessions WHERE id = :id")
    fun observe(id: Long): Flow<AlarmSessionEntity?>

    @Query("SELECT * FROM alarm_sessions WHERE state IN (:states) ORDER BY firedAt DESC LIMIT 1")
    suspend fun findInStates(states: List<String>): AlarmSessionEntity?

    @Query("SELECT * FROM alarm_sessions WHERE state IN (:states) ORDER BY firedAt DESC LIMIT 1")
    fun observeInStates(states: List<String>): Flow<AlarmSessionEntity?>

    @Query("SELECT * FROM alarm_sessions WHERE state IN (:states)")
    suspend fun allInStates(states: List<String>): List<AlarmSessionEntity>

    @Query("SELECT * FROM alarm_sessions WHERE isTest = 0 AND scheduledAt BETWEEN :from AND :to ORDER BY scheduledAt")
    suspend fun between(from: Long, to: Long): List<AlarmSessionEntity>

    @Query("SELECT * FROM alarm_sessions WHERE isTest = 0 AND scheduledAt BETWEEN :from AND :to ORDER BY scheduledAt")
    fun observeBetween(from: Long, to: Long): Flow<List<AlarmSessionEntity>>

    @Query("SELECT * FROM alarm_sessions WHERE isTest = 0 ORDER BY scheduledAt")
    suspend fun allReal(): List<AlarmSessionEntity>

    @Query("SELECT * FROM alarm_sessions WHERE isTest = 1 ORDER BY id DESC LIMIT 1")
    fun observeLatestTest(): Flow<AlarmSessionEntity?>

    @Query("SELECT * FROM alarm_sessions ORDER BY id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<AlarmSessionEntity>>
}

@Dao
interface SleepDao {
    @Query("SELECT * FROM sleep_entries ORDER BY date DESC")
    fun observeAll(): Flow<List<SleepEntryEntity>>

    @Query("SELECT * FROM sleep_entries WHERE date BETWEEN :from AND :to ORDER BY date")
    suspend fun between(from: String, to: String): List<SleepEntryEntity>

    @Query("SELECT * FROM sleep_entries")
    suspend fun all(): List<SleepEntryEntity>

    @Query("SELECT * FROM sleep_entries WHERE date = :date")
    suspend fun forDate(date: String): SleepEntryEntity?

    @Upsert
    suspend fun upsert(entry: SleepEntryEntity): Long

    @Delete
    suspend fun delete(entry: SleepEntryEntity)
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY dueAt")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks")
    suspend fun getAll(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun get(id: Long): TaskEntity?

    @Upsert
    suspend fun upsert(task: TaskEntity): Long

    @Delete
    suspend fun delete(task: TaskEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCompletion(completion: TaskCompletionEntity): Long

    @Query("SELECT * FROM task_completions WHERE completedAt BETWEEN :from AND :to")
    suspend fun completionsBetween(from: Long, to: Long): List<TaskCompletionEntity>

    @Query("SELECT * FROM task_completions")
    suspend fun allCompletions(): List<TaskCompletionEntity>
}

@Dao
interface GoalDao {
    @Query("SELECT * FROM goals WHERE date = :date ORDER BY priority DESC, id")
    fun observeForDate(date: String): Flow<List<GoalEntity>>

    @Query("SELECT * FROM goals WHERE date = :date ORDER BY priority DESC, id")
    suspend fun forDate(date: String): List<GoalEntity>

    @Query("SELECT * FROM goals")
    suspend fun all(): List<GoalEntity>

    @Upsert
    suspend fun upsert(goal: GoalEntity): Long

    @Delete
    suspend fun delete(goal: GoalEntity)
}

@Dao
interface RoutineDao {
    @Query("SELECT * FROM routines ORDER BY isShort, id")
    fun observeRoutines(): Flow<List<RoutineEntity>>

    @Query("SELECT * FROM routines ORDER BY isShort, id")
    suspend fun routines(): List<RoutineEntity>

    @Query("SELECT * FROM routines WHERE id = :id")
    suspend fun routine(id: Long): RoutineEntity?

    @Upsert
    suspend fun upsertRoutine(routine: RoutineEntity): Long

    @Query("SELECT * FROM routine_steps WHERE routineId = :routineId ORDER BY sortOrder")
    fun observeSteps(routineId: Long): Flow<List<RoutineStepEntity>>

    @Query("SELECT * FROM routine_steps WHERE routineId = :routineId ORDER BY sortOrder")
    suspend fun steps(routineId: Long): List<RoutineStepEntity>

    @Upsert
    suspend fun upsertStep(step: RoutineStepEntity): Long

    @Upsert
    suspend fun upsertSteps(steps: List<RoutineStepEntity>)

    @Delete
    suspend fun deleteStep(step: RoutineStepEntity)

    @Insert
    suspend fun insertRun(run: RoutineRunEntity): Long

    @Update
    suspend fun updateRun(run: RoutineRunEntity)

    @Query("SELECT * FROM routine_runs WHERE date BETWEEN :from AND :to")
    suspend fun runsBetween(from: String, to: String): List<RoutineRunEntity>

    @Query("SELECT * FROM routine_runs")
    suspend fun allRuns(): List<RoutineRunEntity>
}

@Dao
interface RewardDao {
    /** Returns -1 when the (type, sourceEventId) pair was already rewarded. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(event: RewardEventEntity): Long

    @Query("SELECT COALESCE(SUM(points), 0) FROM reward_events")
    fun observeTotal(): Flow<Int>

    @Query("SELECT COUNT(*) FROM reward_events WHERE type = :type AND createdAt BETWEEN :from AND :to")
    suspend fun countBetween(type: String, from: Long, to: Long): Int

    @Query("SELECT * FROM reward_events WHERE createdAt BETWEEN :from AND :to")
    suspend fun between(from: Long, to: Long): List<RewardEventEntity>

    @Query("SELECT * FROM reward_events")
    suspend fun all(): List<RewardEventEntity>
}

@Dao
interface RestDayDao {
    @Query("SELECT * FROM rest_days")
    fun observeAll(): Flow<List<RestDayEntity>>

    @Query("SELECT * FROM rest_days")
    suspend fun all(): List<RestDayEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(day: RestDayEntity)

    @Delete
    suspend fun delete(day: RestDayEntity)
}

@Dao
interface DiagnosticDao {
    @Insert
    suspend fun insert(entry: DiagnosticEntity)

    @Query("DELETE FROM diagnostic_log WHERE id NOT IN (SELECT id FROM diagnostic_log ORDER BY ts DESC LIMIT :keep)")
    suspend fun prune(keep: Int)

    @Query("SELECT * FROM diagnostic_log ORDER BY ts DESC LIMIT 300")
    fun observeRecent(): Flow<List<DiagnosticEntity>>
}
