package pl.usagi.edziennik.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import pl.usagi.edziennik.domain.ActiveStudentManager
import pl.usagi.edziennik.domain.ClassificationPeriod
import pl.usagi.edziennik.domain.Student
import pl.usagi.edziennik.domain.time.WeekRange
import pl.usagi.edziennik.domain.time.clampedSchoolWeekEnd
import pl.usagi.edziennik.domain.time.clampedSchoolWeekStart
import pl.usagi.edziennik.domain.time.currentSchoolYearRange
import pl.usagi.edziennik.repository.AttendanceRepository
import pl.usagi.edziennik.repository.GradesRepository
import pl.usagi.edziennik.repository.MessagesRepository
import pl.usagi.edziennik.repository.SchoolTasksRepository
import pl.usagi.edziennik.repository.TimetableRepository
import pl.usagi.edziennik.ui.grades.chooseClassificationPeriod
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val timetableRepository: TimetableRepository,
    private val attendanceRepository: AttendanceRepository,
    private val gradesRepository: GradesRepository,
    private val schoolTasksRepository: SchoolTasksRepository,
    private val messagesRepository: MessagesRepository,
    private val activeStudentManager: ActiveStudentManager,
    private val clock: Clock,
) : ViewModel() {
    private val schoolYearRange = currentSchoolYearRange(LocalDate.now(clock))
    private val _uiState = MutableStateFlow(
        DashboardUiState(today = LocalDate.now(clock)),
    )
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        observeStudents()
        observeActiveStudent()
        observeUnreadCountInvalidations()
    }

    fun selectStudent(student: Student) {
        activeStudentManager.setActiveStudent(student)
    }

    fun showTodayTimetable() {
        _uiState.update { state -> state.copy(selectedTimetableDay = DashboardTimetableDay.Today) }
    }

    fun showTomorrowTimetable() {
        _uiState.update { state -> state.copy(selectedTimetableDay = DashboardTimetableDay.Tomorrow) }
    }

    private fun observeStudents() {
        viewModelScope.launch {
            activeStudentManager.students.collect { students ->
                _uiState.update { state -> state.copy(students = students) }
            }
        }
    }

    private fun observeActiveStudent() {
        viewModelScope.launch {
            activeStudentManager.activeStudent.collect { student ->
                _uiState.update { state ->
                    state.copy(
                        activeStudent = student,
                        today = LocalDate.now(clock),
                        selectedTimetableDay = DashboardTimetableDay.Today,
                        todayTimetable = DashboardSection(),
                        tomorrowTimetable = DashboardSection(),
                        todayAttendance = DashboardSection(),
                        recentGrades = DashboardSection(),
                        upcomingTasks = DashboardSection(),
                        unreadMessagesCount = DashboardSection(),
                    )
                }
                if (student != null) {
                    loadDashboard(student)
                }
            }
        }
    }

    private fun observeUnreadCountInvalidations() {
        viewModelScope.launch {
            messagesRepository.unreadCountInvalidations.collect {
                activeStudentManager.activeStudent.value?.let(::loadUnreadMessagesCount)
            }
        }
    }

    private fun loadDashboard(student: Student) {
        val today = LocalDate.now(clock)
        loadTimetableDay(
            student = student,
            date = today,
            day = DashboardTimetableDay.Today,
        )
        loadTimetableDay(
            student = student,
            date = today.plusDays(1),
            day = DashboardTimetableDay.Tomorrow,
        )
        loadTodayAttendance(student, today)
        loadGrades(student)
        loadSchoolTasks(student, today)
        loadUnreadMessagesCount(student)
    }

    private fun loadTimetableDay(
        student: Student,
        date: LocalDate,
        day: DashboardTimetableDay,
    ) {
        if (!date.isInSchoolYear()) {
            updateTimetableSection(day, DashboardSection(data = emptyList()))
            return
        }

        updateTimetableSection(day, DashboardSection(isLoading = true))
        viewModelScope.launch {
            runCatching {
                timetableRepository.getTimetable(from = date, to = date)
                    .toDashboardLessons(
                        displayedDate = date,
                        now = LocalDateTime.now(clock),
                    )
            }.onSuccess { lessons ->
                if (student.isStillActive()) {
                    updateTimetableSection(day, DashboardSection(data = lessons))
                }
            }.onFailure {
                if (student.isStillActive()) {
                    updateTimetableSection(day, DashboardSection(errorMessage = "Nie udało się pobrać planu lekcji"))
                }
            }
        }
    }

    private fun loadTodayAttendance(
        student: Student,
        today: LocalDate,
    ) {
        if (!today.isInSchoolYear()) {
            _uiState.update { state ->
                state.copy(todayAttendance = DashboardSection(data = emptyList()))
            }
            return
        }

        _uiState.update { state ->
            state.copy(todayAttendance = DashboardSection(isLoading = true))
        }
        viewModelScope.launch {
            val week = WeekRange.fromDate(today)
            runCatching {
                attendanceRepository.getAttendance(
                    from = week.clampedSchoolWeekStart(schoolYearRange),
                    to = week.clampedSchoolWeekEnd(schoolYearRange),
                ).toDashboardAttendanceEntries(today)
            }.onSuccess { entries ->
                if (student.isStillActive()) {
                    _uiState.update { state ->
                        state.copy(todayAttendance = DashboardSection(data = entries))
                    }
                }
            }.onFailure {
                if (student.isStillActive()) {
                    _uiState.update { state ->
                        state.copy(todayAttendance = DashboardSection(errorMessage = "Nie udało się pobrać frekwencji"))
                    }
                }
            }
        }
    }

    private fun loadGrades(student: Student) {
        _uiState.update { state ->
            state.copy(recentGrades = DashboardSection(isLoading = true))
        }
        viewModelScope.launch {
            runCatching {
                val period = gradesRepository.getClassificationPeriods(student)
                    .currentPeriodOrFallback()
                    ?: return@runCatching emptyList()
                gradesRepository.getGrades(student, period).toDashboardGrades(periodId = period.id)
            }.onSuccess { grades ->
                if (student.isStillActive()) {
                    _uiState.update { state ->
                        state.copy(recentGrades = DashboardSection(data = grades))
                    }
                }
            }.onFailure {
                if (student.isStillActive()) {
                    _uiState.update { state ->
                        state.copy(recentGrades = DashboardSection(errorMessage = "Nie udało się pobrać ocen"))
                    }
                }
            }
        }
    }

    private fun loadSchoolTasks(
        student: Student,
        today: LocalDate,
    ) {
        val dateTo = minOf(today.plusDays(21), schoolYearRange.end)
        if (today.isAfter(schoolYearRange.end)) {
            _uiState.update { state ->
                state.copy(upcomingTasks = DashboardSection(data = emptyList()))
            }
            return
        }

        _uiState.update { state ->
            state.copy(upcomingTasks = DashboardSection(isLoading = true))
        }
        viewModelScope.launch {
            runCatching {
                schoolTasksRepository.getSchoolTasks(
                    from = maxOf(today, schoolYearRange.start),
                    to = dateTo,
                ).toDashboardTasks(today)
            }.onSuccess { tasks ->
                if (student.isStillActive()) {
                    _uiState.update { state ->
                        state.copy(upcomingTasks = DashboardSection(data = tasks))
                    }
                }
            }.onFailure {
                if (student.isStillActive()) {
                    _uiState.update { state ->
                        state.copy(upcomingTasks = DashboardSection(errorMessage = "Nie udało się pobrać sprawdzianów i zadań"))
                    }
                }
            }
        }
    }

    private fun loadUnreadMessagesCount(student: Student) {
        _uiState.update { state ->
            state.copy(unreadMessagesCount = DashboardSection(isLoading = true))
        }
        viewModelScope.launch {
            runCatching {
                messagesRepository.getUnreadCount()
            }.onSuccess { count ->
                if (student.isStillActive()) {
                    _uiState.update { state ->
                        state.copy(unreadMessagesCount = DashboardSection(data = count))
                    }
                }
            }.onFailure {
                if (student.isStillActive()) {
                    _uiState.update { state ->
                        state.copy(unreadMessagesCount = DashboardSection())
                    }
                }
            }
        }
    }

    private fun updateTimetableSection(
        day: DashboardTimetableDay,
        section: DashboardSection<List<DashboardLesson>>,
    ) {
        _uiState.update { state ->
            when (day) {
                DashboardTimetableDay.Today -> state.copy(todayTimetable = section)
                DashboardTimetableDay.Tomorrow -> state.copy(tomorrowTimetable = section)
            }
        }
    }

    private fun List<ClassificationPeriod>.currentPeriodOrFallback(): ClassificationPeriod? =
        chooseClassificationPeriod(
            periods = this,
            now = OffsetDateTime.now(clock),
        )

    private fun Student.isStillActive(): Boolean =
        activeStudentManager.activeStudent.value == this

    private fun LocalDate.isInSchoolYear(): Boolean =
        !isBefore(schoolYearRange.start) && !isAfter(schoolYearRange.end)
}
