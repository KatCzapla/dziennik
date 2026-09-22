package pl.usagi.edziennik.ui.schooltasks

import pl.usagi.edziennik.domain.SchoolTask
import pl.usagi.edziennik.domain.Student
import pl.usagi.edziennik.domain.time.SchoolYearRange
import pl.usagi.edziennik.domain.time.WeekRange
import pl.usagi.edziennik.domain.time.currentSchoolYearRange
import pl.usagi.edziennik.domain.time.intersectsSchoolYear
import java.time.LocalDate

data class SchoolTasksUiState(
    val students: List<Student> = emptyList(),
    val activeStudent: Student? = null,
    val schoolYearRange: SchoolYearRange = currentSchoolYearRange(),
    val selectedDate: LocalDate = initialSchoolTasksDate(schoolYearRange = schoolYearRange),
    val weekTasks: List<SchoolTask> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
) {
    val displayedWeek: WeekRange = WeekRange.fromDate(selectedDate)
    val dayGroups: List<SchoolTasksDayGroup> = weekTasks.groupBySchoolDay()
    val isEmpty: Boolean = weekTasks.isEmpty() && !isLoading && errorMessage == null
    val canGoPreviousWeek: Boolean = displayedWeek.previous().intersectsSchoolYear(schoolYearRange)
    val canGoNextWeek: Boolean = displayedWeek.next().intersectsSchoolYear(schoolYearRange)
}
