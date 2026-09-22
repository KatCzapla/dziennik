package pl.usagi.edziennik.ui.grades

import pl.usagi.edziennik.domain.ClassificationPeriod
import pl.usagi.edziennik.domain.Grades
import pl.usagi.edziennik.domain.Student

data class GradesUiState(
    val periods: List<ClassificationPeriod> = emptyList(),
    val selectedPeriod: ClassificationPeriod? = null,
    val grades: Grades? = null,
    val students: List<Student> = emptyList(),
    val activeStudent: Student? = null,
    val isLoadingPeriods: Boolean = false,
    val isLoadingGrades: Boolean = false,
    val errorMessage: String? = null,
) {
    val isLoading: Boolean
        get() = isLoadingPeriods || isLoadingGrades
}
