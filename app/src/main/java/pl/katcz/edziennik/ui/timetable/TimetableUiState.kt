package pl.katcz.edziennik.ui.timetable

import pl.katcz.edziennik.domain.Student
import pl.katcz.edziennik.domain.TimetableEntry
import pl.katcz.edziennik.domain.time.WeekRange
import java.time.LocalDate

data class TimetableUiState(
    val selectedDate: LocalDate = initialTimetableDate(),
    val entries: List<TimetableEntry> = emptyList(),
    val students: List<Student> = emptyList(),
    val activeStudent: Student? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
) {
    val displayedWeek: WeekRange
        get() = WeekRange.fromDate(selectedDate)

    val selectedDayEntries: List<TimetableEntry>
        get() = entries.filter { it.date == selectedDate }

    val isEmpty: Boolean
        get() = !isLoading && errorMessage == null && selectedDayEntries.isEmpty()
}
