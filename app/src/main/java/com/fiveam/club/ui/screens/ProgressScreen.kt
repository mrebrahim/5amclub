package com.fiveam.club.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fiveam.club.data.db.RestDayEntity
import com.fiveam.club.gamification.DayRecord
import com.fiveam.club.gamification.DayStatus
import com.fiveam.club.gamification.StreakStats
import com.fiveam.club.gamification.XpRules
import com.fiveam.club.ui.components.SectionCard
import com.fiveam.club.ui.components.appContainer
import com.fiveam.club.ui.theme.Danger
import com.fiveam.club.ui.theme.Success
import com.fiveam.club.ui.theme.Warning
import com.fiveam.club.util.TimeFormat
import com.fiveam.club.util.WeekDays
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

@Composable
fun ProgressScreen(navigate: (String) -> Unit) {
    val c = appContainer()
    val scope = rememberCoroutineScope()
    val xp by c.db.rewardDao().observeTotal().collectAsState(initial = 0)
    val restDays by c.db.restDayDao().observeAll().collectAsState(initial = emptyList())
    var streak by remember { mutableStateOf(StreakStats(0, 0, 0)) }
    var days by remember { mutableStateOf<List<DayRecord>>(emptyList()) }
    var restMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(xp, restDays) {
        streak = c.progress.streak()
        days = c.progress.dayRecords(LocalDate.now().minusDays(13), LocalDate.now())
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("التقدم", style = MaterialTheme.typography.headlineMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("السلسلة الحالية", "🔥 ${streak.current}", Modifier.weight(1f))
            StatCard("أعلى سلسلة", "🏆 ${streak.best}", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("أيام ناجحة", "✅ ${streak.totalSuccessDays}", Modifier.weight(1f))
            StatCard("النقاط", "⭐ $xp", Modifier.weight(1f))
        }
        SectionCard(title = "المحطات") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                XpRules.milestones.forEach { m ->
                    val reached = m <= streak.best
                    Text(
                        if (reached) "🏅$m" else "○$m",
                        color = if (reached) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
            Text(
                "اليوم الناجح = صحيان مؤكد في الميعاد + نوم كافي مسجل. أيام النوم القليل مش بتكسر السلسلة بس مش بتزودها.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SectionCard(title = "آخر أسبوعين") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                days.takeLast(14).forEach { d -> DayDot(d) }
            }
            Legend()
        }
        SectionCard(title = "يوم راحة مخطط") {
            Text("يوم راحة واحد في الأسبوع بيتحدد قبلها، ومش بيكسر السلسلة.")
            val tomorrow = LocalDate.now().plusDays(1)
            val planned = restDays.map { LocalDate.parse(it.date) }.filter { !it.isBefore(LocalDate.now()) }.sorted()
            planned.forEach { d ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("• ${TimeFormat.relativeDay(d)} (${WeekDays.shortName(d.dayOfWeek)})", Modifier.weight(1f))
                    TextButton(onClick = { scope.launch { c.db.restDayDao().delete(RestDayEntity(d.toString())) } }) { Text("إلغاء") }
                }
            }
            OutlinedButton(onClick = {
                scope.launch {
                    val weekStart = tomorrow.with(TemporalAdjusters.previousOrSame(DayOfWeek.SATURDAY))
                    val weekEnd = weekStart.plusDays(6)
                    val taken = restDays.map { LocalDate.parse(it.date) }.any { !it.isBefore(weekStart) && !it.isAfter(weekEnd) }
                    restMessage = if (taken) {
                        "عندك يوم راحة الأسبوع ده بالفعل."
                    } else {
                        c.db.restDayDao().insert(RestDayEntity(tomorrow.toString()))
                        "بكرة يوم راحة. نام كويس 😴"
                    }
                }
            }) { Text("خلّي بكرة يوم راحة") }
            restMessage?.let { Text(it) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { navigate("report") }) { Text("التقرير الأسبوعي") }
            OutlinedButton(onClick = { navigate("sleep") }) { Text("سجل النوم") }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    SectionCard(modifier = modifier) {
        Text(value, style = MaterialTheme.typography.headlineMedium)
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun statusColor(s: DayStatus): Color = when (s) {
    DayStatus.SUCCESS -> Success
    DayStatus.WOKE_SHORT_SLEEP, DayStatus.WOKE_SLEEP_UNKNOWN -> Warning
    DayStatus.MISSED -> Danger
    DayStatus.REST -> Color(0xFF7C9CFF)
    DayStatus.NONE -> Color(0x33FFFFFF)
}

private fun statusLabel(s: DayStatus): String = when (s) {
    DayStatus.SUCCESS -> "ناجح"
    DayStatus.WOKE_SHORT_SLEEP -> "صحيت بس نومك قليل"
    DayStatus.WOKE_SLEEP_UNKNOWN -> "صحيت والنوم مش متسجل"
    DayStatus.MISSED -> "فاتك"
    DayStatus.REST -> "راحة"
    DayStatus.NONE -> "مفيش منبه"
}

@Composable
private fun DayDot(d: DayRecord) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(16.dp).background(statusColor(d.status), CircleShape)
                .semantics { contentDescription = "${TimeFormat.date(d.date)}: ${statusLabel(d.status)}" },
        )
        Text(d.date.dayOfMonth.toString(), style = MaterialTheme.typography.bodyMedium.copy(textAlign = TextAlign.Center))
    }
}

@Composable
private fun Legend() {
    Column(Modifier.padding(top = 8.dp)) {
        listOf(DayStatus.SUCCESS, DayStatus.WOKE_SLEEP_UNKNOWN, DayStatus.MISSED, DayStatus.REST).forEach { s ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(statusColor(s), CircleShape))
                Text(" ${statusLabel(s)}", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
