package pl.usagi.edziennik.ui.login

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
import pl.usagi.edziennik.network.credentials.CredentialStore
import pl.usagi.edziennik.network.credentials.StoredCredentials
import pl.usagi.edziennik.network.session.AutoLoginSettings
import pl.usagi.edziennik.network.session.SessionManager
import pl.usagi.edziennik.repository.StudentRepository
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val credentialStore: CredentialStore,
    private val autoLoginSettings: AutoLoginSettings,
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

    fun onRememberCredentialsChanged(rememberCredentials: Boolean) {
        _uiState.update { state ->
            state.copy(rememberCredentials = rememberCredentials)
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
                    val credentialErrorMessage = if (isValid) {
                        persistCredentialsAfterSuccessfulLogin(
                            username = credentials.login,
                            password = credentials.password,
                            rememberCredentials = credentials.rememberCredentials,
                        )
                    } else {
                        null
                    }
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            isSessionValid = isValid,
                            errorMessage = credentialErrorMessage
                                ?: if (isValid) null else "Sesja UONET+ nie została potwierdzona.",
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
            autoLoginSettings.setAutoLoginEnabled(false)
            activeStudentManager.clear()
            _uiState.update {
                LoginUiState(
                    isCheckingSession = false,
                    rememberCredentials = credentialStore.hasCredentials(),
                )
            }
        }
    }

    fun clearStoredCredentials() {
        viewModelScope.launch {
            credentialStore.clear()
            autoLoginSettings.setAutoLoginEnabled(false)
            _uiState.update { state ->
                state.copy(rememberCredentials = false)
            }
        }
    }

    private fun checkPersistedSession() {
        viewModelScope.launch {
            val isValid = sessionManager.validatePersistedSession()
            if (isValid) {
                _uiState.update { state ->
                    state.copy(
                        isCheckingSession = false,
                        isSessionValid = true,
                        errorMessage = null,
                    )
                }
                loadStudents()
                return@launch
            }

            if (!autoLoginSettings.isAutoLoginEnabled()) {
                _uiState.update { state ->
                    state.copy(
                        isCheckingSession = false,
                        isSessionValid = false,
                        rememberCredentials = false,
                        errorMessage = null,
                    )
                }
                return@launch
            }

            val storedCredentials = credentialStore.load()
            if (storedCredentials == null) {
                autoLoginSettings.setAutoLoginEnabled(false)
                _uiState.update { state ->
                    state.copy(
                        isCheckingSession = false,
                        isSessionValid = false,
                        rememberCredentials = false,
                        errorMessage = null,
                    )
                }
                return@launch
            }

            _uiState.update { state ->
                state.copy(rememberCredentials = true)
            }

            val renewed = renewSession(storedCredentials)
            _uiState.update { state ->
                state.copy(
                    isCheckingSession = false,
                    isSessionValid = renewed,
                    errorMessage = if (renewed) {
                        null
                    } else {
                        "Sesja wygasła. Zaloguj się ponownie."
                    },
                )
            }
            if (renewed) {
                loadStudents()
            }
        }
    }

    private suspend fun renewSession(credentials: StoredCredentials): Boolean {
        val loginResult = sessionManager.login(
            username = credentials.username,
            password = credentials.password,
        )
        return loginResult.isSuccess && sessionManager.isSessionValid()
    }

    private suspend fun persistCredentialsAfterSuccessfulLogin(
        username: String,
        password: String,
        rememberCredentials: Boolean,
    ): String? =
        if (rememberCredentials) {
            runCatching {
                credentialStore.save(
                    username = username,
                    password = password,
                )
                autoLoginSettings.setAutoLoginEnabled(true)
            }.fold(
                onSuccess = { null },
                onFailure = { "Zalogowano, ale nie udało się zapamiętać danych logowania." },
            )
        } else {
            runCatching {
                autoLoginSettings.setAutoLoginEnabled(false)
            }.fold(
                onSuccess = { null },
                onFailure = { "Zalogowano, ale nie udało się zaktualizować ustawień automatycznego logowania." },
            )
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
