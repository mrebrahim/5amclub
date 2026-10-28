package com.fiveam.club.alarm.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.fiveam.club.alarm.core.AlarmState
import com.fiveam.club.alarm.core.ScanResult
import com.fiveam.club.data.db.AlarmSessionEntity
import com.fiveam.club.data.db.DismissMode
import com.fiveam.club.qr.QrScanner
import com.fiveam.club.util.TimeFormat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalTime
import kotlin.random.Random

@Composable
fun AlarmRoot(vm: AlarmViewModel, onClose: () -> Unit) {
    val session by vm.session.collectAsState()
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            val s = session
            if (!vm.resolved) return@Box
            if (s == null) {
                LaunchedEffect(Unit) { onClose() }
                return@Box
            }
            when (val state = AlarmState.of(s.state)) {
                AlarmState.FIRING, AlarmState.SCHEDULED ->
                    if (vm.dismissMode == DismissMode.MATH) {
                        MathChallenge(onSolved = vm::completeFallback, onEmergency = vm::emergency, onRetryQr = null)
                    } else {
                        FiringScreen(s, vm, onStart = vm::startScan)
                    }
                AlarmState.QR_SCANNING -> ScanScreen(vm)
                AlarmState.QR_FAILED -> MathChallenge(
                    onSolved = vm::completeFallback,
                    onEmergency = vm::emergency,
                    onRetryQr = vm::startScan,
                )
                AlarmState.QR_COMPLETED, AlarmState.AWAITING_CONFIRMATION, AlarmState.CONFIRMED ->
                    DoneScreen(s, vm, state, onClose)
                else -> LaunchedEffect(state) { onClose() }
            }
            if (AlarmState.of(s.state).isRinging) VolumeKeyHint(vm.lastVolumeKeyAt)
        }
    }
}

@Composable
private fun FiringScreen(session: AlarmSessionEntity, vm: AlarmViewModel, onStart: () -> Unit) {
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalTime.now()
            delay(10_000)
        }
    }
    // While ringing, back only leaves the screen; the alarm keeps going.
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(48.dp))
            Text(TimeFormat.time(now), style = MaterialTheme.typography.displayLarge)
            Spacer(Modifier.height(8.dp))
            Text(session.label.ifBlank { "صباح الخير" }, style = MaterialTheme.typography.titleLarge)
            vm.firstGoal?.let { goal ->
                Spacer(Modifier.height(24.dp))
                Text(
                    "إنت صاحي علشان: ${goal.title}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth().height(72.dp),
                shape = RoundedCornerShape(24.dp),
            ) {
                Icon(Icons.Filled.QrCodeScanner, contentDescription = null, modifier = Modifier.size(28.dp))
                Spacer(Modifier.size(12.dp))
                Text("امسح QR علشان توقف المنبه", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(24.dp))
            EmergencyButton(onConfirm = vm::emergency)
        }
    }
}

@Composable
private fun ScanScreen(vm: AlarmViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var message by remember { mutableStateOf<String?>(null) }
    var showFallback by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        if (!granted) vm.scanFailed("camera_permission_denied")
    }
    LaunchedEffect(Unit) {
        if (!hasPermission) launcher.launch(Manifest.permission.CAMERA)
        delay(20_000)
        showFallback = true
    }
    BackHandler { vm.cancelScan() }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth().background(Color.Black)) {
            if (hasPermission) {
                QrScanner(
                    modifier = Modifier.fillMaxSize(),
                    onPayload = { payload ->
                        scope.launch {
                            if (vm.onPayload(payload) == ScanResult.REJECTED) {
                                message = "ده مش الكود المحفوظ. دوّر على الكود بتاعك."
                            }
                        }
                    },
                    onError = { reason -> vm.scanFailed(reason) },
                )
            }
            Text(
                "وجّه الكاميرا على أي كود QR",
                modifier = Modifier.align(Alignment.TopCenter).padding(24.dp),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            message?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp))
            }
            Text(
                "المنبه هيفضل شغال لحد ما المسح ينجح.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = vm::cancelScan) { Text("رجوع") }
                if (showFallback) {
                    OutlinedButton(onClick = { vm.scanFailed("unable_to_scan") }) { Text("مش قادر أمسح؟ استخدم البديل") }
                }
            }
        }
    }
}

private data class MathProblem(val a: Int, val b: Int) {
    val answer get() = a + b
}

