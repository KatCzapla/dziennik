package pl.usagi.edziennik.ui.timetable

import pl.usagi.edziennik.domain.Student
import pl.usagi.edziennik.domain.TimetableEntry
import pl.usagi.edziennik.domain.SchoolDayOff
import pl.usagi.edziennik.domain.forDay
import pl.usagi.edziennik.domain.time.SchoolYearRange
import pl.usagi.edziennik.domain.time.WeekRange
import pl.usagi.edziennik.domain.time.currentSchoolYearRange
import pl.usagi.edziennik.domain.time.intersectsSchoolYear
import java.time.LocalDate

data class TimetableUiState(
    val schoolYearRange: SchoolYearRange = currentSchoolYearRange(),
    val selectedDate: LocalDate = initialTimetableDate(schoolYearRange = schoolYearRange),
    val entries: List<TimetableEntry> = emptyList(),
    val students: List<Student> = emptyList(),
    val activeStudent: Student? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val daysOff: List<SchoolDayOff> = emptyList(),
    val daysOffLoading: Boolean = false,
    val daysOffFailed: Boolean = false,
) {
    fun daysOffFor(date: LocalDate): List<SchoolDayOff> = daysOff.forDay(date, activeStudent?.organizationalUnitId)

    val showNoLessons: Boolean
        get() = showNoLessonsFor(selectedDate)

    fun showNoLessonsFor(date: LocalDate): Boolean =
        !isLoading && errorMessage == null && entries.none { it.date == date } &&
            !daysOffLoading && daysOffFor(date).isEmpty()

    val displayedWeek: WeekRange
        get() = WeekRange.fromDate(selectedDate)

    val selectedDayEntries: List<TimetableEntry>
        get() = entries.filter { it.date == selectedDate }

    val isEmpty: Boolean
        get() = !isLoading && errorMessage == null && selectedDayEntries.isEmpty()

    val canGoPreviousWeek: Boolean
        get() = displayedWeek.previous().intersectsSchoolYear(schoolYearRange)

    val canGoNextWeek: Boolean
        get() = displayedWeek.next().intersectsSchoolYear(schoolYearRange)
}
