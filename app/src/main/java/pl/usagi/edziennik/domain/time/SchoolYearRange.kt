package pl.usagi.edziennik.domain.time

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month

data class SchoolYearRange(
    val start: LocalDate,
    val end: LocalDate,
)

fun currentSchoolYearRange(today: LocalDate = LocalDate.now()): SchoolYearRange {
    val startYear = when (today.month) {
        Month.SEPTEMBER,
        Month.OCTOBER,
        Month.NOVEMBER,
        Month.DECEMBER,
        -> today.year

        else -> today.year - 1
    }

    return SchoolYearRange(
        start = LocalDate.of(startYear, Month.SEPTEMBER, 1),
        end = LocalDate.of(startYear + 1, Month.JUNE, 30),
    )
}

fun LocalDate.isWeekday(): Boolean =
    dayOfWeek in DayOfWeek.MONDAY..DayOfWeek.FRIDAY

fun LocalDate.isSelectableSchoolDate(schoolYearRange: SchoolYearRange): Boolean =
    !isBefore(schoolYearRange.start) && !isAfter(schoolYearRange.end) && isWeekday()

fun firstSelectableSchoolDate(schoolYearRange: SchoolYearRange): LocalDate =
    generateSequence(schoolYearRange.start) { it.plusDays(1) }
        .first { it.isSelectableSchoolDate(schoolYearRange) }

fun lastSelectableSchoolDate(schoolYearRange: SchoolYearRange): LocalDate =
    generateSequence(schoolYearRange.end) { it.minusDays(1) }
        .first { it.isSelectableSchoolDate(schoolYearRange) }

fun LocalDate.coerceInSchoolYear(schoolYearRange: SchoolYearRange): LocalDate =
    when {
        isBefore(schoolYearRange.start) -> firstSelectableSchoolDate(schoolYearRange)
        isAfter(schoolYearRange.end) -> lastSelectableSchoolDate(schoolYearRange)
        isSelectableSchoolDate(schoolYearRange) -> this
        else -> generateSequence(this) { it.plusDays(1) }
            .firstOrNull { it.isSelectableSchoolDate(schoolYearRange) }
            ?: lastSelectableSchoolDate(schoolYearRange)
    }

fun WeekRange.intersectsSchoolYear(schoolYearRange: SchoolYearRange): Boolean {
    val schoolWeekEnd = start.plusDays(4)
    return !schoolWeekEnd.isBefore(schoolYearRange.start) && !start.isAfter(schoolYearRange.end)
}

fun WeekRange.clampedSchoolWeekStart(schoolYearRange: SchoolYearRange): LocalDate =
    maxOf(start, schoolYearRange.start)

fun WeekRange.clampedSchoolWeekEnd(schoolYearRange: SchoolYearRange): LocalDate =
    minOf(start.plusDays(4), schoolYearRange.end)

fun selectableSchoolDayCount(
    from: LocalDate,
    to: LocalDate,
): Int {
    if (to.isBefore(from)) return 0
    return generateSequence(from) { it.plusDays(1) }
        .takeWhile { !it.isAfter(to) }
        .count { it.isWeekday() }
}

fun selectableSchoolDateAt(
    firstDate: LocalDate,
    offset: Int,
): LocalDate {
    require(firstDate.isWeekday()) { "firstDate must be a weekday." }
    require(offset >= 0) { "offset must not be negative." }

    var date = firstDate
    repeat(offset) {
        date = date.nextWeekday()
    }
    return date
}

fun selectableSchoolDateIndex(
    firstDate: LocalDate,
    date: LocalDate,
): Int? {
    if (!firstDate.isWeekday() || !date.isWeekday() || date.isBefore(firstDate)) return null

    var current = firstDate
    var index = 0
    while (current.isBefore(date)) {
        current = current.nextWeekday()
        index += 1
    }
    return if (current == date) index else null
}

fun LocalDate.nextWeekday(): LocalDate =
    when (dayOfWeek) {
        DayOfWeek.FRIDAY -> plusDays(3)
        DayOfWeek.SATURDAY -> plusDays(2)
        else -> plusDays(1)
    }

fun LocalDate.previousWeekday(): LocalDate =
    when (dayOfWeek) {
        DayOfWeek.MONDAY -> minusDays(3)
        DayOfWeek.SUNDAY -> minusDays(2)
        else -> minusDays(1)
    }
