package pl.katcz.edziennik.ui.timetable

import pl.katcz.edziennik.domain.Student
import pl.katcz.edziennik.domain.TimetableEntry
import pl.katcz.edziennik.domain.time.WeekRange

data class TimetableUiState(
    val week: WeekRange = WeekRange.current(),
    val entries: List<TimetableEntry> = emptyList(),
    val students: List<Student> = emptyList(),
    val activeStudent: Student? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
) {
    val isEmpty: Boolean
        get() = !isLoading && errorMessage == null && entries.isEmpty()
}
