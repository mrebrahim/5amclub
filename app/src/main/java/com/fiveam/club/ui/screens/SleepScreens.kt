package com.fiveam.club.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fiveam.club.data.db.AlarmEntity
import com.fiveam.club.data.prefs.Settings
import com.fiveam.club.sleep.SleepPlanner
import com.fiveam.club.ui.components.BackTopBar
import com.fiveam.club.ui.components.ChoiceChips
import com.fiveam.club.ui.components.SectionCard
import com.fiveam.club.ui.components.SwitchRow
import com.fiveam.club.ui.components.TimePickerDialog
import com.fiveam.club.ui.components.appContainer
import com.fiveam.club.ui.theme.Warning
import com.fiveam.club.util.TimeFormat
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

@Composable
fun BedtimeScreen(onBack: () -> Unit, navigate: (String) -> Unit) {
    val c = appContainer()
    val scope = rememberCoroutineScope()
    val settings by c.settings.settings.collectAsState(initial = Settings())
    val next by c.db.alarmDao().observeNext().collectAsState(initial = null)
    var plannedBed by remember { mutableStateOf<LocalTime?>(null) }
    var pick by remember { mutableStateOf(false) }
    var confirmAlt by remember { mutableStateOf<Pair<AlarmEntity, LocalTime>?>(null) }

    fun saveSettings(f: (Settings) -> Settings) = scope.launch {
        c.settings.update(f)
        c.bedtime.reschedule()
    }

    Scaffold(topBar = { BackTopBar("مخطط النوم", onBack) }) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val alarm = next
            val wakeMs = alarm?.nextTriggerAt
            if (alarm == null || wakeMs == null) {
                SectionCard { Text("مفيش منبه مفعّل. فعّل منبه علشان نحسب ميعاد نومك.") }
            } else {
                val wake = TimeFormat.zoned(wakeMs).toLocalDateTime()
                val plan = SleepPlanner.plan(wake, settings.sleepGoalMin, settings.fallAsleepMin)
                SectionCard(title = "خطة الليلة") {
                    Text("ادخل السرير: ${TimeFormat.time(plan.bedAt.toLocalTime())}", style = MaterialTheme.typography.headlineMedium)
                    Text("نوم تقريبًا: ${TimeFormat.time(plan.sleepAt.toLocalTime())}")
                    Text("صحيان: ${TimeFormat.time(wake.toLocalTime())} (${TimeFormat.relativeDay(wake.toLocalDate())})")
                }
                SectionCard(title = "لو هنام متأخر؟") {
                    Text("اختار الوقت اللي متوقع تدخل فيه السرير ونقولك هتنام قد إيه.")
                    OutlinedButton(onClick = { pick = true }) {
                        Text(plannedBed?.let { "دخول السرير: ${TimeFormat.time(it)}" } ?: "اختار الوقت")
                    }
                    plannedBed?.let { t ->
                        val bedAt = bedDateTime(t, wake)
                        val got = SleepPlanner.projectedSleepMin(bedAt, wake, settings.fallAsleepMin)
                        val short = SleepPlanner.shortfallMin(bedAt, wake, settings.sleepGoalMin, settings.fallAsleepMin)
                        Text("هتنام حوالي ${TimeFormat.duration(got)}", style = MaterialTheme.typography.titleMedium)
                        if (short > 0) {
                            // SLP-05 warns, SLP-06 suggests; only the user changes the alarm (SLP-07).
                            Text("ده أقل من هدفك بـ ${TimeFormat.duration(short)}.", color = Warning)
                            val alt = SleepPlanner.suggestedWake(bedAt, settings.sleepGoalMin, settings.fallAsleepMin).toLocalTime()
                            Text("ممكن تصحى ${TimeFormat.time(alt)} بدل كده علشان تاخد نوم كافي.")
                            TextButton(onClick = { confirmAlt = alarm to alt }) { Text("غيّر المنبه لـ ${TimeFormat.time(alt)}؟") }
                        }
                    }
                }
            }
            SectionCard(title = "تذكيرات قبل النوم") {
                SwitchRow("تفعيل التذكيرات", settings.bedtimeRemindersEnabled, { v -> saveSettings { it.copy(bedtimeRemindersEnabled = v) } })
                if (settings.bedtimeRemindersEnabled) {
                    SwitchRow("قبلها بساعة: ابدأ تهدي يومك", settings.remind60, { v -> saveSettings { it.copy(remind60 = v) } })
                    SwitchRow("قبلها بنص ساعة: جهّز حاجات بكرة", settings.remind30, { v -> saveSettings { it.copy(remind30 = v) } })
                    SwitchRow("في الميعاد: حان وقت النوم", settings.remindAtBedtime, { v -> saveSettings { it.copy(remindAtBedtime = v) } })
                }
            }
            SectionCard(title = "إرشادات") {
                Text(
                    "• ثبّت ميعاد النوم والصحيان كل يوم.\n• قلل الإضاءة والشاشات قبل النوم بساعة.\n• بلاش كافيين بعد العصر.\n• أوضة هادية وبرد خفيف ومظلمة.\n\nدي نصايح عامة، والتطبيق مش جهاز طبي ومش بيقيس مراحل النوم.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { navigate("goals") }) { Text("أهداف بكرة") }
                OutlinedButton(onClick = { navigate("sleep") }) { Text("سجل النوم") }
            }
        }
    }
    if (pick) {
        TimePickerDialog(plannedBed ?: LocalTime.now().withSecond(0).withNano(0), onDismiss = { pick = false }) {
            plannedBed = it
            pick = false
        }
    }
    confirmAlt?.let { (alarm, alt) ->
        AlertDialog(
            onDismissRequest = { confirmAlt = null },
            title = { Text("تغيير ميعاد المنبه؟") },
            text = {
                Text(
                    if (alarm.repeatDays == 0) {
                        "المنبه هيتغير لـ ${TimeFormat.time(alt)}."
                    } else {
                        "ده منبه متكرر، فالتغيير هيطبق على كل أيامه (${TimeFormat.time(alt)}). تقدر ترجعه بعدين من شاشة المنبه."
                    },
                )
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch { c.alarms.save(alarm.copy(hour = alt.hour, minute = alt.minute)) }
                    confirmAlt = null
                }) { Text("غيّر") }
            },
            dismissButton = { TextButton(onClick = { confirmAlt = null }) { Text("سيبه زي ما هو") } },
        )
    }
}

