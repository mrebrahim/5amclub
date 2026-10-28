package com.fiveam.club.util

import java.time.DayOfWeek

/** Weekday bitmask helpers. Bit 0 = Monday ... bit 6 = Sunday (ISO order). */
object WeekDays {
    const val ALL = 0b1111111

    fun bit(day: DayOfWeek): Int = 1 shl (day.value - 1)

    fun contains(mask: Int, day: DayOfWeek): Boolean = mask and bit(day) != 0

    fun toggle(mask: Int, day: DayOfWeek): Int = mask xor bit(day)

    /** Display order for Arabic-speaking users: week starts on Saturday. */
    val displayOrder: List<DayOfWeek> = listOf(
        DayOfWeek.SATURDAY, DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
    )

    fun shortName(day: DayOfWeek): String = when (day) {
        DayOfWeek.SATURDAY -> "سبت"
        DayOfWeek.SUNDAY -> "حد"
        DayOfWeek.MONDAY -> "اتنين"
        DayOfWeek.TUESDAY -> "تلات"
        DayOfWeek.WEDNESDAY -> "أربع"
        DayOfWeek.THURSDAY -> "خميس"
        DayOfWeek.FRIDAY -> "جمعة"
    }

    fun describe(mask: Int): String = when {
        mask == 0 -> "مرة واحدة"
        mask == ALL -> "كل يوم"
        else -> displayOrder.filter { contains(mask, it) }.joinToString("، ") { shortName(it) }
    }
}
