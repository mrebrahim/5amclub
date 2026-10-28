package com.fiveam.club

import android.content.Context
import com.fiveam.club.alarm.core.AlarmRepository
import com.fiveam.club.alarm.core.AlarmScheduler
import com.fiveam.club.alarm.core.AlarmSessionManager
import com.fiveam.club.data.DataManager
import com.fiveam.club.data.db.AppDatabase
import com.fiveam.club.data.prefs.SettingsRepository
import com.fiveam.club.diagnostics.DiagLog
import com.fiveam.club.gamification.ProgressRepository
import com.fiveam.club.gamification.RewardEngine
import com.fiveam.club.goals.GoalRepository
import com.fiveam.club.routines.RoutineRepository
import com.fiveam.club.sleep.BedtimeScheduler
import com.fiveam.club.sleep.SleepRepository
import com.fiveam.club.tasks.TaskReminderScheduler
import com.fiveam.club.tasks.TaskRepository
import com.fiveam.club.wake.WakeCheckScheduler
import com.fiveam.club.wake.WakeNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Manual dependency graph. Nothing here needs network, login or a backend (PRD §15.3). */
class AppContainer(val context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val db = AppDatabase.build(context)
    val settings = SettingsRepository(context)
    val diag = DiagLog(db.diagnosticDao())
    val rewards = RewardEngine(db.rewardDao())
    val progress = ProgressRepository(db, settings, rewards)

    val alarmScheduler = AlarmScheduler(context, db.alarmDao(), diag)
    val bedtime = BedtimeScheduler(context, db.alarmDao(), settings)
    val alarms = AlarmRepository(alarmScheduler, bedtime)
    val wakeChecks = WakeCheckScheduler(context)
    val wakeNotifier = WakeNotifier(context)
    val sessions = AlarmSessionManager(context, db, alarms, wakeChecks, wakeNotifier, rewards, progress, diag)

    val taskReminders = TaskReminderScheduler(context, db.taskDao())
    val tasks = TaskRepository(db.taskDao(), taskReminders, rewards)
    val goals = GoalRepository(db.goalDao(), rewards)
    val routines = RoutineRepository(db.routineDao(), rewards)
    val sleep = SleepRepository(db.sleepDao(), progress)
    val data = DataManager(context, db, settings)

    suspend fun restoreAfterSystemEvent() {
        alarms.rescheduleAll()
        taskReminders.rescheduleAll()
        sessions.restorePendingChecks()
    }
}
