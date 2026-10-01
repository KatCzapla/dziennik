package pl.usagi.edziennik.ui.attendance

import pl.usagi.edziennik.domain.AttendanceEntry
import pl.usagi.edziennik.domain.AttendanceStatus
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
    val excusesActive: Boolean? = null,
    val excusesComplete: Boolean = false,
    val submittedExcuseKeys: Set<Pair<LocalDate, Int>> = emptySet(),
    val excuseEntry: AttendanceEntry? = null,
    val excuseContent: String = "",
    val isSubmittingExcuse: Boolean = false,
    val excuseErrorMessage: String? = null,
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

    fun canSubmitExcuse(entry: AttendanceEntry): Boolean {
        val lessonTimeId = entry.lessonTimeId ?: return false
        return excusesActive == true && excusesComplete &&
            entry.status in setOf(AttendanceStatus.Absent, AttendanceStatus.Late) &&
            entry.excuseStatus == null &&
            (entry.date to lessonTimeId) !in submittedExcuseKeys &&
            weekEntries.contains(entry)
    }

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
