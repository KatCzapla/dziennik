package pl.usagi.edziennik.ui.payments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.usagi.edziennik.domain.ActiveStudentManager
import pl.usagi.edziennik.domain.Payments
import pl.usagi.edziennik.domain.Student
import pl.usagi.edziennik.repository.PaymentsRepository
import javax.inject.Inject

data class PaymentsUiState(
    val students: List<Student> = emptyList(),
    val activeStudent: Student? = null,
    val payments: Payments? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class PaymentsViewModel @Inject constructor(
    private val repository: PaymentsRepository,
    private val activeStudentManager: ActiveStudentManager,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PaymentsUiState())
    val uiState = _uiState.asStateFlow()
    private var loadingJob: Job? = null

    init {
        viewModelScope.launch {
            activeStudentManager.students.collect { students ->
                _uiState.update { it.copy(students = students) }
            }
        }
        viewModelScope.launch {
            activeStudentManager.activeStudent.collect { student ->
                loadingJob?.cancel()
                _uiState.update {
                    it.copy(activeStudent = student, payments = null, isLoading = false, errorMessage = null)
                }
                if (student != null) load(student)
            }
        }
    }

    fun selectStudent(student: Student) = activeStudentManager.setActiveStudent(student)

    fun retry() {
        _uiState.value.activeStudent?.let(::load)
    }

    private fun load(student: Student) {
        loadingJob?.cancel()
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        loadingJob = viewModelScope.launch {
            try {
                val payments = repository.getPayments(student)
                if (activeStudentManager.activeStudent.value == student) {
                    _uiState.update { it.copy(payments = payments, isLoading = false) }
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                if (activeStudentManager.activeStudent.value == student) {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "Nie udało się pobrać opłat. Spróbuj ponownie.")
                    }
                }
            }
        }
    }
}
