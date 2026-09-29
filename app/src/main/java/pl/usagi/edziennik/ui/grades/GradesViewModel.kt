package pl.usagi.edziennik.ui.grades

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
import pl.usagi.edziennik.domain.ClassificationPeriod
import pl.usagi.edziennik.domain.Student
import pl.usagi.edziennik.domain.SubjectGrades
import pl.usagi.edziennik.repository.GradesRepository
import pl.usagi.edziennik.ui.navigation.GradeNavigationTarget
import java.time.Clock
import java.time.OffsetDateTime
import javax.inject.Inject

@HiltViewModel
class GradesViewModel @Inject constructor(
    private val gradesRepository: GradesRepository,
    private val activeStudentManager: ActiveStudentManager,
    private val clock: Clock,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(GradesUiState())
    val uiState: StateFlow<GradesUiState> = _uiState.asStateFlow()
    private val navigationTarget = GradeNavigationTarget.from(savedStateHandle)
    private var periodsJob: Job? = null
    private var gradesJob: Job? = null
    private var periodsLoadId = 0L
    private var gradesLoadId = 0L

    fun consumeNavigationTarget(): GradeNavigationTarget? {
        if (savedStateHandle.get<Boolean>("gradeTargetConsumed") == true || _uiState.value.isLoading) return null
        val target = navigationTarget ?: return null
        val state = _uiState.value
        if (state.selectedPeriod?.id != target.periodId) return null
        val subject = state.grades?.subjectByPosition(target.subjectPosition) ?: return null
        val column = subject.columns.firstOrNull { it.id == target.columnId } ?: return null
        if (target.gradeIndex !in column.grades.indices) return null
        savedStateHandle["gradeTargetConsumed"] = true
        return target
    }

    init {
        observeStudents()
        observeActiveStudent()
    }

    fun selectPeriod(period: ClassificationPeriod) {
        val student = _uiState.value.activeStudent ?: return
        periodsJob?.cancel()
        periodsLoadId++
        _uiState.update { state ->
            state.copy(
                selectedPeriod = period,
                isLoadingPeriods = false,
                errorMessage = null,
            )
        }
        loadGrades(student, period)
    }

    fun retry() {
        val state = _uiState.value
        val student = state.activeStudent ?: return
        val selectedPeriod = state.selectedPeriod
        if (state.periods.isEmpty() || selectedPeriod == null) {
            loadPeriods(student)
        } else {
            loadGrades(student, selectedPeriod)
        }
    }

    fun selectStudent(student: Student) {
        activeStudentManager.setActiveStudent(student)
    }

    fun subjectByPosition(position: Int): SubjectGrades? =
        _uiState.value.grades?.subjectByPosition(position)

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
                periodsJob?.cancel()
                gradesJob?.cancel()
                periodsLoadId++
                gradesLoadId++
                _uiState.update { state ->
                    state.copy(
                        activeStudent = student,
                        periods = emptyList(),
                        selectedPeriod = null,
                        grades = null,
                        isLoadingPeriods = false,
                        isLoadingGrades = false,
                        errorMessage = if (student == null) {
                            "Wybierz ucznia, aby zobaczyć oceny."
                        } else {
                            null
                        },
                    )
                }
                if (student != null) {
                    loadPeriods(student)
                }
            }
        }
    }

    private fun loadPeriods(student: Student) {
        periodsJob?.cancel()
        val requestId = ++periodsLoadId
        periodsJob = viewModelScope.launch {
            _uiState.update { state ->
                state.copy(
                    isLoadingPeriods = true,
                    isLoadingGrades = false,
                    errorMessage = null,
                )
            }

            try {
                val periods = gradesRepository.getClassificationPeriods(student)
                    .sortedBy { it.startDate }
                if (periodsLoadId != requestId || activeStudentManager.activeStudent.value != student) return@launch
                val selectedPeriod = periods.firstOrNull { it.id == navigationTarget?.periodId }
                    ?: chooseClassificationPeriod(
                        periods = periods,
                        now = OffsetDateTime.now(clock),
                    )
                _uiState.update { state ->
                    state.copy(
                        periods = periods,
                        selectedPeriod = selectedPeriod,
                        isLoadingPeriods = false,
                        errorMessage = if (selectedPeriod == null) {
                            "Nie znaleziono okresów klasyfikacyjnych."
                        } else {
                            null
                        },
                    )
                }
                if (selectedPeriod != null) {
                    loadGrades(student, selectedPeriod)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (periodsLoadId != requestId || activeStudentManager.activeStudent.value != student) return@launch
                _uiState.update { state ->
                    state.copy(
                        periods = emptyList(),
                        selectedPeriod = null,
                        grades = null,
                        isLoadingPeriods = false,
                        errorMessage = error.toGradesErrorMessage(),
                    )
                }
            }
        }
    }

    private fun loadGrades(
        student: Student,
        period: ClassificationPeriod,
    ) {
        gradesJob?.cancel()
        val requestId = ++gradesLoadId
        gradesJob = viewModelScope.launch {
            _uiState.update { state ->
                state.copy(
                    isLoadingGrades = true,
                    errorMessage = null,
                )
            }

            try {
                val grades = gradesRepository.getGrades(student, period)
                if (gradesLoadId != requestId || activeStudentManager.activeStudent.value != student ||
                    _uiState.value.selectedPeriod?.id != period.id) return@launch
                _uiState.update { state ->
                    state.copy(
                        grades = grades,
                        isLoadingGrades = false,
                        errorMessage = null,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (gradesLoadId != requestId || activeStudentManager.activeStudent.value != student ||
                    _uiState.value.selectedPeriod?.id != period.id) return@launch
                _uiState.update { state ->
                    state.copy(
                        isLoadingGrades = false,
                        errorMessage = error.toGradesErrorMessage(),
                    )
                }
            }
        }
    }
}
