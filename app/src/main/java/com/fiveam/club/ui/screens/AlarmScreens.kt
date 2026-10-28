package com.fiveam.club.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.fiveam.club.alarm.audio.AlarmPlayer
import com.fiveam.club.alarm.audio.RingConfig
import com.fiveam.club.data.db.AlarmEntity
import com.fiveam.club.data.db.DismissMode
import com.fiveam.club.qr.QrScanner
import com.fiveam.club.qr.QrValidator
import com.fiveam.club.ui.components.BackTopBar
import com.fiveam.club.ui.components.ChoiceChips
import com.fiveam.club.ui.components.DayChips
import com.fiveam.club.ui.components.SectionCard
import com.fiveam.club.ui.components.SwitchRow
import com.fiveam.club.ui.components.TimePickerDialog
import com.fiveam.club.ui.components.appContainer
import com.fiveam.club.util.TimeFormat
import com.fiveam.club.util.WeekDays
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalTime

@Composable
fun AlarmsScreen(navigate: (String) -> Unit) {
    val c = appContainer()
    val scope = rememberCoroutineScope()
    val alarms by c.db.alarmDao().observeAll().collectAsState(initial = emptyList())
    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { navigate("alarm/0") },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("منبه جديد") },
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("المنبهات", style = MaterialTheme.typography.headlineMedium)
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { navigate("readiness") }) { Text("الجاهزية واختبار المنبه") }
                }
            }
            if (alarms.isEmpty()) {
                item { SectionCard { Text("مفيش منبهات لسه. اعمل أول منبه ليك.") } }
            }
            items(alarms, key = { it.id }) { alarm ->
                SectionCard(onClick = { navigate("alarm/${alarm.id}") }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(TimeFormat.time(alarm.hour, alarm.minute), style = MaterialTheme.typography.headlineLarge)
                            Text("${alarm.label} · ${WeekDays.describe(alarm.repeatDays)}")
                            Text(
                                dismissLabel(alarm) + " · تأكيد بعد ${alarm.verificationDelayMin} د",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = alarm.enabled,
                            onCheckedChange = { on -> scope.launch { c.alarms.setEnabled(alarm.id, on) } },
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

private fun dismissLabel(a: AlarmEntity) = when (a.dismissMode) {
    DismissMode.QR_FIXED.name -> "كود QR محدد"
    DismissMode.MATH.name -> "مسائل حسابية"
    else -> "أي كود QR"
}

@Composable
fun AlarmEditScreen(id: Long, onBack: () -> Unit) {
    val c = appContainer()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var alarm by remember { mutableStateOf<AlarmEntity?>(null) }
    var pickTime by remember { mutableStateOf(false) }
    var scanningFixed by remember { mutableStateOf(false) }
    var previewing by remember { mutableStateOf(false) }
    val player = remember { AlarmPlayer(context) }
    DisposableEffect(Unit) { onDispose { player.stop() } }

    LaunchedEffect(id) {
        alarm = if (id > 0) c.db.alarmDao().get(id) else AlarmEntity()
    }
    val ringtoneLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val uri: Uri? = if (Build.VERSION.SDK_INT >= 33) {
                res.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                res.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }
            alarm = alarm?.copy(ringtoneUri = uri?.toString())
        }
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) scanningFixed = true
    }

    val a = alarm ?: return
    fun update(f: (AlarmEntity) -> AlarmEntity) {
        alarm = alarm?.let(f)
    }

    Scaffold(
        topBar = {
            BackTopBar(if (id > 0) "تعديل المنبه" else "منبه جديد", onBack) {
                if (id > 0) {
                    IconButton(onClick = { scope.launch { c.alarms.delete(a); onBack() } }) {
                        Icon(Icons.Filled.Delete, contentDescription = "حذف المنبه")
                    }
                }
            }
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard(onClick = { pickTime = true }) {
                Text(TimeFormat.time(a.hour, a.minute), style = MaterialTheme.typography.displayLarge)
                Text("دوس علشان تغيّر الوقت", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedTextField(
                value = a.label,
                onValueChange = { v -> update { it.copy(label = v.take(40)) } },
                label = { Text("اسم المنبه") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            SectionCard(title = "الأيام") {
                DayChips(a.repeatDays) { m -> update { it.copy(repeatDays = m) } }
                Text(WeekDays.describe(a.repeatDays), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SectionCard(title = "طريقة الإيقاف") {
                ChoiceChips(
                    options = listOf(
                        DismissMode.QR_ANY to "أي كود QR",
                        DismissMode.QR_FIXED to "كود محدد",
                        DismissMode.MATH to "مسائل حسابية",
                    ),
                    selected = runCatching { DismissMode.valueOf(a.dismissMode) }.getOrDefault(DismissMode.QR_ANY),
                    onSelect = { m -> update { it.copy(dismissMode = m.name) } },
                )
                if (a.dismissMode == DismissMode.QR_FIXED.name) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (a.qrReferenceHash == null) "لسه مسجلتش كود. امسح الكود اللي هتحطه بعيد عن السرير (مثلا في الحمام)."
                        else "الكود متسجل ✓ (بنحفظ بصمة الكود بس، مش محتواه)",
                    )
                    OutlinedButton(onClick = {
                        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                        if (granted) scanningFixed = true else cameraLauncher.launch(Manifest.permission.CAMERA)
                    }) { Text(if (a.qrReferenceHash == null) "سجّل الكود" else "غيّر الكود") }
                }
                if (a.dismissMode == DismissMode.QR_FIXED.name && a.qrReferenceHash == null) {
                    Text("لو مسجلتش كود، أي كود QR هيوقف المنبه.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            SectionCard(title = "الصوت") {
                Text("مستوى الصوت: ${a.targetVolume}%")
                Slider(
                    value = a.targetVolume.toFloat(),
                    onValueChange = { v -> update { it.copy(targetVolume = v.toInt()) } },
                    valueRange = 10f..100f,
                )
                Text("التصاعد لحد المستوى ده: ${a.rampSeconds} ثانية")
                Slider(
                    value = a.rampSeconds.toFloat(),
                    onValueChange = { v -> update { it.copy(rampSeconds = v.toInt()) } },
                    valueRange = 0f..60f,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(enabled = !previewing, onClick = {
                        previewing = true
                        scope.launch {
                            player.start(RingConfig.from(a).copy(rampSeconds = 0, protectVolume = false), scope)
                            delay(3_000)
                            player.stop()
                            previewing = false
                        }
                    }) { Text(if (previewing) "بيرن…" else "جرّب الصوت 3 ثواني") }
                    TextButton(onClick = {
                        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, a.ringtoneUri?.let(Uri::parse))
                        ringtoneLauncher.launch(intent)
                    }) { Text(if (a.ringtoneUri == null) "النغمة: الافتراضية" else "غيّر النغمة") }
                }
                SwitchRow("اهتزاز", a.vibrationEnabled, { v -> update { it.copy(vibrationEnabled = v) } })
                SwitchRow(
                    "حماية مستوى الصوت",
                    a.protectVolume,
                    { v -> update { it.copy(protectVolume = v) } },
                    subtitle = "لو الصوت قل وقت الرنين، بنرجعه للمستوى اللي اخترته، ومش أعلى منه أبدًا.",
                )
            }
            SectionCard(title = "تأكيد الصحيان بعد المسح") {
                ChoiceChips(
                    options = listOf(3 to "3 د", 5 to "5 د", 10 to "10 د", 15 to "15 د"),
                    selected = a.verificationDelayMin,
                    onSelect = { m -> update { it.copy(verificationDelayMin = m) } },
                )
                Spacer(Modifier.height(8.dp))
                Text("عدد التذكيرات لو مردتش")
                ChoiceChips(
                    options = listOf(1 to "1", 2 to "2", 3 to "3", 5 to "5"),
                    selected = a.maxFollowUps,
                    onSelect = { m -> update { it.copy(maxFollowUps = m) } },
                )
            }
            Button(
                onClick = { scope.launch { c.alarms.save(a.copy(enabled = true)); onBack() } },
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text("حفظ") }
            if (a.repeatDays == 0) {
                Text("من غير أيام، المنبه هيرن مرة واحدة بس.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    if (pickTime) {
        TimePickerDialog(
            initial = LocalTime.of(a.hour, a.minute),
            onDismiss = { pickTime = false },
            onConfirm = { t -> update { it.copy(hour = t.hour, minute = t.minute) }; pickTime = false },
        )
    }
    if (scanningFixed) {
        Dialog(onDismissRequest = { scanningFixed = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize()) {
                QrScanner(
                    modifier = Modifier.fillMaxSize(),
                    onPayload = { payload ->
                        if (scanningFixed) {
                            update { it.copy(qrReferenceHash = QrValidator.hash(payload)) }
                            scanningFixed = false
                        }
                    },
                    onError = { scanningFixed = false },
                )
                Column(Modifier.align(Alignment.BottomCenter).padding(24.dp)) {
                    Text("وجّه الكاميرا على الكود اللي هتستخدمه", color = androidx.compose.ui.graphics.Color.White)
                    TextButton(onClick = { scanningFixed = false }) { Text("إلغاء") }
                }
            }
        }
    }
}
