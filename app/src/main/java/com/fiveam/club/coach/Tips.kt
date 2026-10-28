package com.fiveam.club.coach

import java.time.LocalDate

enum class TipCategory(val label: String) {
    SLEEP("النوم"),
    WAKING("الصحيان"),
    TIME("تنظيم الوقت"),
    MOTIVATION("الدافع"),
    FOCUS("التركيز"),
    PHONE("الموبايل"),
}

data class Tip(val id: Int, val category: TipCategory, val text: String)

data class Challenge(val id: Int, val text: String)

/**
 * Reviewed, non-medical tip library (TIP-01, TIP-05). General habit guidance only;
 * no claims about diagnosing or treating sleep conditions.
 */
object TipsLibrary {
    val tips = listOf(
        Tip(1, TipCategory.SLEEP, "ثبّت ميعاد نومك حتى في الإجازة، فرق ساعة واحدة كفاية يلخبط صحيانك."),
        Tip(2, TipCategory.SLEEP, "خلي آخر كوباية قهوة أو شاي قبل العصر، الكافيين بيفضل في جسمك ساعات."),
        Tip(3, TipCategory.SLEEP, "قبل النوم بساعة وطّي الإضاءة في البيت، ده بيساعد جسمك يفهم إن اليوم خلص."),
        Tip(4, TipCategory.SLEEP, "لو مش جايلك نوم بعد ربع ساعة، قوم اعمل حاجة هادية في إضاءة خفيفة وارجع تاني."),
        Tip(5, TipCategory.SLEEP, "أوضة باردة شوية ومظلمة وهادية بتفرق جدًا في جودة النوم."),
        Tip(6, TipCategory.SLEEP, "عايز تصحى بدري؟ قدّم ميعاد نومك ربع ساعة كل كام يوم بدل ما تقلب كل حاجة مرة واحدة."),
        Tip(7, TipCategory.WAKING, "حط كود الـQR في مكان بعيد عن السرير، زي الحمام أو المطبخ."),
        Tip(8, TipCategory.WAKING, "أول ما تصحى افتح النور أو الشباك، النور بيقلل خمول الصبح."),
        Tip(9, TipCategory.WAKING, "اشرب كوباية مية أول ما تقوم، حاجة بسيطة بتفوّقك."),
        Tip(10, TipCategory.WAKING, "جرّب صوت المنبه بالليل قبل ما تنام، اتأكد إنه مسموع ومش مزعج زيادة."),
        Tip(11, TipCategory.WAKING, "أول 5 دقايق بعد المنبه هي الأصعب. خطط لحاجة واحدة سهلة تعملها فيهم."),
        Tip(12, TipCategory.TIME, "اكتب أهم 3 حاجات لبكرة قبل ما تنام، الصبح هتبدأ على طول."),
        Tip(13, TipCategory.TIME, "ابدأ بأصعب مهمة في أول ساعة صحيان، وقت الهدوء ده كنز."),
        Tip(14, TipCategory.TIME, "قسّم الشغل لقطع 25 دقيقة مع راحة قصيرة."),
        Tip(15, TipCategory.TIME, "حدد وقت للمهمة مش بس يوم، المهام اللي ليها ميعاد بتتعمل أكتر."),
        Tip(16, TipCategory.MOTIVATION, "افتكر إنت بتصحى بدري ليه. السبب الواضح أقوى من أي منبه."),
        Tip(17, TipCategory.MOTIVATION, "يوم وحش مش معناه إنك فشلت. المهم ترجع بكرة."),
        Tip(18, TipCategory.MOTIVATION, "احتفل بالإنجازات الصغيرة، 3 أيام متواصلين بداية حلوة."),
        Tip(19, TipCategory.MOTIVATION, "الاستمرار أهم من الكمال. روتين 10 دقايق أحسن من ولا حاجة."),
        Tip(20, TipCategory.FOCUS, "اقفل إشعارات الموبايل في أول ساعة من يومك."),
        Tip(21, TipCategory.FOCUS, "جهّز مكان الشغل بالليل علشان الصبح تقعد وتبدأ."),
        Tip(22, TipCategory.FOCUS, "حاجة واحدة في المرة. التنقل بين المهام بيستهلك طاقتك."),
        Tip(23, TipCategory.PHONE, "اشحن الموبايل بعيد عن السرير، هتقوم علشان تقفل المنبه وده المطلوب."),
        Tip(24, TipCategory.PHONE, "متفتحش السوشيال ميديا أول ما تصحى، خليها بعد الروتين."),
        Tip(25, TipCategory.PHONE, "استخدم وضع النوم في الموبايل علشان يقلل الإشعارات بالليل."),
        Tip(26, TipCategory.PHONE, "آخر نص ساعة قبل النوم من غير شاشات، جرب كتاب أو ترتيب حاجات بكرة."),
    )

    val challenges = listOf(
        Challenge(1, "جهّز أهداف بكرة قبل الساعة 10 بالليل"),
        Challenge(2, "ابعد الموبايل عن السرير النهارده"),
        Challenge(3, "خفّض الإضاءة قبل النوم بساعة"),
        Challenge(4, "ابدأ الصبح من غير سوشيال ميديا لحد ما تخلص الروتين"),
        Challenge(5, "اشرب كوباية مية أول ما تصحى"),
        Challenge(6, "جرّب صوت المنبه قبل ما تنام"),
        Challenge(7, "اخرج في النور 10 دقايق الصبح"),
        Challenge(8, "خلّص أهم مهمة قبل الساعة 9 الصبح"),
    )

    fun tipOfDay(date: LocalDate, preferred: TipCategory?): Tip {
        val pool = preferred?.let { c -> tips.filter { it.category == c } }?.takeIf { it.isNotEmpty() } ?: tips
        return pool[Math.floorMod(date.toEpochDay(), pool.size.toLong()).toInt()]
    }

    fun challengeOfDay(date: LocalDate): Challenge =
        challenges[Math.floorMod(date.toEpochDay() * 3, challenges.size.toLong()).toInt()]
}

/** MOT-06: short motivational lines that don't repeat back-to-back. */
object Motivation {
    private val lines = listOf(
        "صباح الإنجاز! أول خطوة أصعب خطوة، وإنت عملتها.",
        "النهارده يوم جديد لهدفك.",
        "الهدوء ده بتاعك. استغله في اللي يفرق معاك.",
        "كل صبح بتصحى فيه بتقرّب من النسخة اللي عايزها من نفسك.",
        "ابدأ صغير، بس ابدأ دلوقتي.",
        "إنت قلت هتصحى علشان سبب. السبب ده مستنيك.",
    )

    fun pick(lastIndex: Int, seed: Long): Pair<Int, String> {
        var i = Math.floorMod(seed, lines.size.toLong()).toInt()
        if (i == lastIndex) i = (i + 1) % lines.size
        return i to lines[i]
    }

    fun withGoal(base: String, reason: String?): String =
        if (reason.isNullOrBlank()) base else "$base\nافتكر: $reason"
}
