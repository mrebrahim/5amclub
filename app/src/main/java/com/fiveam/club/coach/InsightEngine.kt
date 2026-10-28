package com.fiveam.club.coach

import com.fiveam.club.gamification.WeeklyReport
import com.fiveam.club.util.TimeFormat

/** Where a statement comes from, shown next to it (REP-04, AI-06). */
enum class EvidenceSource(val label: String) {
    MEASURED("مقاس من المنبه"),
    USER("من تسجيلك"),
    ESTIMATED("تقدير"),
}

data class Insight(
    val title: String,
    val evidence: String,
    val recommendation: String,
    val source: EvidenceSource,
    val tipCategory: TipCategory,
)

/**
 * Local rule-based coach (AI-09). Phrased as observations, never as proof of sleep or
 * wakefulness (REP-05), and never changes alarms by itself (AI-08).
 */
object InsightEngine {
    fun analyze(report: WeeklyReport, sleepGoalMin: Int): List<Insight> {
        val out = mutableListOf<Insight>()
        report.avgSleepMin?.let { avg ->
            if (avg < sleepGoalMin - 30) {
                out += Insight(
                    title = "نومك أقل من هدفك",
                    evidence = "متوسط النوم اللي سجلته ${TimeFormat.duration(avg)} من ${report.sleepLoggedDays} أيام، وهدفك ${TimeFormat.duration(sleepGoalMin)}.",
                    recommendation = "جرّب تقدّم ميعاد السرير 15 دقيقة كل 3 أيام لحد ما توصل لهدفك، بدل ما تأخر المنبه.",
                    source = EvidenceSource.USER,
                    tipCategory = TipCategory.SLEEP,
                )
            }
        }
        report.avgDeviationMin?.let { dev ->
            if (dev > 10) {
                out += Insight(
                    title = "بتاخد وقت لحد ما تمسح الكود",
                    evidence = "في المتوسط مسحت QR بعد $dev دقيقة من ميعاد المنبه.",
                    recommendation = "حط الكود في مكان أقرب للباب وجهّز نور الأوضة يفتح بسهولة.",
                    source = EvidenceSource.MEASURED,
                    tipCategory = TipCategory.WAKING,
                )
            }
        }
        report.confirmRate?.let { rate ->
            if (rate < 0.7f) {
                out += Insight(
                    title = "تأكيد الصحيان بيفوتك",
                    evidence = "أكدت صحيانك في ${(rate * 100).toInt()}% من المرات بعد المنبه.",
                    recommendation = "بعد مسح الكود اقعد بعيد عن السرير لحد ما يجيلك سؤال التأكيد.",
                    source = EvidenceSource.MEASURED,
                    tipCategory = TipCategory.WAKING,
                )
            }
        }
        if (report.volumeKeyPresses >= 3) {
            out += Insight(
                title = "زراير الصوت اتداس عليها وقت المنبه",
                evidence = "اتسجل ${report.volumeKeyPresses} ضغطة على زراير الصوت والمنبه بيرن.",
                recommendation = "اشحن الموبايل بعيد عن إيدك، وجرّب مستوى صوت مناسب من شاشة الجاهزية.",
                source = EvidenceSource.MEASURED,
                tipCategory = TipCategory.PHONE,
            )
        }
        if (report.emergencyCount >= 2) {
            out += Insight(
                title = "إيقاف الطوارئ اتكرر",
                evidence = "استخدمت إيقاف الطوارئ ${report.emergencyCount} مرات الأسبوع ده.",
                recommendation = "لو الكاميرا أو الكود فيهم مشكلة، جرّب كود تاني أو فعّل التحدي الحسابي كبديل.",
                source = EvidenceSource.MEASURED,
                tipCategory = TipCategory.WAKING,
            )
        }
        report.routineRate?.let { rate ->
            if (rate < 0.5f) {
                out += Insight(
                    title = "الروتين طويل شوية؟",
                    evidence = "خلصت حوالي ${(rate * 100).toInt()}% من خطوات الروتين في المتوسط.",
                    recommendation = "استخدم روتين الأيام الصعبة (10 دقايق) لحد ما يبقى عادة.",
                    source = EvidenceSource.MEASURED,
                    tipCategory = TipCategory.MOTIVATION,
                )
            }
        }
        if (report.sleepLoggedDays < 3 && report.scheduledDays >= 3) {
            out += Insight(
                title = "سجّل نومك علشان نفهم أكتر",
                evidence = "سجلت نومك ${report.sleepLoggedDays} أيام بس من آخر 7.",
                recommendation = "بعد تأكيد الصحيان هنسألك سؤالين سريعين عن نومك.",
                source = EvidenceSource.USER,
                tipCategory = TipCategory.SLEEP,
            )
        }
        return out
    }

    /** REP-03: one practical weekly note. */
    fun weeklyNote(report: WeeklyReport, sleepGoalMin: Int): String =
        analyze(report, sleepGoalMin).firstOrNull()?.recommendation
            ?: if (report.onTimeDays > 0) {
                "أسبوع كويس! حافظ على نفس ميعاد النوم والصحيان."
            } else {
                "ابدأ بهدف صغير: صحيان في الميعاد يومين الأسبوع الجاي."
            }
}
