package pl.katcz.edziennik.ui.timetable

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.katcz.edziennik.domain.ActiveStudentManager
import pl.katcz.edziennik.domain.Student
import pl.katcz.edziennik.domain.time.WeekRange
import pl.katcz.edziennik.repository.TimetableRepository
import javax.inject.Inject

@HiltViewModel
class TimetableViewModel @Inject constructor(
    private val timetableRepository: TimetableRepository,
    private val activeStudentManager: ActiveStudentManager,
) : ViewModel() {
    private val _uiState = MutableStateFlow(TimetableUiState())
    val uiState: StateFlow<TimetableUiState> = _uiState.asStateFlow()

    init {
        observeStudents()
        observeActiveStudent()
    }

    fun previousWeek() {
        _uiState.update { state -> state.copy(week = state.week.previous()) }
        loadTimetable()
    }

    fun nextWeek() {
        _uiState.update { state -> state.copy(week = state.week.next()) }
        loadTimetable()
    }

    fun currentWeek() {
        _uiState.update { state -> state.copy(week = WeekRange.current()) }
        loadTimetable()
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
                _uiState.update { state ->
                    state.copy(
                        activeStudent = student,
                        entries = if (student == null) emptyList() else state.entries,
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
        if (_uiState.value.activeStudent == null) {
            _uiState.update { state ->
                state.copy(
                    entries = emptyList(),
                    isLoading = false,
                    errorMessage = "Wybierz ucznia, aby zobaczyć plan zajęć.",
                )
            }
            return
        }

        viewModelScope.launch {
            val week = _uiState.value.week
            _uiState.update { state ->
                state.copy(
                    isLoading = true,
                    errorMessage = null,
                )
            }

            runCatching {
                timetableRepository.getTimetable(
                    from = week.start,
                    to = week.end,
                )
            }.onSuccess { entries ->
                _uiState.update { state ->
                    state.copy(
                        entries = entries,
                        isLoading = false,
                        errorMessage = null,
                    )
                }
            }.onFailure { error ->
                _uiState.update { state ->
                    state.copy(
                        entries = emptyList(),
                        isLoading = false,
                        errorMessage = error.toTimetableErrorMessage(),
                    )
                }
            }
        }
    }
}
