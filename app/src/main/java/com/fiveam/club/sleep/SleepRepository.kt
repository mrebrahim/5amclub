package com.fiveam.club.sleep

import com.fiveam.club.data.db.DataSource
import com.fiveam.club.data.db.SleepDao
import com.fiveam.club.data.db.SleepEntryEntity
import com.fiveam.club.gamification.ProgressRepository
import java.time.LocalDate

class SleepRepository(private val dao: SleepDao, private val progress: ProgressRepository) {
    val entries = dao.observeAll()

    suspend fun forDate(date: LocalDate) = dao.forDate(date.toString())

    /**
     * Saves a diary entry. Duration comes only from the user's estimated sleep start, never
     * from bedtime (SLP-04, AC-18).
     */
    suspend fun save(
        date: LocalDate,
        bedtime: Long?,
        estimatedSleepStart: Long?,
        wakeTime: Long?,
        energy: Int?,
    ) {
        val existing = dao.forDate(date.toString())
        val duration = if (estimatedSleepStart != null && wakeTime != null && wakeTime > estimatedSleepStart) {
            SleepPlanner.durationMin(estimatedSleepStart, wakeTime)
        } else {
            null
        }
        dao.upsert(
            SleepEntryEntity(
                id = existing?.id ?: 0,
                date = date.toString(),
                bedtime = bedtime,
                estimatedSleepStart = estimatedSleepStart,
                wakeTime = wakeTime,
                estimatedDurationMin = duration,
                morningEnergyScore = energy,
                dataSource = DataSource.USER.name,
            ),
        )
        progress.evaluateDate(date)
    }

    suspend fun delete(entry: SleepEntryEntity) = dao.delete(entry)
}
