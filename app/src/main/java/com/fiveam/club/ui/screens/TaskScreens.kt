package com.fiveam.club.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.fiveam.club.data.db.Recurrence
import com.fiveam.club.data.db.TaskEntity
import com.fiveam.club.data.db.TaskStatus
import com.fiveam.club.tasks.TaskRecurrence
import com.fiveam.club.ui.components.BackTopBar
import com.fiveam.club.ui.components.ChoiceChips
import com.fiveam.club.ui.components.DatePickerDialogCompat
import com.fiveam.club.ui.components.DayChips
import com.fiveam.club.ui.components.SectionCard
import com.fiveam.club.ui.components.TimePickerDialog
import com.fiveam.club.ui.components.appContainer
import com.fiveam.club.ui.theme.Danger
import com.fiveam.club.util.TimeFormat
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

@Composable
fun TasksScreen(navigate: (String) -> Unit) {
    val c = appContainer()
    val scope = rememberCoroutineScope()
    val tasks by c.tasks.tasks.collectAsState(initial = emptyList())
    val today = LocalDate.now()
    val pending = tasks.filter { it.status == TaskStatus.PENDING.name }
    val overdue = pending.filter { c.tasks.isOverdue(it) }
    val rest = pending - overdue.toSet()
    val sections = listOf(
        "متأخرة" to overdue,
        "النهارده" to rest.filter { TimeFormat.localDate(it.dueAt) <= today },
        "بكرة" to rest.filter { TimeFormat.localDate(it.dueAt) == today.plusDays(1) },
        "الجاية" to rest.filter { TimeFormat.localDate(it.dueAt) > today.plusDays(1) },
        "خلصت" to tasks.filter { it.status == TaskStatus.DONE.name }.sortedByDescending { it.completedAt }.take(10),
    )

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { navigate("task/0") },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("مهمة جديدة") },
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { Text("المهام", style = MaterialTheme.typography.headlineMedium) }
            if (tasks.isEmpty()) item { SectionCard { Text("مفيش مهام. ضيف أول مهمة ليومك.") } }
            sections.forEach { (title, list) ->
                if (list.isNotEmpty()) {
                    item(key = "h_$title") {
                        Text(
                            title,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (title == "متأخرة") Danger else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    items(list, key = { "${title}_${it.id}" }) { task ->
                        TaskRow(
                            task = task,
                            overdue = title == "متأخرة",
                            onToggle = {
                                scope.launch {
                                    if (task.status == TaskStatus.DONE.name) c.tasks.reopen(task.id) else c.tasks.markDone(task.id)
                                }
                            },
                            onOpen = { navigate("task/${task.id}") },
                            onMoveTomorrow = { scope.launch { c.tasks.reschedule(task.id, c.tasks.nextDay(task.dueAt)) } },
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun TaskRow(task: TaskEntity, overdue: Boolean, onToggle: () -> Unit, onOpen: () -> Unit, onMoveTomorrow: () -> Unit) {
    val done = task.status == TaskStatus.DONE.name
    SectionCard(onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = done, onCheckedChange = { onToggle() })
            Column(Modifier.weight(1f)) {
                Text(
                    task.title,
                    style = MaterialTheme.typography.titleMedium,
                    textDecoration = if (done) TextDecoration.LineThrough else null,
                )
                val rec = runCatching { Recurrence.valueOf(task.recurrence) }.getOrDefault(Recurrence.NONE)
                Text(
                    buildString {
                        append(TimeFormat.dateTime(task.dueAt))
                        append(" · ").append(TimeFormat.duration(task.durationMin))
                        if (rec != Recurrence.NONE) append(" · ").append(TaskRecurrence.describe(rec, task.repeatDays))
                        if (task.priority == 2) append(" · ⭐ مهمة")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (overdue) TextButton(onClick = onMoveTomorrow) { Text("انقلها لبكرة") }
    }
}

private val reminderOptions = listOf<Pair<Int?, String>>(
    null to "من غير تذكير", 0 to "في الميعاد", 5 to "قبلها 5 د", 15 to "15 د", 30 to "30 د", 60 to "ساعة",
)

@Composable
fun TaskEditScreen(id: Long, onBack: () -> Unit) {
    val c = appContainer()
    val scope = rememberCoroutineScope()
    var task by remember { mutableStateOf<TaskEntity?>(null) }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(id) {
        task = if (id > 0) c.tasks.get(id) else {
            val start = ZonedDateTime.now().plusHours(1).withMinute(0).withSecond(0).withNano(0)
            TaskEntity(title = "", dueAt = start.toInstant().toEpochMilli())
        }
    }
    val t = task ?: return
    fun update(f: (TaskEntity) -> TaskEntity) {
        task = task?.let(f)
    }
    val due = TimeFormat.zoned(t.dueAt)

    Scaffold(
        topBar = {
            BackTopBar(if (id > 0) "تعديل المهمة" else "مهمة جديدة", onBack) {
                if (id > 0) {
                    IconButton(onClick = { scope.launch { c.tasks.delete(t); onBack() } }) {
                        Icon(Icons.Filled.Delete, contentDescription = "حذف المهمة")
                    }
                }
            }
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = t.title, onValueChange = { v -> update { it.copy(title = v.take(80)) }; error = null },
                label = { Text("اسم المهمة") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                isError = error != null,
            )
            OutlinedTextField(
                value = t.description, onValueChange = { v -> update { it.copy(description = v.take(500)) } },
                label = { Text("وصف (اختياري)") }, modifier = Modifier.fillMaxWidth(), minLines = 2,
            )
            SectionCard(title = "الميعاد") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { pickDate = true }) { Text(TimeFormat.relativeDay(due.toLocalDate())) }
                    TextButton(onClick = { pickTime = true }) { Text(TimeFormat.time(due.toLocalTime())) }
                }
                Text("المدة")
                ChoiceChips(
                    options = listOf(15 to "15 د", 30 to "30 د", 60 to "ساعة", 90 to "ساعة ونص", 120 to "ساعتين"),
                    selected = t.durationMin,
                    onSelect = { m -> update { it.copy(durationMin = m) } },
                )
            }
            SectionCard(title = "التذكير") {
                ChoiceChips(reminderOptions, t.reminderOffsetMin) { m -> update { it.copy(reminderOffsetMin = m, snoozedUntil = null) } }
                t.reminderOffsetMin?.let { off ->
                    Text(
                        "هيوصلك إشعار ${TimeFormat.dateTime(t.dueAt - off * 60_000L)}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            SectionCard(title = "التكرار") {
                val rec = runCatching { Recurrence.valueOf(t.recurrence) }.getOrDefault(Recurrence.NONE)
                ChoiceChips(
                    options = listOf(Recurrence.NONE to "مرة واحدة", Recurrence.DAILY to "يومي", Recurrence.WEEKLY to "أسبوعي", Recurrence.CUSTOM to "أيام محددة"),
                    selected = rec,
                    onSelect = { r -> update { it.copy(recurrence = r.name) } },
                )
                if (rec == Recurrence.CUSTOM) {
                    Spacer(Modifier.height(8.dp))
                    DayChips(t.repeatDays) { m -> update { it.copy(repeatDays = m) } }
                }
            }
            SectionCard(title = "الأولوية والتصنيف") {
                ChoiceChips(listOf(0 to "عادية", 1 to "متوسطة", 2 to "مهمة"), t.priority) { p -> update { it.copy(priority = p) } }
                Spacer(Modifier.height(8.dp))
                ChoiceChips(
                    listOf("عام" to "عام", "شغل" to "شغل", "دراسة" to "دراسة", "شخصي" to "شخصي", "صحة" to "صحة"),
                    t.category,
                ) { cat -> update { it.copy(category = cat) } }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = {
                    when {
                        t.title.isBlank() -> error = "اكتب اسم للمهمة"
                        t.recurrence == Recurrence.CUSTOM.name && t.repeatDays == 0 -> error = "اختار يوم واحد على الأقل"
                        else -> scope.launch { c.tasks.save(t.copy(title = t.title.trim())); onBack() }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text("حفظ") }
        }
    }

    if (pickDate) {
        DatePickerDialogCompat(due.toLocalDate(), onDismiss = { pickDate = false }) { d ->
            update { it.copy(dueAt = combine(d, due.toLocalTime())) }
            pickDate = false
        }
    }
    if (pickTime) {
        TimePickerDialog(due.toLocalTime(), onDismiss = { pickTime = false }) { tm ->
            update { it.copy(dueAt = combine(due.toLocalDate(), tm)) }
            pickTime = false
        }
    }
}

private fun combine(date: LocalDate, time: LocalTime): Long =
    ZonedDateTime.of(date, time, ZoneId.systemDefault()).toInstant().toEpochMilli()
