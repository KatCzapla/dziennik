package pl.usagi.edziennik.ui.timetable

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import pl.usagi.edziennik.domain.ActiveStudentManager
import pl.usagi.edziennik.domain.Student
import pl.usagi.edziennik.domain.time.WeekRange
import pl.usagi.edziennik.domain.time.clampedSchoolWeekEnd
import pl.usagi.edziennik.domain.time.clampedSchoolWeekStart
import pl.usagi.edziennik.domain.time.currentSchoolYearRange
import pl.usagi.edziennik.domain.time.isSelectableSchoolDate
import pl.usagi.edziennik.repository.TimetableRepository
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class TimetableViewModel @Inject constructor(
    private val timetableRepository: TimetableRepository,
    private val activeStudentManager: ActiveStudentManager,
    private val clock: Clock,
) : ViewModel() {
    private val schoolYearRange = currentSchoolYearRange(LocalDate.now(clock))
    private val _uiState = MutableStateFlow(
        TimetableUiState(
            schoolYearRange = schoolYearRange,
            selectedDate = initialTimetableDate(LocalDate.now(clock), schoolYearRange),
        ),
    )
    val uiState: StateFlow<TimetableUiState> = _uiState.asStateFlow()
    private var loadedWeek: WeekRange? = null
    private var loadingJob: Job? = null
    private var loadId = 0L

    init {
        observeStudents()
        observeActiveStudent()
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

    fun currentWeek() {
        selectDate(initialTimetableDate(LocalDate.now(clock), schoolYearRange))
    }

    fun selectDate(date: LocalDate) {
        if (!date.isSelectableSchoolDate(schoolYearRange)) return

        val previousWeek = _uiState.value.displayedWeek
        _uiState.update { state -> state.copy(selectedDate = date) }
        if (WeekRange.fromDate(date) != previousWeek) {
            loadedWeek = null
            loadTimetable()
        }
    }

    fun selectStudent(student: Student) {
        activeStudentManager.setActiveStudent(student)
    }

    fun retry() {
        loadTimetable()
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
                loadingJob?.cancel()
                loadId++
                loadedWeek = null
                _uiState.update { state ->
                    state.copy(
                        activeStudent = student,
                        entries = if (student == null) emptyList() else state.entries,
                        isLoading = false,
                        errorMessage = if (student == null) {
                            "Wybierz ucznia, aby zobaczyć plan zajęć."
                        } else {
                            null
                        },
                    )
                }
                if (student != null) {
                    loadTimetable()
                }
            }
        }
    }

    private fun loadTimetable() {
        val student = _uiState.value.activeStudent
        if (student == null) {
            loadingJob?.cancel()
            loadId++
            _uiState.update { state ->
                state.copy(
                    entries = emptyList(),
                    isLoading = false,
                    errorMessage = "Wybierz ucznia, aby zobaczyć plan zajęć.",
                )
            }
            return
        }

        val week = _uiState.value.displayedWeek
        if (loadedWeek == week && loadingJob?.isActive != true) return
        loadingJob?.cancel()
        val requestId = ++loadId
        loadingJob = viewModelScope.launch {
            _uiState.update { state ->
                state.copy(
                    isLoading = true,
                    errorMessage = null,
                )
            }

            try {
                val entries = timetableRepository.getTimetable(
                    from = week.clampedSchoolWeekStart(schoolYearRange),
                    to = week.clampedSchoolWeekEnd(schoolYearRange),
                )
                if (loadId != requestId || activeStudentManager.activeStudent.value != student ||
                    _uiState.value.displayedWeek != week) return@launch
                _uiState.update { state ->
                    state.copy(
                        entries = entries,
                        isLoading = false,
                        errorMessage = null,
                    )
                }
                loadedWeek = week
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (loadId != requestId || activeStudentManager.activeStudent.value != student ||
                    _uiState.value.displayedWeek != week) return@launch
                _uiState.update { state ->
                    state.copy(
                        entries = emptyList(),
                        isLoading = false,
                        errorMessage = error.toTimetableErrorMessage(),
                    )
                }
                loadedWeek = null
            }
        }
    }
}
