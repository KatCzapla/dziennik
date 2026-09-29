package pl.usagi.edziennik.ui.dashboard

import pl.usagi.edziennik.domain.Student
import pl.usagi.edziennik.domain.DashboardInformation
import java.time.LocalDate

data class DashboardUiState(
    val students: List<Student> = emptyList(),
    val activeStudent: Student? = null,
    val today: LocalDate = LocalDate.now(),
    val selectedTimetableDay: DashboardTimetableDay = DashboardTimetableDay.Today,
    val todayTimetable: DashboardSection<List<DashboardLesson>> = DashboardSection(),
    val tomorrowTimetable: DashboardSection<List<DashboardLesson>> = DashboardSection(),
    val todayAttendance: DashboardSection<List<DashboardAttendanceEntry>> = DashboardSection(),
    val recentGrades: DashboardSection<List<DashboardGrade>> = DashboardSection(),
    val upcomingTasks: DashboardSection<List<DashboardTask>> = DashboardSection(),
    val unreadMessagesCount: DashboardSection<Int> = DashboardSection(),
    val information: DashboardSection<List<DashboardInformation>> = DashboardSection(),
) {
    val selectedTimetable: DashboardSection<List<DashboardLesson>>
        get() = when (selectedTimetableDay) {
            DashboardTimetableDay.Today -> todayTimetable
            DashboardTimetableDay.Tomorrow -> tomorrowTimetable
        }

    val selectedTimetableDate: LocalDate
        get() = when (selectedTimetableDay) {
            DashboardTimetableDay.Today -> today
            DashboardTimetableDay.Tomorrow -> today.plusDays(1)
        }
}

data class DashboardSection<T>(
    val isLoading: Boolean = false,
    val data: T? = null,
    val errorMessage: String? = null,
)

enum class DashboardTimetableDay {
    Today,
    Tomorrow,
}
