package com.fiveam.club.routines

import com.fiveam.club.data.db.RoutineDao
import com.fiveam.club.data.db.RoutineEntity
import com.fiveam.club.data.db.RoutineRunEntity
import com.fiveam.club.data.db.RoutineStepEntity
import com.fiveam.club.gamification.RewardEngine
import com.fiveam.club.gamification.RewardType
import java.time.LocalDate

class RoutineRepository(private val dao: RoutineDao, private val rewards: RewardEngine) {
    val routines = dao.observeRoutines()

    fun steps(routineId: Long) = dao.observeSteps(routineId)

    suspend fun stepsNow(routineId: Long) = dao.steps(routineId)

    suspend fun routine(id: Long) = dao.routine(id)

    /** Seeds the 20/20/20 routine and a short one for hard days (RTN-05, RTN-06). */
    suspend fun ensureDefaults() {
        if (dao.routines().isNotEmpty()) return
        val main = dao.upsertRoutine(RoutineEntity(name = "روتين 20/20/20"))
        dao.upsertSteps(
            listOf(
                RoutineStepEntity(routineId = main, title = "حركة وتمارين", durationMin = 20, sortOrder = 0),
                RoutineStepEntity(routineId = main, title = "تأمل أو تخطيط اليوم", durationMin = 20, sortOrder = 1),
                RoutineStepEntity(routineId = main, title = "تعلّم: قراءة أو AI", durationMin = 20, sortOrder = 2),
            ),
        )
        val short = dao.upsertRoutine(RoutineEntity(name = "روتين الأيام الصعبة", isShort = true))
        dao.upsertSteps(
            listOf(
                RoutineStepEntity(routineId = short, title = "اشرب مية وافتح الشباك", durationMin = 2, sortOrder = 0),
                RoutineStepEntity(routineId = short, title = "حركة خفيفة", durationMin = 4, sortOrder = 1),
                RoutineStepEntity(routineId = short, title = "اكتب أهم 3 حاجات النهارده", durationMin = 4, sortOrder = 2),
            ),
        )
    }

    suspend fun addStep(routineId: Long, title: String, durationMin: Int) {
        val order = (dao.steps(routineId).maxOfOrNull { it.sortOrder } ?: -1) + 1
        dao.upsertStep(RoutineStepEntity(routineId = routineId, title = title, durationMin = durationMin, sortOrder = order))
    }

    suspend fun updateStep(step: RoutineStepEntity) = dao.upsertStep(step)

    suspend fun deleteStep(step: RoutineStepEntity) {
        dao.deleteStep(step)
        normalize(step.routineId)
    }

    /** RTN-04: swap with neighbour. [delta] is -1 (up) or +1 (down). */
    suspend fun move(step: RoutineStepEntity, delta: Int) {
        val steps = dao.steps(step.routineId).toMutableList()
        val i = steps.indexOfFirst { it.id == step.id }
        val j = i + delta
        if (i < 0 || j !in steps.indices) return
        steps[i] = steps[j].also { steps[j] = steps[i] }
        dao.upsertSteps(steps.mapIndexed { idx, s -> s.copy(sortOrder = idx) })
    }

    private suspend fun normalize(routineId: Long) {
        dao.upsertSteps(dao.steps(routineId).mapIndexed { idx, s -> s.copy(sortOrder = idx) })
    }

    suspend fun startRun(routineId: Long, sessionId: Long?): RoutineRunEntity {
        val total = dao.steps(routineId).size
        val run = RoutineRunEntity(
            routineId = routineId,
            date = LocalDate.now().toString(),
            sessionId = sessionId,
            startedAt = System.currentTimeMillis(),
            totalSteps = total,
        )
        return run.copy(id = dao.insertRun(run))
    }

    /** RTN-07/08: completion is tracked separately from the wake-up result. */
    suspend fun finishRun(run: RoutineRunEntity, completed: Int, skipped: Int) {
        val done = run.copy(finishedAt = System.currentTimeMillis(), completedSteps = completed, skippedSteps = skipped)
        dao.updateRun(done)
        if (run.totalSteps > 0 && completed == run.totalSteps) {
            rewards.grant(RewardType.ROUTINE_COMPLETED, "routine:${run.date}")
        }
    }
}
