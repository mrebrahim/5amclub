package com.fiveam.club.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

enum class ThemeMode { DARK, LIGHT, SYSTEM }

data class Settings(
    val onboardingDone: Boolean = false,
    val sleepGoalMin: Int = 480,
    val fallAsleepMin: Int = 15,
    val wakeDifficulty: String? = null,
    val bedtimeRemindersEnabled: Boolean = true,
    val remind60: Boolean = true,
    val remind30: Boolean = true,
    val remindAtBedtime: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val tipsEnabled: Boolean = true,
    /** Minutes after the scheduled time that still count as an on-time wake-up. */
    val wakeWindowMin: Int = 20,
    val lastTestScheduledAt: Long? = null,
    val lastMotivationIndex: Int = -1,
)

class SettingsRepository(private val context: Context) {
    private object K {
        val onboardingDone = booleanPreferencesKey("onboarding_done")
        val sleepGoalMin = intPreferencesKey("sleep_goal_min")
        val fallAsleepMin = intPreferencesKey("fall_asleep_min")
        val wakeDifficulty = stringPreferencesKey("wake_difficulty")
        val bedtimeReminders = booleanPreferencesKey("bedtime_reminders")
        val remind60 = booleanPreferencesKey("remind_60")
        val remind30 = booleanPreferencesKey("remind_30")
        val remind0 = booleanPreferencesKey("remind_0")
        val themeMode = stringPreferencesKey("theme_mode")
        val tipsEnabled = booleanPreferencesKey("tips_enabled")
        val wakeWindowMin = intPreferencesKey("wake_window_min")
        val lastTest = longPreferencesKey("last_test_at")
        val lastMotivation = intPreferencesKey("last_motivation")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { it.toSettings() }

    suspend fun current(): Settings = settings.first()

    private fun Preferences.toSettings() = Settings(
        onboardingDone = this[K.onboardingDone] ?: false,
        sleepGoalMin = this[K.sleepGoalMin] ?: 480,
        fallAsleepMin = this[K.fallAsleepMin] ?: 15,
        wakeDifficulty = this[K.wakeDifficulty],
        bedtimeRemindersEnabled = this[K.bedtimeReminders] ?: true,
        remind60 = this[K.remind60] ?: true,
        remind30 = this[K.remind30] ?: true,
        remindAtBedtime = this[K.remind0] ?: true,
        themeMode = this[K.themeMode]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.DARK,
        tipsEnabled = this[K.tipsEnabled] ?: true,
        wakeWindowMin = this[K.wakeWindowMin] ?: 20,
        lastTestScheduledAt = this[K.lastTest],
        lastMotivationIndex = this[K.lastMotivation] ?: -1,
    )

    suspend fun update(transform: (Settings) -> Settings) {
        context.dataStore.edit { prefs ->
            val s = transform(prefs.toSettings())
            prefs[K.onboardingDone] = s.onboardingDone
            prefs[K.sleepGoalMin] = s.sleepGoalMin
            prefs[K.fallAsleepMin] = s.fallAsleepMin
            if (s.wakeDifficulty != null) prefs[K.wakeDifficulty] = s.wakeDifficulty else prefs.remove(K.wakeDifficulty)
            prefs[K.bedtimeReminders] = s.bedtimeRemindersEnabled
            prefs[K.remind60] = s.remind60
            prefs[K.remind30] = s.remind30
            prefs[K.remind0] = s.remindAtBedtime
            prefs[K.themeMode] = s.themeMode.name
            prefs[K.tipsEnabled] = s.tipsEnabled
            prefs[K.wakeWindowMin] = s.wakeWindowMin
            if (s.lastTestScheduledAt != null) prefs[K.lastTest] = s.lastTestScheduledAt else prefs.remove(K.lastTest)
            prefs[K.lastMotivation] = s.lastMotivationIndex
        }
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }
}
