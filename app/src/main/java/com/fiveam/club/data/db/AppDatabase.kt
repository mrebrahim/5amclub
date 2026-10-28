package com.fiveam.club.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        AlarmEntity::class,
        AlarmSessionEntity::class,
        SleepEntryEntity::class,
        TaskEntity::class,
        TaskCompletionEntity::class,
        GoalEntity::class,
        RoutineEntity::class,
        RoutineStepEntity::class,
        RoutineRunEntity::class,
        RewardEventEntity::class,
        RestDayEntity::class,
        DiagnosticEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDao
    abstract fun sessionDao(): SessionDao
    abstract fun sleepDao(): SleepDao
    abstract fun taskDao(): TaskDao
    abstract fun goalDao(): GoalDao
    abstract fun routineDao(): RoutineDao
    abstract fun rewardDao(): RewardDao
    abstract fun restDayDao(): RestDayDao
    abstract fun diagnosticDao(): DiagnosticDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "fiveam.db").build()
    }
}