/** Safe fallback dismissal (QR-09): three short additions. */
@Composable
private fun MathChallenge(onSolved: () -> Unit, onEmergency: (String) -> Unit, onRetryQr: (() -> Unit)?) {
    val problems = remember { List(3) { MathProblem(Random.nextInt(12, 60), Random.nextInt(12, 40)) } }
    var index by remember { mutableIntStateOf(0) }
    var input by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    val p = problems[index]
    Column(
        Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(32.dp))
        Text("حل ${problems.size} مسائل صغيرة علشان توقف المنبه", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text("مسألة ${index + 1} من ${problems.size}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(32.dp))
        Text("${p.a} + ${p.b} = ؟", style = MaterialTheme.typography.displayLarge.copy(fontSize = MaterialTheme.typography.headlineLarge.fontSize * 1.6f))
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = input,
            onValueChange = { v -> input = v.filter(Char::isDigit).take(4); wrong = false },
            label = { Text("الإجابة") },
            singleLine = true,
            isError = wrong,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        if (wrong) Text("حاول تاني", color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                if (input.toIntOrNull() == p.answer) {
                    input = ""
                    if (index == problems.lastIndex) onSolved() else index++
                } else {
                    wrong = true
                }
            },
            modifier = Modifier.fillMaxWidth().height(64.dp),
        ) { Text("تأكيد", style = MaterialTheme.typography.titleMedium) }
        Spacer(Modifier.height(16.dp))
        if (onRetryQr != null) TextButton(onClick = onRetryQr) { Text("جرّب QR تاني") }
        Spacer(Modifier.height(16.dp))
        EmergencyButton(onConfirm = onEmergency)
    }
}

private val emergencyReasons = listOf(
    "camera_broken" to "الكاميرا مش شغالة",
    "qr_missing" to "مش لاقي الكود",
    "urgent" to "ظرف طارئ",
    "other" to "سبب تاني",
)

/** QR-10: always available, but needs a reason and a short wait to avoid half-asleep taps. */
@Composable
private fun EmergencyButton(onConfirm: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    TextButton(onClick = { open = true }) {
        Text("إيقاف طوارئ", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (!open) return
    var reason by remember { mutableStateOf(emergencyReasons.first().first) }
    var countdown by remember { mutableIntStateOf(5) }
    LaunchedEffect(Unit) {
        while (countdown > 0) {
            delay(1_000)
            countdown--
        }
    }
    AlertDialog(
        onDismissRequest = { open = false },
        title = { Text("إيقاف المنبه من غير QR؟") },
        text = {
            Column {
                Text("هيتسجل إن المنبه اتوقف بإيقاف الطوارئ، ومش هيتحسب صحيان في الميعاد.")
                Spacer(Modifier.height(12.dp))
                emergencyReasons.forEach { (key, label) ->
                    Row(
                        Modifier.fillMaxWidth().selectable(selected = reason == key, onClick = { reason = key }, role = Role.RadioButton)
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = reason == key, onClick = null)
                        Spacer(Modifier.size(8.dp))
                        Text(label)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { open = false; onConfirm(reason) }, enabled = countdown == 0) {
                Text(if (countdown > 0) "استنى $countdown" else "إيقاف")
            }
        },
        dismissButton = { TextButton(onClick = { open = false }) { Text("رجوع") } },
    )
}

@Composable
private fun DoneScreen(session: AlarmSessionEntity, vm: AlarmViewModel, state: AlarmState, onClose: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("☀️", style = MaterialTheme.typography.displayLarge)
        Spacer(Modifier.height(16.dp))
        if (session.isTest) {
            Text("الاختبار نجح ✅", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                "الصوت والشاشة والكاميرا والمسح اشتغلوا. ارجع للتطبيق علشان تشوف النتيجة.",
                textAlign = TextAlign.Center,
            )
        } else {
            Text("برافو! المنبه وقف", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(8.dp))
            val text = if (state == AlarmState.CONFIRMED) {
                "صحيانك اتسجل."
            } else {
                "هنسألك بعد ${session.verificationDelayMin} دقايق إنك صاحي. خليك بعيد عن السرير 😉"
            }
            Text(text, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
            vm.firstGoal?.let { goal ->
                Spacer(Modifier.height(24.dp))
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(16.dp).fillMaxWidth()) {
                        Text("أول هدف النهارده", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Text(goal.title, style = MaterialTheme.typography.titleLarge)
                        if (goal.motivationReason.isNotBlank()) Text(goal.motivationReason)
                    }
                }
            }
        }
        Spacer(Modifier.height(32.dp))
        Button(onClick = onClose, modifier = Modifier.fillMaxWidth().height(60.dp)) { Text("تمام") }
    }
}

@Composable
private fun VolumeKeyHint(lastAt: Long) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(lastAt) {
        if (lastAt == 0L) return@LaunchedEffect
        visible = true
        delay(3_000)
        visible = false
    }
    if (!visible) return
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.BottomCenter) {
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
            Text(
                "زراير الصوت مش بتوقف المنبه. امسح الكود ☝️",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}
