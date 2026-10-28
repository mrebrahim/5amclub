package com.fiveam.club.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.content.Intent
import android.provider.Settings as AndroidSettings
import com.fiveam.club.coach.InsightEngine
import com.fiveam.club.data.prefs.Settings
import com.fiveam.club.data.prefs.ThemeMode
import com.fiveam.club.gamification.WeeklyReport
import com.fiveam.club.ui.components.BackTopBar
import com.fiveam.club.ui.components.ChoiceChips
import com.fiveam.club.ui.components.SectionCard
import com.fiveam.club.ui.components.SwitchRow
import com.fiveam.club.ui.components.appContainer
import com.fiveam.club.util.TimeFormat
import kotlinx.coroutines.launch
import java.time.LocalDate

private val delayReasons = listOf(
    "late_sleep" to "نمت متأخر",
    "didnt_hear" to "مسمعتش المنبه",
    "volume_down" to "وطيت الصوت",
    "back_to_sleep" to "رجعت نمت بعد QR",
    "tired" to "كنت مرهق",
    "external" to "ظروف خارجية",
    "other" to "سبب تاني",
)

@Composable
fun WeeklyReportScreen(onBack: () -> Unit) {
    val c = appContainer()
    val scope = rememberCoroutineScope()
    var report by remember { mutableStateOf<WeeklyReport?>(null) }
    var goal by remember { mutableStateOf(480) }
    var refresh by remember { mutableStateOf(0) }
    LaunchedEffect(refresh) {
        report = c.progress.weeklyReport()
        goal = c.settings.current().sleepGoalMin
    }
    Scaffold(topBar = { BackTopBar("التقرير الأسبوعي", onBack) }) { pad ->
        val r = report
        if (r == null) {
            Text("بنحسب…", Modifier.padding(pad).padding(16.dp))
            return@Scaffold
        }
        Column(
            Modifier.fillMaxSize().padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("${TimeFormat.date(r.from)} – ${TimeFormat.date(r.to)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            SectionCard(title = "الصحيان · مقاس من المنبه") {
                Metric("صحيت في الميعاد", "${r.onTimeDays} من ${r.scheduledDays} أيام")
                Metric("متوسط التأخير عن المنبه", r.avgDeviationMin?.let { "$it دقيقة" } ?: "—")
                Metric("نسبة إكمال QR", pct(r.qrRate))
                Metric("نسبة تأكيد الصحيان", pct(r.confirmRate))
                Metric("إيقاف طوارئ", "${r.emergencyCount}")
            }
            SectionCard(title = "النوم · من تسجيلك") {
                Metric("متوسط النوم", r.avgSleepMin?.let { TimeFormat.duration(it) } ?: "مش متسجل")
                Metric("أيام متسجلة", "${r.sleepLoggedDays} من 7")
                Metric("الهدف", TimeFormat.duration(goal))
            }
            SectionCard(title = "الإنجاز") {
                Metric("إكمال الروتين", pct(r.routineRate))
                Metric("مهام خلصت", "${r.tasksCompleted}")
                Metric("نقاط الأسبوع", "${r.xp} XP")
                Metric("السلسلة", "${r.streak.current} (أعلى ${r.streak.best})")
            }
            SectionCard(title = "ملاحظة الأسبوع · تقدير") {
                Text(InsightEngine.weeklyNote(r, goal), style = MaterialTheme.typography.bodyLarge)
            }
            if (r.lateSessions.isNotEmpty()) {
                SectionCard(title = "إيه اللي أخّرك؟ (اختياري)") {
                    r.lateSessions.forEach { s ->
                        Text(TimeFormat.dateTime(s.scheduledAt), style = MaterialTheme.typography.titleMedium)
                        ChoiceChips(delayReasons.map { Pair<String?, String>(it.first, it.second) }, s.delayReason) { reason ->
                            scope.launch {
                                c.sessions.setDelayReason(s.id, reason)
                                refresh++
                            }
                        }
                    }
                }
            }
            Text(
                "عدم الرد على التأكيد مش معناه إنك نايم، والرد مش معناه إنك صاحي تمامًا. دي مؤشرات بس.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

private fun pct(v: Float?) = v?.let { "${(it * 100).toInt()}%" } ?: "—"

@Composable
fun SettingsScreen(onBack: () -> Unit, navigate: (String) -> Unit) {
    val c = appContainer()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val s by c.settings.settings.collectAsState(initial = Settings())
    var confirmDelete by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            scope.launch {
                message = runCatching { c.data.exportTo(uri) }.fold({ "اتصدّرت البيانات ✓" }, { "التصدير فشل" })
            }
        }
    }
    fun save(f: (Settings) -> Settings) = scope.launch {
        c.settings.update(f)
        c.bedtime.reschedule()
    }

    Scaffold(topBar = { BackTopBar("الإعدادات", onBack) }) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard(title = "المظهر") {
                ChoiceChips(
                    listOf(ThemeMode.DARK to "داكن", ThemeMode.LIGHT to "فاتح", ThemeMode.SYSTEM to "زي الجهاز"),
                    s.themeMode,
                ) { m -> save { it.copy(themeMode = m) } }
            }
            SectionCard(title = "النوم") {
                Text("هدف النوم: ${TimeFormat.duration(s.sleepGoalMin)}")
                Slider(
                    value = s.sleepGoalMin.toFloat(),
                    onValueChange = { v -> save { it.copy(sleepGoalMin = (v / 15).toInt() * 15) } },
                    valueRange = 360f..600f,
                )
                Text("وقت لحد ما تنام: ${s.fallAsleepMin} دقيقة")
                ChoiceChips(listOf(5 to "5", 10 to "10", 15 to "15", 20 to "20", 30 to "30"), s.fallAsleepMin) { m ->
                    save { it.copy(fallAsleepMin = m) }
                }
                Text("نافذة الصحيان في الميعاد: ${s.wakeWindowMin} دقيقة")
                ChoiceChips(listOf(10 to "10", 15 to "15", 20 to "20", 30 to "30"), s.wakeWindowMin) { m ->
                    save { it.copy(wakeWindowMin = m) }
                }
            }
            SectionCard(title = "الإشعارات") {
                SwitchRow("تذكيرات قبل النوم", s.bedtimeRemindersEnabled, { v -> save { it.copy(bedtimeRemindersEnabled = v) } })
                TextButton(onClick = {
                    context.safeStart(
                        Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName),
                    )
                }) { Text("إعدادات إشعارات النظام") }
                TextButton(onClick = { navigate("readiness") }) { Text("جاهزية المنبه والصلاحيات") }
            }
            SectionCard(title = "بياناتك") {
                Text("كل بياناتك محفوظة على الموبايل بس. مفيش حساب ولا سيرفر.", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { exportLauncher.launch("fiveam-export-${LocalDate.now()}.json") }) { Text("تصدير") }
                    Button(
                        onClick = { confirmDelete = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    ) { Text("حذف كل البيانات") }
                }
                message?.let { Text(it) }
                TextButton(onClick = { navigate("diagnostics") }) { Text("السجل التشخيصي") }
            }
            Text(
                "5 AM مش جهاز طبي ومش بيشخّص أو يعالج مشاكل النوم.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("حذف كل البيانات؟") },
            text = { Text("هيتمسح كل المنبهات والمهام والسجلات والنقاط. مفيش رجوع.") },
            confirmButton = {
                Button(
                    onClick = {
                        confirmDelete = false
                        scope.launch {
                            c.db.alarmDao().getAll().forEach { c.alarms.delete(it) }
                            c.db.taskDao().getAll().forEach { c.tasks.delete(it) }
                            c.data.deleteEverything()
                            c.bedtime.reschedule()
                            c.routines.ensureDefaults()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("احذف") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("إلغاء") } },
        )
    }
}

@Composable
fun DiagnosticsScreen(onBack: () -> Unit) {
    val c = appContainer()
    val entries by c.db.diagnosticDao().observeRecent().collectAsState(initial = emptyList())
    Scaffold(topBar = { BackTopBar("السجل التشخيصي", onBack) }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp)) {
            item {
                Text(
                    "سجل تقني محلي بيساعد في اكتشاف مشاكل المنبه. مفيهوش محتوى مهامك أو أكواد QR.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            items(entries, key = { it.id }) { e ->
                Column(Modifier.padding(vertical = 4.dp)) {
                    Text("${TimeFormat.dateTime(e.ts)} · ${e.tag}", style = MaterialTheme.typography.labelLarge)
                    Text(e.message, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
