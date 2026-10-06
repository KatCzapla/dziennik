package pl.usagi.edziennik.ui.attendance

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
import pl.usagi.edziennik.domain.AttendanceSubject
import pl.usagi.edziennik.domain.time.WeekRange
import pl.usagi.edziennik.domain.time.clampedSchoolWeekEnd
import pl.usagi.edziennik.domain.time.clampedSchoolWeekStart
import pl.usagi.edziennik.domain.time.coerceInSchoolYear
import pl.usagi.edziennik.domain.time.currentSchoolYearRange
import pl.usagi.edziennik.domain.time.isSelectableSchoolDate
import pl.usagi.edziennik.domain.time.lastSelectableSchoolDate
import pl.usagi.edziennik.repository.AttendanceRepository
import pl.usagi.edziennik.repository.AttendanceExcuseRequest
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
    private val latestSelectableDate = lastSelectableSchoolDate(schoolYearRange)
    private val _uiState = MutableStateFlow(
        AttendanceUiState(
            schoolYearRange = schoolYearRange,
            latestSelectableDate = latestSelectableDate,
            selectedDate = LocalDate.now(clock).attendanceDateWithinLimits(),
            today = LocalDate.now(clock),
        ),
    )
    val uiState: StateFlow<AttendanceUiState> = _uiState.asStateFlow()
    private var loadedWeek: WeekRange? = null
    private var attendanceJob: Job? = null
    private var attendanceLoadId = 0L
    private var loadedStatisticsSubjectId: Int? = null
    private var statisticsJob: Job? = null
    private var statisticsLoadId = 0L

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
        val previousWeek = _uiState.value.displayedWeek
        _uiState.update { state ->
            state.copy(
                selectedDate = date,
                excuseFormWeek = if (state.isSubmittingExcuse) state.excuseFormWeek else null,
                selectedExcuseOptions = if (state.isSubmittingExcuse) state.selectedExcuseOptions else emptySet(),
            )
        }
        if (WeekRange.fromDate(date) != previousWeek) {
            loadedWeek = null
            loadAttendance()
        }
    }

    fun retry() {
        if (_uiState.value.view == AttendanceView.STATISTICS) {
            loadStatistics(force = true)
        } else {
            loadAttendance(force = true)
        }
    }

    fun selectView(view: AttendanceView) {
        if (_uiState.value.view == view) return
        _uiState.update { it.copy(view = view) }
        if (view == AttendanceView.STATISTICS) loadStatistics()
    }

    fun selectSubject(subjectId: Int) {
        if (subjectId == _uiState.value.selectedSubjectId ||
            _uiState.value.subjects.none { it.id == subjectId }
        ) return

        _uiState.update {
            it.copy(selectedSubjectId = subjectId, statistics = null, statisticsErrorMessage = null)
        }
        loadStatistics()
    }

    fun selectStudent(student: Student) {
        activeStudentManager.setActiveStudent(student)
    }

    fun openExcuseForDisplayedWeek() {
        val state = _uiState.value
        if (state.availableExcuseOptions.isEmpty() || state.isSubmittingExcuse) return
        _uiState.update {
            it.copy(
                excuseFormWeek = state.displayedWeek,
                selectedExcuseOptions = emptySet(),
                excuseContent = "",
                excuseErrorMessage = null,
            )
        }
    }

    fun toggleExcuseOption(option: ExcuseOption) {
        val state = _uiState.value
        if (state.isSubmittingExcuse || state.excuseFormWeek != state.displayedWeek ||
            option !in state.availableExcuseOptions
        ) return
        _uiState.update {
            val otherDays = it.selectedExcuseOptions.filterNot { selected -> selected.date == option.date }
            val sameDay = it.selectedExcuseOptions.filter { selected -> selected.date == option.date }
            val selection = when {
                option in sameDay -> otherDays + sameDay.filterNot { selected -> selected == option }
                option is ExcuseOption.WholeDay -> otherDays + option
                else -> otherDays + sameDay.filterIsInstance<ExcuseOption.Lesson>() + option
            }
            it.copy(selectedExcuseOptions = selection.toSet(), excuseErrorMessage = null)
        }
    }

    fun changeExcuseContent(content: String) {
        if (content.codePointCount(0, content.length) > 256 || _uiState.value.isSubmittingExcuse) return
        _uiState.update { it.copy(excuseContent = content, excuseErrorMessage = null) }
    }

    fun dismissExcuse() {
        if (_uiState.value.isSubmittingExcuse) return
        _uiState.update {
            it.copy(excuseFormWeek = null, selectedExcuseOptions = emptySet(), excuseErrorMessage = null)
        }
    }

    fun submitExcuse() {
        val state = _uiState.value
        val options = state.selectedExcuseOptions.takeIf { it.isNotEmpty() } ?: return
        val student = state.activeStudent ?: return
        if (state.isSubmittingExcuse || state.excuseFormWeek != state.displayedWeek ||
            !state.availableExcuseOptions.containsAll(options)
        ) return
        _uiState.update { it.copy(isSubmittingExcuse = true, excuseErrorMessage = null) }
        viewModelScope.launch {
            try {
                attendanceRepository.submitExcuses(
                    options.sortedWith(compareBy<ExcuseOption> { it.date }.thenBy { it.lessonTimeId ?: -1 })
                        .map { AttendanceExcuseRequest(it.date, it.lessonTimeId) },
                    state.excuseContent,
                )
                if (activeStudentManager.activeStudent.value != student) return@launch
                _uiState.update {
                    it.copy(
                        submittedExcuseKeys = it.submittedExcuseKeys + options.map { selected ->
                            selected.date to selected.lessonTimeId
                        },
                    )
                }
                loadAttendance(force = true)?.join()
                if (activeStudentManager.activeStudent.value != student) return@launch
                _uiState.update {
                    it.copy(
                        isSubmittingExcuse = false,
                        excuseFormWeek = null,
                        selectedExcuseOptions = emptySet(),
                        excuseContent = "",
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (activeStudentManager.activeStudent.value != student) return@launch
                _uiState.update {
                    it.copy(isSubmittingExcuse = false, excuseErrorMessage = error.toExcuseSubmitErrorMessage())
                }
            }
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
                attendanceJob?.cancel()
                attendanceLoadId++
                loadedWeek = null
                statisticsJob?.cancel()
                statisticsLoadId++
                loadedStatisticsSubjectId = null
                _uiState.update { state ->
                    state.copy(
                        activeStudent = student,
                        weekEntries = if (student == null) emptyList() else state.weekEntries,
                        isLoading = false,
                        subjects = emptyList(),
                        selectedSubjectId = -1,
                        statistics = null,
                        isStatisticsLoading = false,
                        statisticsErrorMessage = null,
                        excusesActive = null,
                        excusesComplete = false,
                        submittedExcuseKeys = emptySet(),
                        excuseFormWeek = null,
                        selectedExcuseOptions = emptySet(),
                        excuseContent = "",
                        isSubmittingExcuse = false,
                        excuseErrorMessage = null,
                        errorMessage = if (student == null) {
                            "Wybierz ucznia, aby zobaczyć frekwencję."
                        } else {
                            null
                        },
                    )
                }
                if (student != null) {
                    loadAttendance()
                    if (_uiState.value.view == AttendanceView.STATISTICS) loadStatistics()
                }
            }
        }
    }

    private fun loadStatistics(force: Boolean = false) {
        val student = _uiState.value.activeStudent ?: return
        val subjectId = _uiState.value.selectedSubjectId
        if (!force && loadedStatisticsSubjectId == subjectId && statisticsJob?.isActive != true) return

        statisticsJob?.cancel()
        val requestId = ++statisticsLoadId
        statisticsJob = viewModelScope.launch {
            _uiState.update {
                it.copy(isStatisticsLoading = true, statisticsErrorMessage = null)
            }
            try {
                if (_uiState.value.subjects.isEmpty()) {
                    val subjects = attendanceRepository.getSubjects()
                    if (statisticsLoadId != requestId || activeStudentManager.activeStudent.value != student ||
                        _uiState.value.selectedSubjectId != subjectId) return@launch
                    val options = if (subjects.any { it.id == -1 }) {
                        subjects
                    } else {
                        listOf(AttendanceSubject(-1, "Wszystkie")) + subjects
                    }
                    _uiState.update { it.copy(subjects = options) }
                }
                val statistics = attendanceRepository.getStatistics(subjectId)
                if (statisticsLoadId != requestId || activeStudentManager.activeStudent.value != student ||
                    _uiState.value.selectedSubjectId != subjectId) return@launch
                _uiState.update {
                    it.copy(
                        statistics = statistics,
                        isStatisticsLoading = false,
                        statisticsErrorMessage = null,
                    )
                }
                loadedStatisticsSubjectId = subjectId
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                if (statisticsLoadId != requestId || activeStudentManager.activeStudent.value != student ||
                    _uiState.value.selectedSubjectId != subjectId) return@launch
                _uiState.update {
                    it.copy(
                        statistics = null,
                        isStatisticsLoading = false,
                        statisticsErrorMessage = error.toAttendanceErrorMessage(),
                    )
                }
                loadedStatisticsSubjectId = null
            }
        }
    }

    private fun loadAttendance(force: Boolean = false): Job? {
        val student = _uiState.value.activeStudent
        if (student == null) {
            attendanceJob?.cancel()
            attendanceLoadId++
            _uiState.update { state ->
                state.copy(
                    weekEntries = emptyList(),
                    excusesActive = null,
                    excusesComplete = false,
                    isLoading = false,
                    errorMessage = "Wybierz ucznia, aby zobaczyć frekwencję.",
                )
            }
            return null
        }

        val week = _uiState.value.displayedWeek
        if (!force && loadedWeek == week && attendanceJob?.isActive != true) return attendanceJob
        attendanceJob?.cancel()
        val requestId = ++attendanceLoadId
        attendanceJob = viewModelScope.launch {
            _uiState.update { state ->
                state.copy(
                    isLoading = true,
                    errorMessage = null,
                    excusesActive = null,
                    excusesComplete = false,
                )
            }

            try {
                val fetched = attendanceRepository.getAttendanceWithCompleteness(
                    from = week.clampedSchoolWeekStart(schoolYearRange),
                    to = week.clampedSchoolWeekEnd(schoolYearRange),
                )
                if (attendanceLoadId != requestId || activeStudentManager.activeStudent.value != student ||
                    _uiState.value.displayedWeek != week) return@launch
                _uiState.update { state ->
                    state.copy(
                        weekEntries = fetched.entries,
                        isLoading = false,
                        errorMessage = null,
                        excusesActive = fetched.excusesActive,
                        excusesComplete = fetched.excusesComplete && fetched.entriesComplete,
                    )
                }
                loadedWeek = week
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (attendanceLoadId != requestId || activeStudentManager.activeStudent.value != student ||
                    _uiState.value.displayedWeek != week) return@launch
                _uiState.update { state ->
                    state.copy(
                        weekEntries = emptyList(),
                        isLoading = false,
                        errorMessage = error.toAttendanceErrorMessage(),
                        excusesActive = null,
                        excusesComplete = false,
                    )
                }
                loadedWeek = null
            }
        }
        return attendanceJob
    }

    private fun LocalDate.attendanceDateWithinLimits(): LocalDate =
        initialAttendanceDate(this, schoolYearRange)
            .coerceInSchoolYear(schoolYearRange)

}
