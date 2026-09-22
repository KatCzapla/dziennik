package pl.usagi.edziennik.ui.timetable

import pl.usagi.edziennik.domain.TimetableChange
import pl.usagi.edziennik.domain.TimetableEntry
import pl.usagi.edziennik.domain.time.SchoolYearRange
import pl.usagi.edziennik.domain.time.WeekRange
import pl.usagi.edziennik.domain.time.coerceInSchoolYear
import pl.usagi.edziennik.domain.time.currentSchoolYearRange
import pl.usagi.edziennik.domain.time.nextWeekday
import pl.usagi.edziennik.domain.time.previousWeekday
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

fun WeekRange.schoolDays(): List<LocalDate> =
    (0L..4L).map { start.plusDays(it) }

fun WeekRange.schoolWeekLabel(): String {
    val start = start
    val end = schoolDays().last()

    return when {
        start.year != end.year -> "${start.format(dayMonthYearFormatter)} – ${end.format(dayMonthYearFormatter)}"
        start.month != end.month -> "${start.format(dayMonthFormatter)} – ${end.format(dayMonthFormatter)}"
        else -> "${start.dayOfMonth}–${end.format(dayMonthFormatter)}"
    }
}

fun TimetableEntry.displayLessonNumber(): Int? =
    lessonNumber ?: changes.firstNotNullOfOrNull { it.lessonNumber }

fun TimetableChange.readableNote(): String? =
    if (isTeacherAbsence) {
        listOfNotNull(
            "Nieobecny nauczyciel wspomagający",
            absenceEffect?.let { "Skutek nieobecności: $it" },
        ).joinToString("\n")
    } else {
        listOfNotNull(
            subject?.let { "zmieniony przedmiot: $it" },
            room?.let { "sala $it" },
            teacher?.let { "prowadzi $it" },
            absenceInformation,
        ).joinToString(" · ").ifBlank { null }
    }

fun initialTimetableDate(
    today: LocalDate = LocalDate.now(),
    schoolYearRange: SchoolYearRange = currentSchoolYearRange(today),
): LocalDate {
    val effectiveDate = when (today.dayOfWeek) {
        DayOfWeek.SATURDAY -> today.plusDays(2)
        DayOfWeek.SUNDAY -> today.plusDays(1)
        else -> today
    }
    return effectiveDate.coerceInSchoolYear(schoolYearRange)
}

fun nextSchoolDate(date: LocalDate): LocalDate =
    date.nextWeekday()

fun previousSchoolDate(date: LocalDate): LocalDate =
    date.previousWeekday()

fun schoolDateAtOffset(anchor: LocalDate, offset: Int): LocalDate {
    var date = initialTimetableDate(anchor)
    repeat(kotlin.math.abs(offset)) {
        date = if (offset >= 0) nextSchoolDate(date) else previousSchoolDate(date)
    }
    return date
}

fun schoolDateOffset(from: LocalDate, to: LocalDate): Int {
    val normalizedFrom = initialTimetableDate(from)
    val normalizedTo = initialTimetableDate(to)
    if (normalizedFrom == normalizedTo) return 0

    var date = normalizedFrom
    var offset = 0
    if (normalizedTo.isAfter(normalizedFrom)) {
        while (date != normalizedTo) {
            date = nextSchoolDate(date)
            offset += 1
        }
    } else {
        while (date != normalizedTo) {
            date = previousSchoolDate(date)
            offset -= 1
        }
    }
    return offset
}

fun LocalDate.dayMonthLabel(): String =
    format(dayMonthFormatter)

fun DayOfWeek.polishName(): String =
    when (this) {
        DayOfWeek.MONDAY -> "Poniedziałek"
        DayOfWeek.TUESDAY -> "Wtorek"
        DayOfWeek.WEDNESDAY -> "Środa"
        DayOfWeek.THURSDAY -> "Czwartek"
        DayOfWeek.FRIDAY -> "Piątek"
        DayOfWeek.SATURDAY -> "Sobota"
        DayOfWeek.SUNDAY -> "Niedziela"
    }

private val polishLocale = Locale.forLanguageTag("pl-PL")
private val dayMonthFormatter = DateTimeFormatter.ofPattern("d MMMM", polishLocale)
private val dayMonthYearFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", polishLocale)
