package com.fiveam.club.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.fiveam.club.data.db.GoalEntity
import com.fiveam.club.goals.GoalRepository
import com.fiveam.club.ui.components.BackTopBar
import com.fiveam.club.ui.components.ChoiceChips
import com.fiveam.club.ui.components.SectionCard
import com.fiveam.club.ui.components.appContainer
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun GoalsScreen(onBack: () -> Unit) {
    val c = appContainer()
    val scope = rememberCoroutineScope()
    val today = LocalDate.now()
    val tomorrow = today.plusDays(1)
    val todayGoals by c.goals.forDate(today).collectAsState(initial = emptyList())
    val tomorrowGoals by c.goals.forDate(tomorrow).collectAsState(initial = emptyList())
    var title by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    var priority by remember { mutableIntStateOf(1) }
    var duration by remember { mutableIntStateOf(30) }
    var message by remember { mutableStateOf<String?>(null) }

    Scaffold(topBar = { BackTopBar("إنت هتصحى بكرة ليه؟", onBack) }) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard(title = "أهداف بكرة (${tomorrowGoals.size}/${GoalRepository.MAX_PER_DAY})") {
                if (tomorrowGoals.isEmpty()) Text("اكتب من هدف لـ 3 أهداف رئيسية.")
                tomorrowGoals.forEach { g ->
                    GoalRow(g, onToggle = null, onDelete = { scope.launch { c.goals.delete(g) } }, onCarry = null)
                }
            }
            if (tomorrowGoals.size < GoalRepository.MAX_PER_DAY) {
                SectionCard(title = "هدف جديد") {
                    OutlinedTextField(title, { title = it.take(80) }, label = { Text("الهدف") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(
                        reason, { reason = it.take(160) },
                        label = { Text("ليه مهم ليك؟ (السبب اللي هيصحيك)") }, modifier = Modifier.fillMaxWidth(),
                    )
                    Text("الأولوية")
                    ChoiceChips(listOf(0 to "عادية", 1 to "متوسطة", 2 to "عالية"), priority) { priority = it }
                    Text("المدة المتوقعة")
                    ChoiceChips(listOf(15 to "15 د", 30 to "30 د", 60 to "ساعة", 120 to "ساعتين"), duration) { duration = it }
                    message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Button(
                        enabled = title.isNotBlank(),
                        onClick = {
                            scope.launch {
                                val ok = c.goals.save(
                                    GoalEntity(
                                        date = tomorrow.toString(), title = title.trim(), motivationReason = reason.trim(),
                                        priority = priority, expectedDurationMin = duration,
                                    ),
                                )
                                if (ok) {
                                    title = ""; reason = ""; message = null
                                } else {
                                    message = "كفاية 3 أهداف، ركّز عليهم."
                                }
                            }
                        },
                    ) { Text("ضيف الهدف") }
                }
            }
            if (todayGoals.isNotEmpty()) {
                SectionCard(title = "أهداف النهارده") {
                    todayGoals.forEach { g ->
                        GoalRow(
                            g,
                            onToggle = { scope.launch { c.goals.toggle(g) } },
                            onDelete = { scope.launch { c.goals.delete(g) } },
                            onCarry = if (!g.isCompleted) ({ scope.launch { c.goals.carryOver(g) } }) else null,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GoalRow(g: GoalEntity, onToggle: (() -> Unit)?, onDelete: () -> Unit, onCarry: (() -> Unit)?) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        if (onToggle != null) Checkbox(g.isCompleted, { onToggle() })
        Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
            Text(
                g.title,
                style = MaterialTheme.typography.titleMedium,
                textDecoration = if (g.isCompleted) TextDecoration.LineThrough else null,
            )
            if (g.motivationReason.isNotBlank()) {
                Text(g.motivationReason, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (onCarry != null) TextButton(onClick = onCarry) { Text("رحّله لبكرة") }
        }
        IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "حذف الهدف") }
    }
}
