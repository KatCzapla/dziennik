package pl.usagi.edziennik.ui.attendance

import pl.usagi.edziennik.domain.AttendanceEntry
import pl.usagi.edziennik.domain.AttendanceStatistics
import pl.usagi.edziennik.domain.AttendanceSubject
import pl.usagi.edziennik.domain.Student
import pl.usagi.edziennik.domain.time.SchoolYearRange
import pl.usagi.edziennik.domain.time.WeekRange
import pl.usagi.edziennik.domain.time.currentSchoolYearRange
import pl.usagi.edziennik.domain.time.intersectsSchoolYear
import java.time.LocalDate

enum class AttendanceView { ENTRIES, STATISTICS }

data class AttendanceUiState(
    val schoolYearRange: SchoolYearRange = currentSchoolYearRange(),
    val latestSelectableDate: LocalDate = initialAttendanceDate(schoolYearRange = schoolYearRange),
    val selectedDate: LocalDate = latestSelectableDate,
    val weekEntries: List<AttendanceEntry> = emptyList(),
    val students: List<Student> = emptyList(),
    val activeStudent: Student? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val view: AttendanceView = AttendanceView.ENTRIES,
    val subjects: List<AttendanceSubject> = emptyList(),
    val selectedSubjectId: Int = -1,
    val statistics: AttendanceStatistics? = null,
    val isStatisticsLoading: Boolean = false,
    val statisticsErrorMessage: String? = null,
) {
    val displayedWeek: WeekRange
        get() = WeekRange.fromDate(selectedDate)

    val selectedDayEntries: List<AttendanceEntry>
        get() = weekEntries.filter { it.date == selectedDate }

    val isEmpty: Boolean
        get() = !isLoading && errorMessage == null && selectedDayEntries.isEmpty()

    val canGoPreviousWeek: Boolean
        get() = displayedWeek.previous().intersectsSchoolYear(schoolYearRange)

    val canGoNextWeek: Boolean
        get() {
            val targetWeek = displayedWeek.next()
            val latestWeek = WeekRange.fromDate(latestSelectableDate)
            return targetWeek.intersectsSchoolYear(schoolYearRange) &&
                !targetWeek.start.isAfter(latestWeek.start)
        }
}
