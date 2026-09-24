package pl.usagi.edziennik.ui.attendance

import pl.usagi.edziennik.domain.AttendanceStatus
import pl.usagi.edziennik.domain.time.SchoolYearRange
import pl.usagi.edziennik.domain.time.coerceInSchoolYear
import pl.usagi.edziennik.domain.time.currentSchoolYearRange
import pl.usagi.edziennik.ui.timetable.nextSchoolDate
import pl.usagi.edziennik.ui.timetable.previousSchoolDate
import java.time.DayOfWeek
import java.time.LocalDate

fun AttendanceStatus.label(): String =
    when (this) {
        AttendanceStatus.Present -> "Obecność"
        AttendanceStatus.Absent -> "Nieobecność"
        AttendanceStatus.ExcusedAbsence -> "Nieobecność usprawiedliwiona"
        AttendanceStatus.Late -> "Spóźnienie"
        AttendanceStatus.ExcusedLate -> "Spóźnienie usprawiedliwione"
        AttendanceStatus.SchoolRelatedAbsence -> "Nieobecność z przyczyn szkolnych"
        AttendanceStatus.Released -> "Zwolnienie"
        is AttendanceStatus.Unknown -> description ?: rawValue?.let { "Status: $it" }
            ?: "Status nieznany"
    }

fun initialAttendanceDate(
    today: LocalDate = LocalDate.now(),
    schoolYearRange: SchoolYearRange = currentSchoolYearRange(today),
): LocalDate {
    val effectiveDate = when (today.dayOfWeek) {
        DayOfWeek.SATURDAY -> today.minusDays(1)
        DayOfWeek.SUNDAY -> today.minusDays(2)
        else -> today
    }
    return effectiveDate.coerceInSchoolYear(schoolYearRange)
}

fun attendanceDateAtOffset(anchor: LocalDate, offset: Int): LocalDate {
    var date = initialAttendanceDate(anchor)
    repeat(kotlin.math.abs(offset)) {
        date = if (offset >= 0) nextSchoolDate(date) else previousSchoolDate(date)
    }
    return date
}

fun attendanceDateOffset(from: LocalDate, to: LocalDate): Int {
    val normalizedFrom = initialAttendanceDate(from)
    val normalizedTo = initialAttendanceDate(to)
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
