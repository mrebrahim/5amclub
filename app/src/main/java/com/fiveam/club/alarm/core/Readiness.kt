package com.fiveam.club.alarm.core

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.fiveam.club.alarm.audio.AlarmPlayer
import com.fiveam.club.util.ExactAlarms
import com.fiveam.club.util.Notifications

enum class Severity { REQUIRED, RECOMMENDED }

data class ReadinessItem(
    val key: String,
    val title: String,
    val why: String,
    val ok: Boolean,
    val severity: Severity,
    /** Runtime permission to request in-app, if any. */
    val permission: String? = null,
    /** Settings screen to open otherwise. */
    val settingsIntent: Intent? = null,
)

/** UX-06: report the real state of everything the alarm depends on, never an optimistic one. */
object Readiness {
    fun check(context: Context): List<ReadinessItem> {
        val pkg = Uri.parse("package:${context.packageName}")
        val appDetails = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)
        val items = mutableListOf<ReadinessItem>()

        items += ReadinessItem(
            key = "notifications",
            title = "الإشعارات",
            why = "علشان شاشة المنبه تظهر، وتوصلك أسئلة تأكيد الصحيان وتذكيرات المهام.",
            ok = Notifications.enabled(context),
            severity = Severity.REQUIRED,
            permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.POST_NOTIFICATIONS else null,
            settingsIntent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
        )
        items += ReadinessItem(
            key = "exact_alarm",
            title = "المنبهات الدقيقة",
            why = "من غيرها النظام ممكن يأخر المنبه. لازمة علشان يرن في ميعاده بالظبط.",
            ok = ExactAlarms.canScheduleExact(context),
            severity = Severity.REQUIRED,
            settingsIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg)
            } else {
                appDetails
            },
        )
        if (Build.VERSION.SDK_INT >= 34) {
            val nm = context.getSystemService(NotificationManager::class.java)
            items += ReadinessItem(
                key = "full_screen",
                title = "شاشة المنبه الكاملة",
                why = "علشان شاشة المنبه تفتح فوق شاشة القفل بدل إشعار صغير.",
                ok = nm.canUseFullScreenIntent(),
                severity = Severity.REQUIRED,
                settingsIntent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkg),
            )
        }
        items += ReadinessItem(
            key = "camera",
            title = "الكاميرا",
            why = "لمسح كود QR وإيقاف المنبه. الصور مش بتتحفظ ولا بتتبعت لأي مكان.",
            ok = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
            severity = Severity.REQUIRED,
            permission = Manifest.permission.CAMERA,
        )
        val pm = context.getSystemService(PowerManager::class.java)
        items += ReadinessItem(
            key = "battery",
            title = "استثناء توفير البطارية",
            why = "بعض الأجهزة (Xiaomi وOPPO وSamsung) بتقفل التطبيقات في الخلفية. الاستثناء بيقلل المشاكل دي.",
            ok = pm.isIgnoringBatteryOptimizations(context.packageName),
            severity = Severity.RECOMMENDED,
            settingsIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
        )
        val volume = AlarmPlayer.alarmVolumePercent(context)
        items += ReadinessItem(
            key = "volume",
            title = "صوت المنبهات في الجهاز: $volume%",
            why = "التطبيق بيظبط الصوت على المستوى اللي اخترته وقت الرنين، بس بعض الأجهزة أو وضع عدم الإزعاج ممكن يمنع ده.",
            ok = volume >= 30,
            severity = Severity.RECOMMENDED,
            settingsIntent = Intent(Settings.ACTION_SOUND_SETTINGS),
        )
        return items
    }

    fun requiredOk(items: List<ReadinessItem>) = items.filter { it.severity == Severity.REQUIRED }.all { it.ok }
}