/** Interprets a picked bedtime as the evening before [wake] (or the same night after midnight). */
private fun bedDateTime(t: LocalTime, wake: LocalDateTime): LocalDateTime {
    val sameDay = wake.toLocalDate().atTime(t)
    return if (sameDay.isBefore(wake)) sameDay else sameDay.minusDays(1)
}

@Composable
fun SleepDiaryScreen(onBack: () -> Unit) {
    val c = appContainer()
    val entries by c.sleep.entries.collectAsState(initial = emptyList())
    var editing by remember { mutableStateOf<LocalDate?>(null) }
    Scaffold(topBar = { BackTopBar("سجل النوم", onBack) }) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(onClick = { editing = LocalDate.now() }) { Text("سجّل نوم امبارح بالليل") }
            Text(
                "التطبيق مش بيقيس نومك. الأرقام دي من تسجيلك إنت، ووقت دخول السرير مش بيتحسب كأنه وقت النوم.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            entries.forEach { e ->
                SectionCard(onClick = { editing = LocalDate.parse(e.date) }) {
                    Text(TimeFormat.relativeDay(LocalDate.parse(e.date)), style = MaterialTheme.typography.titleMedium)
                    Text(
                        buildString {
                            append(e.estimatedDurationMin?.let { "نوم تقريبًا ${TimeFormat.duration(it)}" } ?: "مدة النوم مش معروفة")
                            e.bedtime?.let { append(" · السرير ${TimeFormat.time(it)}") }
                            e.morningEnergyScore?.let { append(" · طاقة $it/5") }
                        },
                    )
                    Text("من تسجيلك", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    editing?.let { date -> SleepEntryDialog(date, onDismiss = { editing = null }) }
}

/** Quick diary entry (SLP-03). Also used right after the wake-up confirmation. */
@Composable
fun SleepEntryDialog(date: LocalDate, onDismiss: () -> Unit) {
    val c = appContainer()
    val scope = rememberCoroutineScope()
    val zone = ZoneId.systemDefault()
    var bed by remember { mutableStateOf<LocalTime?>(null) }
    var asleep by remember { mutableStateOf<LocalTime?>(null) }
    var wake by remember { mutableStateOf<LocalTime?>(LocalTime.now().withSecond(0).withNano(0)) }
    var energy by remember { mutableStateOf<Int?>(null) }
    var picking by remember { mutableStateOf<String?>(null) }

    fun toMs(t: LocalTime?, isNight: Boolean): Long? {
        t ?: return null
        // Evening times belong to the previous calendar day.
        val d = if (isNight && t.hour >= 12) date.minusDays(1) else date
        return d.atTime(t).atZone(zone).toInstant().toEpochMilli()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("نمت إزاي؟") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TimeField("دخلت السرير", bed) { picking = "bed" }
                TimeField("نمت تقريبًا الساعة", asleep) { picking = "asleep" }
                TimeField("صحيت", wake) { picking = "wake" }
                Text("طاقتك الصبح")
                ChoiceChips(listOf<Pair<Int?, String>>(1 to "1", 2 to "2", 3 to "3", 4 to "4", 5 to "5"), energy) { energy = it }
                if (asleep == null) {
                    Text("من غير وقت النوم التقريبي مش هنحسب مدة النوم.", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                scope.launch {
                    c.sleep.save(date, toMs(bed, true), toMs(asleep, true), toMs(wake, false), energy)
                    onDismiss()
                }
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("بعدين") } },
    )

    picking?.let { which ->
        val initial = when (which) {
            "bed" -> bed ?: LocalTime.of(22, 30)
            "asleep" -> asleep ?: bed?.plusMinutes(15) ?: LocalTime.of(22, 45)
            else -> wake ?: LocalTime.of(5, 0)
        }
        TimePickerDialog(initial, onDismiss = { picking = null }) { t ->
            when (which) {
                "bed" -> bed = t
                "asleep" -> asleep = t
                else -> wake = t
            }
            picking = null
        }
    }
}

@Composable
private fun TimeField(label: String, value: LocalTime?, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = onClick) { Text(value?.let { TimeFormat.time(it) } ?: "اختار") }
    }
    Spacer(Modifier.height(2.dp))
}
