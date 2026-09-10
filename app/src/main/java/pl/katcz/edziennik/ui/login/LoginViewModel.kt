package pl.katcz.edziennik.ui.login

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
import pl.katcz.edziennik.network.session.UonetSessionManager
import pl.katcz.edziennik.repository.StudentRepository
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val sessionManager: UonetSessionManager,
    private val studentRepository: StudentRepository,
    private val activeStudentManager: ActiveStudentManager,
) : ViewModel() {
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    init {
        observeActiveStudent()
        checkPersistedSession()
    }

    fun onLoginChanged(login: String) {
        _uiState.update { state ->
            state.copy(login = login, errorMessage = null)
        }
    }

    fun onPasswordChanged(password: String) {
        _uiState.update { state ->
            state.copy(password = password, errorMessage = null)
        }
    }

    fun login() {
        val credentials = _uiState.value
        if (!credentials.canSubmit) return

        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(
                    password = "",
                    isLoading = true,
                    errorMessage = null,
                )
            }

            val result = sessionManager.login(
                username = credentials.login,
                password = credentials.password,
            )

            result.fold(
                onSuccess = {
                    val isValid = sessionManager.isSessionValid()
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            isSessionValid = isValid,
                            errorMessage = if (isValid) null else "Sesja UONET+ nie została potwierdzona.",
                        )
                    }
                    if (isValid) {
                        loadStudents()
                    }
                },
                onFailure = { error ->
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            isSessionValid = false,
                            errorMessage = error.toLoginErrorMessage(),
                        )
                    }
                },
            )
        }
    }

    fun selectStudent(student: Student) {
        activeStudentManager.setActiveStudent(student)
        _uiState.update { state ->
            state.copy(errorMessage = null)
        }
    }

    fun clearSession() {
        viewModelScope.launch {
            sessionManager.clearSession()
            activeStudentManager.clear()
            _uiState.update { LoginUiState(isCheckingSession = false) }
        }
    }

    private fun checkPersistedSession() {
        viewModelScope.launch {
            val isValid = sessionManager.validatePersistedSession()
            _uiState.update { state ->
                state.copy(
                    isCheckingSession = false,
                    isSessionValid = isValid,
                    errorMessage = null,
                )
            }
            if (isValid) {
                loadStudents()
            }
        }
    }

    private fun observeActiveStudent() {
        viewModelScope.launch {
            activeStudentManager.activeStudent.collect { student ->
                _uiState.update { state ->
                    state.copy(activeStudent = student)
                }
            }
        }
    }

    private suspend fun loadStudents() {
        _uiState.update { state ->
            state.copy(
                isLoadingStudents = true,
                errorMessage = null,
            )
        }

        runCatching { studentRepository.getStudents() }
            .onSuccess { students ->
                activeStudentManager.initialize(students)
                _uiState.update { state ->
                    state.copy(
                        isLoadingStudents = false,
                        students = students,
                        errorMessage = if (students.isEmpty()) "Konto nie ma przypisanych uczniów." else null,
                    )
                }
            }
            .onFailure {
                activeStudentManager.clear()
                _uiState.update { state ->
                    state.copy(
                        isLoadingStudents = false,
                        students = emptyList(),
                        errorMessage = "Nie udało się pobrać danych ucznia.",
                    )
                }
            }
    }
}
