package com.fiveam.club.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fiveam.club.data.db.RoutineEntity
import com.fiveam.club.data.db.RoutineRunEntity
import com.fiveam.club.data.db.RoutineStepEntity
import com.fiveam.club.ui.components.BackTopBar
import com.fiveam.club.ui.components.ChoiceChips
import com.fiveam.club.ui.components.SectionCard
import com.fiveam.club.ui.components.appContainer
import com.fiveam.club.util.TimeFormat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun RoutineEditScreen(onBack: () -> Unit, navigate: (String) -> Unit) {
    val c = appContainer()
    val routines by c.routines.routines.collectAsState(initial = emptyList())
    Scaffold(topBar = { BackTopBar("الروتين الصباحي", onBack) }) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            routines.forEach { r -> RoutineCard(r, navigate) }
        }
    }
}

@Composable
private fun RoutineCard(routine: RoutineEntity, navigate: (String) -> Unit) {
    val c = appContainer()
    val scope = rememberCoroutineScope()
    val steps by c.routines.steps(routine.id).collectAsState(initial = emptyList())
    var newTitle by remember { mutableStateOf("") }
    var newDuration by remember { mutableIntStateOf(10) }
    SectionCard(title = "${routine.name} · ${TimeFormat.duration(steps.sumOf { it.durationMin })}") {
        steps.forEachIndexed { i, step ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${i + 1}. ${step.title}", style = MaterialTheme.typography.titleMedium)
                    Text(TimeFormat.duration(step.durationMin), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { scope.launch { c.routines.move(step, -1) } }, enabled = i > 0) {
                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "لفوق")
                }
                IconButton(onClick = { scope.launch { c.routines.move(step, 1) } }, enabled = i < steps.lastIndex) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "لتحت")
                }
                IconButton(onClick = { scope.launch { c.routines.deleteStep(step) } }) {
                    Icon(Icons.Filled.Delete, contentDescription = "حذف ${step.title}")
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(newTitle, { newTitle = it.take(60) }, label = { Text("نشاط جديد") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        ChoiceChips(listOf(2 to "2 د", 5 to "5 د", 10 to "10 د", 15 to "15 د", 20 to "20 د", 30 to "30 د"), newDuration) { newDuration = it }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = newTitle.isNotBlank(), onClick = {
                scope.launch { c.routines.addStep(routine.id, newTitle.trim(), newDuration) }
                newTitle = ""
            }) { Text("ضيف") }
            Button(enabled = steps.isNotEmpty(), onClick = { navigate("routine_run/${routine.id}") }) { Text("ابدأ الروتين") }
        }
    }
}

/** RTN-07: start, skip a step, or stop at any time. */
@Composable
fun RoutineRunScreen(routineId: Long, sessionId: Long?, onDone: () -> Unit) {
    val c = appContainer()
    val scope = rememberCoroutineScope()
    var steps by remember { mutableStateOf<List<RoutineStepEntity>>(emptyList()) }
    var routine by remember { mutableStateOf<RoutineEntity?>(null) }
    var run by remember { mutableStateOf<RoutineRunEntity?>(null) }
    var index by remember { mutableIntStateOf(0) }
    var completed by remember { mutableIntStateOf(0) }
    var skipped by remember { mutableIntStateOf(0) }
    var remaining by remember { mutableIntStateOf(0) }
    var paused by remember { mutableStateOf(false) }
    var goalTitle by remember { mutableStateOf<String?>(null) }
    var finished by remember { mutableStateOf(false) }

    LaunchedEffect(routineId) {
        routine = c.routines.routine(routineId)
        steps = c.routines.stepsNow(routineId)
        run = c.routines.startRun(routineId, sessionId)
        if (sessionId != null) c.sessions.markRoutineStarted(sessionId)
        goalTitle = c.goals.forDateNow(LocalDate.now()).firstOrNull { !it.isCompleted }?.title
        remaining = (steps.firstOrNull()?.durationMin ?: 0) * 60
    }
    LaunchedEffect(index, paused, steps) {
        while (!paused && remaining > 0 && !finished) {
            delay(1_000)
            remaining--
        }
    }

    fun advance(done: Boolean) {
        if (done) completed++ else skipped++
        if (index >= steps.lastIndex) {
            finished = true
            run?.let { r -> scope.launch { c.routines.finishRun(r, completed, skipped) } }
        } else {
            index++
            remaining = steps[index].durationMin * 60
        }
    }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(routine?.name ?: "الروتين", style = MaterialTheme.typography.titleLarge)
        // UX-08: a short reminder of the main goal.
        goalTitle?.let { Text("هدفك النهارده: $it", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center) }
        if (steps.isEmpty()) return@Column
        if (finished) {
            Spacer(Modifier.height(48.dp))
            Text("🎉", style = MaterialTheme.typography.displayLarge)
            Text("خلصت $completed من ${steps.size} خطوات", style = MaterialTheme.typography.headlineMedium)
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("ابدأ يومك") }
            return@Column
        }
        LinearProgressIndicator(progress = { index / steps.size.toFloat() }, modifier = Modifier.fillMaxWidth())
        Text("خطوة ${index + 1} من ${steps.size}")
        Spacer(Modifier.height(24.dp))
        Text(steps[index].title, style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
        Text("%d:%02d".format(remaining / 60, remaining % 60), style = MaterialTheme.typography.displayLarge)
        Button(onClick = { advance(true) }, modifier = Modifier.fillMaxWidth().height(64.dp)) { Text("خلصت ✓") }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { paused = !paused }) { Text(if (paused) "كمّل" else "وقفة") }
            OutlinedButton(onClick = { advance(false) }) { Text("تخطّي") }
        }
        TextButton(onClick = {
            finished = true
            run?.let { r -> scope.launch { c.routines.finishRun(r, completed, skipped) } }
        }) { Text("إنهاء الروتين") }
    }
}
