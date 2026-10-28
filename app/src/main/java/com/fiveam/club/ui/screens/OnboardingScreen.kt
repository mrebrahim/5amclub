package com.fiveam.club.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fiveam.club.alarm.core.Readiness
import com.fiveam.club.data.db.AlarmEntity
import com.fiveam.club.sleep.SleepPlanner
import com.fiveam.club.ui.components.ChoiceChips
import com.fiveam.club.ui.components.DayChips
import com.fiveam.club.ui.components.SectionCard
import com.fiveam.club.ui.components.TimePickerDialog
import com.fiveam.club.ui.components.appContainer
import com.fiveam.club.util.TimeFormat
import com.fiveam.club.util.WeekDays
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

private val difficulties = listOf(
    "dismiss_and_sleep" to "بطفي المنبه وبرجع أنام",
    "volume_down" to "بوطي صوت المنبه",
    "cant_hear" to "مش بسمع المنبه",
    "late_sleep" to "بنام متأخر",
    "cant_start" to "بصحى بس مش بعرف أبدأ يومي",
)

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val c = appContainer()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var step by remember { mutableIntStateOf(0) }
    var wake by remember { mutableStateOf(LocalTime.of(5, 0)) }
    var days by remember { mutableIntStateOf(WeekDays.ALL) }
    var sleepGoal by remember { mutableIntStateOf(480) }
    var difficulty by remember { mutableStateOf<String?>(null) }
    var pickTime by remember { mutableStateOf(false) }
    val lastStep = 5

    fun finish() = scope.launch {
        c.settings.update {
            it.copy(
                onboardingDone = true,
                sleepGoalMin = sleepGoal,
                wakeDifficulty = difficulty,
                bedtimeRemindersEnabled = true,
            )
        }
        // ONB-04 only tunes suggested defaults; it is not a diagnosis.
        val base = AlarmEntity(hour = wake.hour, minute = wake.minute, repeatDays = days)
        val tuned = when (difficulty) {
            "volume_down" -> base.copy(protectVolume = true, rampSeconds = 15)
            "cant_hear" -> base.copy(targetVolume = 90, rampSeconds = 10)
            "dismiss_and_sleep" -> base.copy(verificationDelayMin = 5, maxFollowUps = 3)
            else -> base
        }
        c.alarms.save(tuned)
        onDone()
    }

    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(20.dp)) {
        if (step > 0) LinearProgressIndicator(progress = { step / lastStep.toFloat() }, modifier = Modifier.fillMaxWidth())
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (step) {
                0 -> {
                    Spacer(Modifier.height(48.dp))
                    Text("☀️", style = MaterialTheme.typography.displayLarge, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                    Text("5 AM", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                    Text(
                        "اصحى في معادك، ابدأ يومك بهدف، وشوف إنجازك بيزيد كل يوم.",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    SectionCard {
                        Text("• منبه بيقف بمسح كود QR بعيد عن السرير\n• سؤال بعد شوية علشان نتأكد إنك صاحي\n• تخطيط للنوم وأهداف بكرة\n• مهام وتذكيرات وروتين صباحي\n• كل حاجة بتشتغل من غير إنترنت")
                    }
                }
                1 -> {
                    Text("عايز تصحى الساعة كام؟", style = MaterialTheme.typography.headlineMedium)
                    SectionCard(onClick = { pickTime = true }) {
                        Text(TimeFormat.time(wake), style = MaterialTheme.typography.displayLarge)
                        Text("دوس علشان تغيّر", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    ChoiceChips(
                        options = listOf(LocalTime.of(4, 30) to "4:30", LocalTime.of(5, 0) to "5:00", LocalTime.of(5, 30) to "5:30", LocalTime.of(6, 0) to "6:00"),
                        selected = wake,
                        onSelect = { wake = it },
                    )
                    Text("الأيام", style = MaterialTheme.typography.titleMedium)
                    DayChips(days) { days = it }
                    Text(
                        "تقدر تضيف منبهات تانية بمواعيد مختلفة لأيام معينة بعدين.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                2 -> {
                    Text("محتاج تنام كام ساعة؟", style = MaterialTheme.typography.headlineMedium)
                    Text(TimeFormat.duration(sleepGoal), style = MaterialTheme.typography.headlineLarge)
                    Slider(
                        value = sleepGoal.toFloat(),
                        onValueChange = { sleepGoal = (it / 15).toInt() * 15 },
                        valueRange = 360f..600f,
                    )
                    val plan = SleepPlanner.plan(LocalDate.now().plusDays(1).atTime(wake), sleepGoal, 15)
                    SectionCard(title = "خطتك المقترحة") {
                        Text("ادخل السرير: ${TimeFormat.time(plan.bedAt.toLocalTime())}", style = MaterialTheme.typography.titleLarge)
                        Text("نوم فعلي تقريبًا: ${TimeFormat.time(plan.sleepAt.toLocalTime())}")
                        Text("صحيان: ${TimeFormat.time(wake)}")
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "حسبنا ربع ساعة لحد ما تنام. تقدر تغيرها من الإعدادات.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                3 -> {
                    Text("إيه أكتر حاجة بتصعّب عليك الصحيان؟", style = MaterialTheme.typography.headlineMedium)
                    difficulties.forEach { (key, label) ->
                        Row(
                            Modifier.fillMaxWidth()
                                .selectable(selected = difficulty == key, onClick = { difficulty = key }, role = Role.RadioButton)
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = difficulty == key, onClick = null)
                            Text(label, modifier = Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    Text(
                        "بنستخدم إجابتك علشان نقترح إعدادات ونصايح مناسبة بس، مش تشخيص.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                4 -> {
                    Text("خلينا نجهّز المنبه", style = MaterialTheme.typography.headlineMedium)
                    Text("كل صلاحية مكتوب جنبها بنحتاجها ليه. تقدر تغيرها في أي وقت.")
                    ReadinessPanel()
                }
                5 -> {
                    Text("جرّب المنبه دلوقتي", style = MaterialTheme.typography.headlineMedium)
                    Text("أحسن طريقة تتأكد إن كل حاجة شغالة على جهازك.")
                    TestAlarmPanel()
                    val issues = Readiness.check(context).filter { !it.ok && it.severity == com.fiveam.club.alarm.core.Severity.REQUIRED }
                    if (issues.isNotEmpty()) {
                        SectionCard(title = "لسه فيه مشاكل") {
                            issues.forEach { Text("• ${it.title}: ${it.why}") }
                            Text(
                                "المنبه ممكن ميشتغلش صح لحد ما تظبطها. تقدر ترجع لها من شاشة جاهزية المنبه.",
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            if (step > 0) TextButton(onClick = { step-- }) { Text("رجوع") } else Spacer(Modifier)
            Button(
                onClick = { if (step < lastStep) step++ else finish() },
                modifier = Modifier.height(56.dp),
            ) { Text(if (step == 0) "ابدأ رحلتك" else if (step < lastStep) "التالي" else "يلا بينا") }
        }
    }

    if (pickTime) {
        TimePickerDialog(initial = wake, onDismiss = { pickTime = false }, onConfirm = { wake = it; pickTime = false })
    }
}

