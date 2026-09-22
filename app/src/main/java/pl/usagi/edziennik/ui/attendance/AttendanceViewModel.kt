package pl.usagi.edziennik.ui.attendance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.usagi.edziennik.domain.ActiveStudentManager
import pl.usagi.edziennik.domain.Student
import pl.usagi.edziennik.domain.time.WeekRange
import pl.usagi.edziennik.domain.time.clampedSchoolWeekEnd
import pl.usagi.edziennik.domain.time.clampedSchoolWeekStart
import pl.usagi.edziennik.domain.time.coerceInSchoolYear
import pl.usagi.edziennik.domain.time.currentSchoolYearRange
import pl.usagi.edziennik.domain.time.isSelectableSchoolDate
import pl.usagi.edziennik.repository.AttendanceRepository
import pl.usagi.edziennik.ui.timetable.nextSchoolDate
import pl.usagi.edziennik.ui.timetable.previousSchoolDate
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class AttendanceViewModel @Inject constructor(
    private val attendanceRepository: AttendanceRepository,
    private val activeStudentManager: ActiveStudentManager,
    private val clock: Clock,
) : ViewModel() {
    private val schoolYearRange = currentSchoolYearRange(LocalDate.now(clock))
    private val latestSelectableDate = LocalDate.now(clock).latestAttendanceSelectableDate()
    private val _uiState = MutableStateFlow(
        AttendanceUiState(
            schoolYearRange = schoolYearRange,
            latestSelectableDate = latestSelectableDate,
            selectedDate = LocalDate.now(clock).attendanceDateWithinLimits(),
        ),
    )
    val uiState: StateFlow<AttendanceUiState> = _uiState.asStateFlow()
    private var loadedWeek: WeekRange? = null

    init {
        observeStudents()
        observeActiveStudent()
    }

    fun previousDay() {
        selectDate(previousSchoolDate(_uiState.value.selectedDate))
    }

    fun nextDay() {
        selectDate(nextSchoolDate(_uiState.value.selectedDate))
    }

    fun previousWeek() {
        if (_uiState.value.canGoPreviousWeek) {
            selectDate(_uiState.value.selectedDate.minusWeeks(1))
        }
    }

    fun nextWeek() {
        if (_uiState.value.canGoNextWeek) {
            selectDate(_uiState.value.selectedDate.plusWeeks(1))
        }
    }

    fun today() {
        selectDate(LocalDate.now(clock).attendanceDateWithinLimits())
    }

    fun selectDate(date: LocalDate) {
        if (!date.isSelectableSchoolDate(schoolYearRange)) return
        if (WeekRange.fromDate(date).start.isAfter(WeekRange.fromDate(latestSelectableDate).start)) return

        val previousWeek = _uiState.value.displayedWeek
        _uiState.update { state -> state.copy(selectedDate = date) }
        if (WeekRange.fromDate(date) != previousWeek) {
            loadAttendance()
        }
    }

    fun retry() {
        loadAttendance(force = true)
    }

    fun selectStudent(student: Student) {
        activeStudentManager.setActiveStudent(student)
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
                        weekEntries = if (student == null) emptyList() else state.weekEntries,
                        errorMessage = if (student == null) {
                            "Wybierz ucznia, aby zobaczyć frekwencję."
                        } else {
                            null
                        },
                    )
                }
                if (student != null) {
                    loadedWeek = null
                    loadAttendance()
                }
            }
        }
    }

    private fun loadAttendance(force: Boolean = false) {
        if (_uiState.value.activeStudent == null) {
            _uiState.update { state ->
                state.copy(
                    weekEntries = emptyList(),
                    isLoading = false,
                    errorMessage = "Wybierz ucznia, aby zobaczyć frekwencję.",
                )
            }
            return
        }

        viewModelScope.launch {
            val week = _uiState.value.displayedWeek
            if (!force && loadedWeek == week) {
                return@launch
            }
            _uiState.update { state ->
                state.copy(
                    isLoading = true,
                    errorMessage = null,
                )
            }

            runCatching {
                attendanceRepository.getAttendance(
                    from = week.clampedSchoolWeekStart(schoolYearRange),
                    to = week.clampedSchoolWeekEnd(schoolYearRange),
                )
            }.onSuccess { entries ->
                _uiState.update { state ->
                    state.copy(
                        weekEntries = entries,
                        isLoading = false,
                        errorMessage = null,
                    )
                }
                loadedWeek = week
            }.onFailure { error ->
                _uiState.update { state ->
                    state.copy(
                        weekEntries = emptyList(),
                        isLoading = false,
                        errorMessage = error.toAttendanceErrorMessage(),
                    )
                }
                loadedWeek = null
            }
        }
    }

    private fun LocalDate.attendanceDateWithinLimits(): LocalDate =
        initialAttendanceDate(this, schoolYearRange)
            .coerceInSchoolYear(schoolYearRange)

    private fun LocalDate.latestAttendanceSelectableDate(): LocalDate =
        WeekRange.fromDate(this).start.plusDays(4)
            .coerceInSchoolYear(schoolYearRange)
}
