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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.fiveam.club.alarm.core.AlarmState
import com.fiveam.club.alarm.core.Readiness
import com.fiveam.club.alarm.ui.AlarmActivity
import com.fiveam.club.data.db.TaskStatus
import com.fiveam.club.data.prefs.Settings
import com.fiveam.club.gamification.StreakStats
import com.fiveam.club.sleep.SleepPlanner
import com.fiveam.club.ui.components.SectionCard
import com.fiveam.club.ui.components.appContainer
import com.fiveam.club.ui.theme.Warning
import com.fiveam.club.util.TimeFormat
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navigate: (String) -> Unit) {
    val c = appContainer()
    val context = LocalContext.current
    val settings by c.settings.settings.collectAsState(initial = Settings())
    val next by c.db.alarmDao().observeNext().collectAsState(initial = null)
    val active by c.db.sessionDao().observeInStates(AlarmState.ACTIVE.map { it.name }).collectAsState(initial = null)
    val tomorrow = LocalDate.now().plusDays(1)
    val goals by c.goals.forDate(tomorrow).collectAsState(initial = emptyList())
    val tasks by c.tasks.tasks.collectAsState(initial = emptyList())
    val xp by c.db.rewardDao().observeTotal().collectAsState(initial = 0)
    var streak by remember { mutableStateOf(StreakStats(0, 0, 0)) }
    var ready by remember { mutableStateOf(true) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { ready = Readiness.requiredOk(Readiness.check(context)) }
    LaunchedEffect(xp) { streak = c.progress.streak() }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(30_000)
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (LocalTime.now().hour < 12) "صباح الخير ☀️" else "مساء الخير 🌙", style = MaterialTheme.typography.headlineMedium)
                Text("🔥 ${streak.current} يوم · ⭐ $xp XP", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { navigate("settings") }) { Icon(Icons.Filled.Settings, contentDescription = "الإعدادات") }
        }

        active?.let { s ->
            val state = AlarmState.of(s.state)
            if (state.isRinging) {
                SectionCard(title = "المنبه بيرن", container = MaterialTheme.colorScheme.primaryContainer, onClick = {
                    context.startActivity(AlarmActivity.intent(context, s.id))
                }) { Text("افتح شاشة المنبه وامسح الكود") }
            } else if (!s.isTest) {
                SectionCard(title = "أكّد صحيانك", container = MaterialTheme.colorScheme.primaryContainer, onClick = {
                    navigate("wake/${s.id}?confirm=false")
                }) { Text("صحيت؟ دوس هنا علشان تأكد وتبدأ يومك.") }
            }
        }

        if (!ready) {
            SectionCard(title = "⚠️ المنبه مش جاهز بالكامل", onClick = { navigate("readiness") }) {
                Text("فيه صلاحية أو إعداد ناقص ممكن يمنع المنبه يرن صح. دوس علشان تشوف.", color = Warning)
            }
        }

        SectionCard(title = "المنبه الجاي", onClick = { navigate("alarms") }) {
            val n = next
            if (n?.nextTriggerAt == null) {
                Text("مفيش منبه مفعّل", style = MaterialTheme.typography.titleLarge)
                Text("دوس علشان تضيف منبه", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(TimeFormat.time(n.nextTriggerAt), style = MaterialTheme.typography.headlineLarge)
                Text("${n.label} · ${TimeFormat.relativeDay(TimeFormat.localDate(n.nextTriggerAt))} · ${TimeFormat.until(n.nextTriggerAt, now)}")
            }
        }

        next?.nextTriggerAt?.let { wakeMs ->
            val wake = TimeFormat.zoned(wakeMs).toLocalDateTime()
            val plan = SleepPlanner.plan(wake, settings.sleepGoalMin, settings.fallAsleepMin)
            val ifNow = SleepPlanner.projectedSleepMin(LocalDateTime.now(), wake, settings.fallAsleepMin)
            SectionCard(title = "خطة النوم", onClick = { navigate("bedtime") }) {
                Text("ادخل السرير ${TimeFormat.time(plan.bedAt.toLocalTime())}", style = MaterialTheme.typography.titleLarge)
                Text("علشان تنام ${TimeFormat.duration(settings.sleepGoalMin)} وتصحى ${TimeFormat.time(wake.toLocalTime())}")
                if (LocalDateTime.now().isAfter(plan.bedAt) && ifNow < settings.sleepGoalMin && ifNow < 16 * 60) {
                    Spacer(Modifier.height(4.dp))
                    Text("لو نمت دلوقتي هتنام حوالي ${TimeFormat.duration(ifNow)} بس.", color = Warning)
                }
            }
        }

        SectionCard(title = "إنت هتصحى بكرة ليه؟", onClick = { navigate("goals") }) {
            if (goals.isEmpty()) {
                Text("لسه مكتبتش أهداف لبكرة. اكتب هدف واحد على الأقل.")
            } else {
                goals.forEach { Text("• ${it.title}", style = MaterialTheme.typography.bodyLarge) }
            }
        }

        val today = LocalDate.now()
        val todayTasks = tasks.filter { TimeFormat.localDate(it.dueAt) == today }
        val done = todayTasks.count { it.status == TaskStatus.DONE.name }
        SectionCard(title = "تقدم النهارده", onClick = { navigate("tasks") }) {
            Text("مهام النهارده: $done من ${todayTasks.size}", style = MaterialTheme.typography.titleMedium)
            if (streak.nextMilestone() != null) {
                Text("فاضل ${streak.nextMilestone()!! - streak.current} يوم على محطة ${streak.nextMilestone()} يوم 🏁")
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = { navigate("routines") }, label = { Text("الروتين الصباحي") })
            AssistChip(onClick = { navigate("sleep") }, label = { Text("سجل النوم") })
            AssistChip(onClick = { navigate("report") }, label = { Text("التقرير") })
        }
    }
}
