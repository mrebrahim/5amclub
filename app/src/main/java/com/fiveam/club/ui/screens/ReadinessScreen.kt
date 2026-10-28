package com.fiveam.club.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.fiveam.club.alarm.core.AlarmState
import com.fiveam.club.alarm.core.Readiness
import com.fiveam.club.alarm.core.ReadinessItem
import com.fiveam.club.alarm.core.Severity
import com.fiveam.club.data.prefs.Settings as AppSettings
import com.fiveam.club.ui.components.BackTopBar
import com.fiveam.club.ui.components.SectionCard
import com.fiveam.club.ui.components.StatusDot
import com.fiveam.club.ui.components.appContainer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ReadinessScreen(onBack: () -> Unit) {
    Scaffold(topBar = { BackTopBar("جاهزية المنبه", onBack) }) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ReadinessPanel()
            TestAlarmPanel()
            LimitationsCard()
        }
    }
}

@Composable
fun ReadinessPanel() {
    val context = LocalContext.current
    var items by remember { mutableStateOf(Readiness.check(context)) }
    val refresh = { items = Readiness.check(context) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh() }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh() }

    val ready = Readiness.requiredOk(items)
    SectionCard(title = if (ready) "المنبه جاهز ✓" else "فيه حاجات محتاجة تظبيط") {
        items.forEach { item ->
            ReadinessRow(item) {
                when {
                    item.permission != null && !item.ok -> launcher.launch(item.permission)
                    item.settingsIntent != null -> context.safeStart(item.settingsIntent)
                }
            }
        }
    }
}

@Composable
private fun ReadinessRow(item: ReadinessItem, onFix: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
        StatusDot(if (item.ok) true else if (item.severity == Severity.REQUIRED) false else null)
        Column(Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.titleMedium)
            Text(item.why, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!item.ok) {
                Spacer(Modifier.height(4.dp))
                OutlinedButton(onClick = onFix) { Text(if (item.permission != null) "اسمح" else "افتح الإعدادات") }
            }
        }
    }
}

/** ONB-06: a real end-to-end run of the alarm pipeline, with honest results. */
@Composable
fun TestAlarmPanel() {
    val c = appContainer()
    val scope = rememberCoroutineScope()
    val settings by c.settings.settings.collectAsState(initial = AppSettings())
    val latest by c.db.sessionDao().observeLatestTest().collectAsState(initial = null)
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val scheduledAt = settings.lastTestScheduledAt
    val run = latest?.takeIf { scheduledAt != null && (it.firedAt ?: 0) >= scheduledAt - 1_000 }

    SectionCard(title = "اختبار المنبه") {
        Text("هيرن منبه تجريبي بعد 10 ثواني بصوت متوسط. اقفل الشاشة لو عايز تجرب شاشة القفل، وبعدين امسح أي كود QR.")
        Spacer(Modifier.height(12.dp))
        Button(onClick = {
            scope.launch {
                val at = System.currentTimeMillis() + 10_000
                c.alarmScheduler.scheduleTest(at)
                c.settings.update { it.copy(lastTestScheduledAt = at) }
            }
        }) { Text("ابدأ الاختبار") }

        if (scheduledAt != null) {
            Spacer(Modifier.height(12.dp))
            val waiting = run == null
            when {
                waiting && now < scheduledAt -> Text("هيرن ${(scheduledAt - now) / 1000 + 1} ثانية…")
                waiting && now - scheduledAt < 60_000 -> Text("مستنيين المنبه يرن…")
                waiting -> Text(
                    "المنبه مرنش في الدقيقة الأولى. راجع صلاحية المنبهات الدقيقة والإشعارات وتوفير البطارية فوق.",
                    color = MaterialTheme.colorScheme.error,
                )
                else -> {
                    val s = run!!
                    val state = AlarmState.of(s.state)
                    StepRow("رن المنبه", s.firedAt != null, s.firedAt?.let { "بتأخير ${(it - scheduledAt) / 1000} ثانية" })
                    StepRow("اتفتح الماسح", s.qrStartedAt != null)
                    StepRow(
                        "اتمسح QR",
                        s.dismissMethod == "QR",
                        when (s.dismissMethod) {
                            "MATH" -> "اتقفل بالبديل الحسابي (${s.fallbackReason ?: ""})"
                            "EMERGENCY" -> "اتقفل بإيقاف الطوارئ"
                            else -> null
                        },
                    )
                    StepRow("الدورة اكتملت", state == AlarmState.CONFIRMED)
                    if (s.volumeKeyPresses > 0 || s.volumeRestores > 0) {
                        Text(
                            "زراير الصوت: ${s.volumeKeyPresses} ضغطة، الصوت اترجع للمستوى المختار ${s.volumeRestores} مرة.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StepRow(title: String, ok: Boolean, note: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StatusDot(ok)
        Column {
            Text(title)
            if (note != null) Text(note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LimitationsCard() {
    SectionCard(title = "حدود لازم تعرفها") {
        Text(
            "• لو الموبايل مقفول خالص المنبه مش هيرن.\n" +
                "• لو عملت Force Stop للتطبيق من الإعدادات، النظام بيلغي منبهاته لحد ما تفتحه تاني.\n" +
                "• زراير الصوت مش بتوقف المنبه. وإنت فاتح شاشة المنبه التطبيق بيتجاهلها، ولو الصوت قل التطبيق بيرجعه للمستوى اللي اخترته بس، مش أعلى. بعض الأجهزة ممكن تتصرف بشكل مختلف.\n" +
                "• وضع عدم الإزعاج وإعدادات الجهاز ليها الأولوية دايمًا.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

fun android.content.Context.safeStart(intent: Intent) {
    try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    } catch (_: SecurityException) {
        // Some OEM settings screens are not exported; nothing else to do.
    }
}
