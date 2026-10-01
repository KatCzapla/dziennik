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

sealed interface ExcuseOption {
    val date: LocalDate
    val lessonTimeId: Int?

    data class Lesson(val entry: AttendanceEntry) : ExcuseOption {
        override val date: LocalDate get() = entry.date
        override val lessonTimeId: Int? get() = entry.lessonTimeId
    }

    data class WholeDay(override val date: LocalDate) : ExcuseOption {
        override val lessonTimeId: Int? = null
    }
}

data class AttendanceUiState(
    val schoolYearRange: SchoolYearRange = currentSchoolYearRange(),
    val latestSelectableDate: LocalDate = initialAttendanceDate(schoolYearRange = schoolYearRange),
    val selectedDate: LocalDate = latestSelectableDate,
    val today: LocalDate = LocalDate.now(),
    val weekEntries: List<AttendanceEntry> = emptyList(),
    val students: List<Student> = emptyList(),
    val activeStudent: Student? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val excusesActive: Boolean? = null,
    val excusesComplete: Boolean = false,
    val submittedExcuseKeys: Set<Pair<LocalDate, Int?>> = emptySet(),
    val excuseFormDate: LocalDate? = null,
    val selectedExcuseOption: ExcuseOption? = null,
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

    val availableExcuseOptions: List<ExcuseOption>
        get() {
            if (excusesActive != true || !excusesComplete || isLoading || errorMessage != null) return emptyList()
            val lessons = selectedDayEntries.filter(::canSubmitExcuse).map(ExcuseOption::Lesson)
            val wholeDayAvailable = submittedExcuseKeys.none { it.first == selectedDate } &&
                selectedDayEntries.all { it.excuseStatus == null && it.status in EXCUSABLE_STATUSES } &&
                (selectedDate.isAfter(today) || lessons.isNotEmpty())
            return if (wholeDayAvailable) lessons + ExcuseOption.WholeDay(selectedDate) else lessons
        }

    val showHeaderExcuseButton: Boolean
        get() = view == AttendanceView.ENTRIES && availableExcuseOptions.isNotEmpty()

    fun canSubmitExcuse(entry: AttendanceEntry): Boolean {
        val lessonTimeId = entry.lessonTimeId ?: return false
        return excusesActive == true && excusesComplete &&
            entry.status in EXCUSABLE_STATUSES &&
            entry.excuseStatus == null &&
            (entry.date to lessonTimeId) !in submittedExcuseKeys &&
            (entry.date to null) !in submittedExcuseKeys &&
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

    private companion object {
        val EXCUSABLE_STATUSES = setOf(AttendanceStatus.Absent, AttendanceStatus.Late)
    }
}
