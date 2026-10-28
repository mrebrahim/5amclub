package com.fiveam.club.data

import android.content.Context
import android.net.Uri
import com.fiveam.club.data.db.AppDatabase
import com.fiveam.club.data.prefs.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** NFR-06: export everything as JSON, or wipe it. */
class DataManager(
    private val context: Context,
    private val db: AppDatabase,
    private val settings: SettingsRepository,
) {
    suspend fun exportTo(uri: Uri) = withContext(Dispatchers.IO) {
        val root = JSONObject()
        root.put("exportedAt", System.currentTimeMillis())
        root.put("alarms", JSONArray(db.alarmDao().getAll().map { a ->
            JSONObject().put("id", a.id).put("label", a.label).put("hour", a.hour).put("minute", a.minute)
                .put("repeatDays", a.repeatDays).put("enabled", a.enabled).put("targetVolume", a.targetVolume)
                .put("dismissMode", a.dismissMode).put("verificationDelayMin", a.verificationDelayMin)
        }))
        root.put("sessions", JSONArray(db.sessionDao().allReal().map { s ->
            JSONObject().put("id", s.id).put("alarmId", s.alarmId).put("scheduledAt", s.scheduledAt)
                .put("firedAt", s.firedAt).put("qrCompletedAt", s.qrCompletedAt).put("confirmedAt", s.confirmedAt)
                .put("endState", s.endState).put("dismissMethod", s.dismissMethod).put("delayReason", s.delayReason)
        }))
        root.put("sleep", JSONArray(db.sleepDao().all().map { e ->
            JSONObject().put("date", e.date).put("bedtime", e.bedtime).put("estimatedSleepStart", e.estimatedSleepStart)
                .put("wakeTime", e.wakeTime).put("durationMin", e.estimatedDurationMin)
                .put("energy", e.morningEnergyScore).put("source", e.dataSource)
        }))
        root.put("tasks", JSONArray(db.taskDao().getAll().map { t ->
            JSONObject().put("id", t.id).put("title", t.title).put("description", t.description).put("dueAt", t.dueAt)
                .put("durationMin", t.durationMin).put("priority", t.priority).put("category", t.category)
                .put("recurrence", t.recurrence).put("status", t.status)
        }))
        root.put("goals", JSONArray(db.goalDao().all().map { g ->
            JSONObject().put("date", g.date).put("title", g.title).put("reason", g.motivationReason).put("done", g.isCompleted)
        }))
        root.put("routineRuns", JSONArray(db.routineDao().allRuns().map { r ->
            JSONObject().put("date", r.date).put("completed", r.completedSteps).put("total", r.totalSteps)
        }))
        root.put("rewards", JSONArray(db.rewardDao().all().map { r ->
            JSONObject().put("type", r.type).put("points", r.points).put("createdAt", r.createdAt)
        }))
        context.contentResolver.openOutputStream(uri)?.use { it.write(root.toString(2).toByteArray()) }
            ?: error("Cannot open export destination")
    }

    suspend fun deleteEverything() = withContext(Dispatchers.IO) {
        db.clearAllTables()
        settings.clear()
    }
}
