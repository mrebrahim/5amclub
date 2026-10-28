package com.fiveam.club.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fiveam.club.alarm.core.AlarmState
import com.fiveam.club.coach.Motivation
import com.fiveam.club.data.db.GoalEntity
import com.fiveam.club.data.db.RoutineEntity
import com.fiveam.club.ui.components.SectionCard
import com.fiveam.club.ui.components.appContainer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

/** WAK-04/WAK-08, MOT-04: confirm, then go straight into the first goal or the routine. */
@Composable
fun WakeConfirmScreen(sessionId: Long, autoConfirm: Boolean, navigate: (String) -> Unit, onDone: () -> Unit) {
    val c = appContainer()
    val scope = rememberCoroutineScope()
    val session by c.db.sessionDao().observe(sessionId).collectAsState(initial = null)
    var goal by remember { mutableStateOf<GoalEntity?>(null) }
    var routines by remember { mutableStateOf<List<RoutineEntity>>(emptyList()) }
    var motivation by remember { mutableStateOf("") }
    var askSleep by remember { mutableStateOf(false) }

    LaunchedEffect(sessionId) {
        if (autoConfirm) c.sessions.confirmWake(sessionId)
        goal = c.goals.forDateNow(LocalDate.now()).firstOrNull { !it.isCompleted }
        routines = c.routines.routines.first().filter { it.enabled }
        val s = c.settings.current()
        val (idx, line) = Motivation.pick(s.lastMotivationIndex, LocalDate.now().toEpochDay())
        c.settings.update { it.copy(lastMotivationIndex = idx) }
        motivation = Motivation.withGoal(line, goal?.motivationReason)
        if (c.sleep.forDate(LocalDate.now()) == null) askSleep = true
    }

    val state = session?.let { AlarmState.of(it.state) }
    val confirmed = state == AlarmState.CONFIRMED || state == AlarmState.ROUTINE_STARTED

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (!confirmed) {
            Text("صحيت؟", style = MaterialTheme.typography.headlineLarge)
            Button(
                onClick = { scope.launch { c.sessions.confirmWake(sessionId) } },
                modifier = Modifier.fillMaxWidth().height(64.dp),
            ) { Text("أنا صاحي ☀️", style = MaterialTheme.typography.titleMedium) }
            if (state == AlarmState.AWAITING_CONFIRMATION) {
                OutlinedButton(onClick = { scope.launch { c.sessions.needMoreTime(sessionId) }; onDone() }, modifier = Modifier.fillMaxWidth()) {
                    Text("محتاج دقيقة")
                }
                TextButton(onClick = { scope.launch { c.sessions.reportBackToSleep(sessionId) }; onDone() }) {
                    Text("رجعت نمت / معرفتش أصحى")
                }
            }
            if (state == AlarmState.VERIFICATION_MISSED) {
                Text("ماوصلناش تأكيد في الوقت، بس تقدر تسجل إنك صحيت دلوقتي.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Column
        }

        Text("صباح الفل ☀️", style = MaterialTheme.typography.headlineLarge)
        Text(motivation, style = MaterialTheme.typography.bodyLarge)
        goal?.let { g ->
            SectionCard(title = "أول هدف") {
                Text(g.title, style = MaterialTheme.typography.titleLarge)
                if (g.motivationReason.isNotBlank()) Text(g.motivationReason)
                Button(onClick = { scope.launch { c.goals.toggle(g); goal = null } }) { Text("ابدأ دلوقتي ✓ خلصته") }
            }
        }
        if (routines.isNotEmpty()) {
            SectionCard(title = "الروتين الصباحي") {
                routines.forEach { r ->
                    OutlinedButton(
                        onClick = { navigate("routine_run/${r.id}?session=$sessionId") },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (r.isShort) "${r.name} (قصير)" else r.name) }
                }
            }
        }
        TextButton(onClick = onDone) { Text("تخطي، روح للرئيسية") }
        Text(
            "التأكيد بيقول إنك تفاعلت مع التطبيق، مش إثبات طبي إنك صاحي تمامًا.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
    if (askSleep && confirmed) SleepEntryDialog(LocalDate.now(), onDismiss = { askSleep = false })
}
