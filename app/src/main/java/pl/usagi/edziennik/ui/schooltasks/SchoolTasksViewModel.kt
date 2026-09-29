package pl.usagi.edziennik.ui.schooltasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
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
import pl.usagi.edziennik.repository.SchoolTasksRepository
import pl.usagi.edziennik.ui.navigation.SchoolTaskNavigationTarget
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class SchoolTasksViewModel @Inject constructor(
    private val schoolTasksRepository: SchoolTasksRepository,
    private val activeStudentManager: ActiveStudentManager,
    private val clock: Clock,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val schoolYearRange = currentSchoolYearRange(LocalDate.now(clock))
    private val navigationTarget = SchoolTaskNavigationTarget.from(savedStateHandle)
    private val _uiState = MutableStateFlow(
        SchoolTasksUiState(
            schoolYearRange = schoolYearRange,
            selectedDate = navigationTarget?.date?.takeIf { it.isSelectableSchoolDate(schoolYearRange) }
                ?: initialSchoolTasksDate(LocalDate.now(clock), schoolYearRange),
            navigationTarget = navigationTarget.takeUnless { savedStateHandle.get<Boolean>("taskTargetConsumed") == true },
        ),
    )
    val uiState: StateFlow<SchoolTasksUiState> = _uiState.asStateFlow()
    private var loadedWeek: WeekRange? = null
    private var loadingJob: Job? = null
    private var loadId = 0L

    fun navigationTargetShown() {
        savedStateHandle["taskTargetConsumed"] = true
        _uiState.update { it.copy(navigationTarget = null) }
    }

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

    fun today() {
        selectDate(initialSchoolTasksDate(LocalDate.now(clock), schoolYearRange))
    }

    fun selectStudent(student: Student) {
        activeStudentManager.setActiveStudent(student)
    }

    fun retry() {
        loadSchoolTasks(force = true)
    }

    private fun selectDate(date: LocalDate) {
        if (!date.isSelectableSchoolDate(schoolYearRange)) return

        val previousWeek = _uiState.value.displayedWeek
        _uiState.update { state -> state.copy(selectedDate = date) }
        if (WeekRange.fromDate(date) != previousWeek) {
            loadedWeek = null
            loadSchoolTasks()
        }
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
                        weekTasks = if (student == null) emptyList() else state.weekTasks,
                        isLoading = false,
                        errorMessage = if (student == null) {
                            "Wybierz ucznia, aby zobaczyć sprawdziany i zadania."
                        } else {
                            null
                        },
                    )
                }
                if (student != null) {
                    loadSchoolTasks()
                }
            }
        }
    }

    private fun loadSchoolTasks(force: Boolean = false) {
        val student = _uiState.value.activeStudent
        if (student == null) {
            loadingJob?.cancel()
            loadId++
            _uiState.update { state ->
                state.copy(
                    weekTasks = emptyList(),
                    isLoading = false,
                    errorMessage = "Wybierz ucznia, aby zobaczyć sprawdziany i zadania.",
                )
            }
            return
        }

        val week = _uiState.value.displayedWeek
        if (!force && loadedWeek == week && loadingJob?.isActive != true) return
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
                val tasks = schoolTasksRepository.getSchoolTasks(
                    from = week.clampedSchoolWeekStart(schoolYearRange),
                    to = week.clampedSchoolWeekEnd(schoolYearRange),
                )
                if (loadId != requestId || activeStudentManager.activeStudent.value != student ||
                    _uiState.value.displayedWeek != week) return@launch
                _uiState.update { state ->
                    state.copy(
                        weekTasks = tasks.filter { it.date in week.clampedSchoolWeekStart(schoolYearRange)..week.clampedSchoolWeekEnd(schoolYearRange) },
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
                        weekTasks = emptyList(),
                        isLoading = false,
                        errorMessage = error.toSchoolTasksErrorMessage(),
                    )
                }
                loadedWeek = null
            }
        }
    }
}
