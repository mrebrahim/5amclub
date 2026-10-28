package com.fiveam.club.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fiveam.club.coach.Insight
import com.fiveam.club.coach.InsightEngine
import com.fiveam.club.coach.TipsLibrary
import com.fiveam.club.ui.components.SectionCard
import com.fiveam.club.ui.components.appContainer
import java.time.LocalDate

@Composable
fun CoachScreen(navigate: (String) -> Unit) {
    val c = appContainer()
    var insights by remember { mutableStateOf<List<Insight>>(emptyList()) }
    LaunchedEffect(Unit) {
        val report = c.progress.weeklyReport()
        insights = InsightEngine.analyze(report, c.settings.current().sleepGoalMin)
    }
    val today = LocalDate.now()
    // TIP-03: the tip follows what the data says matters most right now.
    val tip = TipsLibrary.tipOfDay(today, insights.firstOrNull()?.tipCategory)
    val challenge = TipsLibrary.challengeOfDay(today)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("المدرب", style = MaterialTheme.typography.headlineMedium)
        SectionCard(title = "نصيحة النهارده · ${tip.category.label}") {
            Text(tip.text, style = MaterialTheme.typography.bodyLarge)
        }
        SectionCard(title = "تحدي النهارده") {
            Text(challenge.text, style = MaterialTheme.typography.titleMedium)
        }
        if (insights.isNotEmpty()) {
            Text("ملاحظات من بياناتك", style = MaterialTheme.typography.titleMedium)
            insights.forEach { i ->
                SectionCard(title = i.title) {
                    Text(i.recommendation, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "ليه؟ ${i.evidence} (${i.source.label})",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            SectionCard { Text("لسه مفيش بيانات كفاية للملاحظات. كمّل أسبوع وهنقولك نلاحظ إيه.") }
        }
        SectionCard(title = "مدرب AI · قريبًا") {
            Text(
                "في مرحلة جاية هتقدر تتكلم مع مدرب ذكي بالعربي. مش هنبعت أي بيانات ليك لخدمة سحابية من غير موافقتك الصريحة، والمدرب مش هيغير منبهاتك من غير إذنك.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        SectionCard(onClick = { navigate("report") }) { Text("افتح التقرير الأسبوعي ←") }
    }
}
