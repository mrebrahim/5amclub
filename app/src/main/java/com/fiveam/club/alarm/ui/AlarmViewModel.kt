package com.fiveam.club.alarm.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fiveam.club.AppContainer
import com.fiveam.club.alarm.core.AlarmState
import com.fiveam.club.alarm.core.ScanResult
import com.fiveam.club.data.db.AlarmSessionEntity
import com.fiveam.club.data.db.DismissMode
import com.fiveam.club.data.db.GoalEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class AlarmViewModel(private val c: AppContainer) : ViewModel() {
    private val sessionId = MutableStateFlow<Long?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val session: StateFlow<AlarmSessionEntity?> = sessionId
        .flatMapLatest { id -> if (id == null) flowOf(null) else c.db.sessionDao().observe(id) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    var resolved by mutableStateOf(false)
        private set
    var dismissMode by mutableStateOf(DismissMode.QR_ANY)
        private set
    var firstGoal by mutableStateOf<GoalEntity?>(null)
        private set
    var lastVolumeKeyAt by mutableStateOf(0L)
        private set

    fun resolve(intentId: Long?) = viewModelScope.launch {
        val id = intentId?.takeIf { it > 0 }
            ?: c.db.sessionDao().findInStates(AlarmState.ACTIVE.map { it.name })?.id
        sessionId.value = id
        if (id != null) {
            val s = c.db.sessionDao().get(id)
            if (s != null && !s.isTest) {
                c.db.alarmDao().get(s.alarmId)?.let { a ->
                    dismissMode = runCatching { DismissMode.valueOf(a.dismissMode) }.getOrDefault(DismissMode.QR_ANY)
                }
            }
        }
        firstGoal = c.goals.forDateNow(LocalDate.now()).firstOrNull { !it.isCompleted }
        resolved = true
    }

    private fun withId(block: suspend (Long) -> Unit) {
        val id = sessionId.value ?: return
        viewModelScope.launch { block(id) }
    }

    fun startScan() = withId { c.sessions.startScan(it) }
    fun cancelScan() = withId { c.sessions.cancelScan(it) }
    fun scanFailed(reason: String) = withId { c.sessions.scanFailed(it, reason) }
    fun completeFallback() = withId { c.sessions.completeFallback(it) }
    fun emergency(reason: String) = withId { c.sessions.emergencyDismiss(it, reason) }

    fun onVolumeKey() {
        lastVolumeKeyAt = System.currentTimeMillis()
        withId { c.sessions.recordVolumeKey(it) }
    }

    suspend fun onPayload(payload: String): ScanResult {
        val id = sessionId.value ?: return ScanResult.IGNORED
        return c.sessions.onQrScanned(id, payload)
    }
}
