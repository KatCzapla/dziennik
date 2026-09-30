package pl.usagi.edziennik.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.usagi.edziennik.domain.ActiveStudentManager
import pl.usagi.edziennik.domain.Student
import pl.usagi.edziennik.domain.changes.LocalAccountId
import pl.usagi.edziennik.persistence.changes.LocalSnapshotIdentity
import pl.usagi.edziennik.network.credentials.CredentialStore
import pl.usagi.edziennik.network.credentials.StoredCredentials
import pl.usagi.edziennik.network.session.AutoLoginSettings
import pl.usagi.edziennik.network.session.AutoDetectLogin
import pl.usagi.edziennik.network.session.AutoDetectLoginResult
import pl.usagi.edziennik.network.session.AuthenticationException
import pl.usagi.edziennik.network.session.AuthenticationProvider
import pl.usagi.edziennik.network.session.CurrentAuthenticationSession
import pl.usagi.edziennik.network.session.SessionManager
import pl.usagi.edziennik.network.session.toLoggedInUser
import pl.usagi.edziennik.repository.MessagesRepository
import pl.usagi.edziennik.repository.StudentRepository
import pl.usagi.edziennik.persistence.changes.SnapshotStore
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val autoDetectLogin: AutoDetectLogin,
    private val currentSession: CurrentAuthenticationSession,
    private val messagesRepository: MessagesRepository,
    private val credentialStore: CredentialStore,
    private val autoLoginSettings: AutoLoginSettings,
    private val studentRepository: StudentRepository,
    private val activeStudentManager: ActiveStudentManager,
    private val localSnapshotIdentity: LocalSnapshotIdentity,
    private val snapshotStore: SnapshotStore,
) : ViewModel() {
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    init {
        observeActiveStudent()
        observeLoggedInUser()
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

            currentSession.clear()
            val result = autoDetectLogin.authenticate(
                username = credentials.login,
                password = credentials.password,
            )

            when (result) {
                is AutoDetectLoginResult.Success -> {
                    currentSession.activate(result)
                    val isValid = sessionManager.isSessionValid()
                    if (!isValid) currentSession.clear()
                    val credentialErrorMessage = if (isValid) {
                        persistCredentialsAfterSuccessfulLogin(
                            username = credentials.login,
                            password = credentials.password,
                            rememberCredentials = credentials.rememberCredentials,
                            provider = result.provider,
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
                        replaceSnapshotAccount()
                        loadLoggedInUser()
                        loadStudents()
                    }
                }
                AutoDetectLoginResult.InvalidCredentials,
                AutoDetectLoginResult.TechnicalError -> {
                    val message = if (result == AutoDetectLoginResult.InvalidCredentials) {
                        AuthenticationException.InvalidCredentials().toLoginErrorMessage()
                    } else {
                        "Proces logowania UONET+ nie zakończył się poprawnie."
                    }
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            isSessionValid = false,
                            errorMessage = message,
                        )
                    }
                }
            }
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
            currentSession.clear()
            autoLoginSettings.setAutoLoginEnabled(false)
            autoLoginSettings.setAuthenticationProvider(null)
            activeStudentManager.clear()
            clearSnapshotAccount()
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
            autoLoginSettings.setAuthenticationProvider(null)
            _uiState.update { state ->
                state.copy(rememberCredentials = false)
            }
        }
    }

    private fun checkPersistedSession() {
        viewModelScope.launch {
            val savedProvider = autoLoginSettings.getAuthenticationProvider()
            currentSession.restore(savedProvider ?: AuthenticationProvider.PROJECT_UONET)
            val isValid = sessionManager.validatePersistedSession()
            if (isValid) {
                runCatching { localSnapshotIdentity.ensureAccountId() }
                if (savedProvider == null) {
                    autoLoginSettings.setAuthenticationProvider(AuthenticationProvider.PROJECT_UONET)
                }
                loadLoggedInUser()
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

            currentSession.clear()

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

            if (savedProvider == null) autoLoginSettings.setAutoLoginEnabled(false)
            val renewed = savedProvider?.let { renewSession(storedCredentials, it) } ?: false
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
                runCatching { localSnapshotIdentity.ensureAccountId() }
                loadLoggedInUser()
                loadStudents()
            }
        }
    }

    private suspend fun renewSession(
        credentials: StoredCredentials,
        provider: AuthenticationProvider,
    ): Boolean {
        val result = autoDetectLogin.authenticateWithProvider(
            provider = provider,
            username = credentials.username,
            password = credentials.password,
        )
        if (result !is AutoDetectLoginResult.Success) return false
        currentSession.activate(result)
        val isValid = sessionManager.isSessionValid()
        if (!isValid) sessionManager.clearSession()
        return isValid
    }

    private suspend fun replaceSnapshotAccount() {
        val oldId = runCatching { localSnapshotIdentity.currentAccountId() }.getOrNull()
        runCatching { localSnapshotIdentity.replaceAccountId() }
            .onFailure { runCatching { localSnapshotIdentity.clearAccount() } }
        oldId?.let { deleteSnapshotsSafely(it) }
    }

    private suspend fun clearSnapshotAccount() {
        val oldId = runCatching { localSnapshotIdentity.clearAccount() }.getOrNull()
        oldId?.let { deleteSnapshotsSafely(it) }
    }

    private suspend fun deleteSnapshotsSafely(accountId: LocalAccountId) {
        try {
            snapshotStore.deleteAccount(accountId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // The local account ID is already invalidated, so these rows cannot be reused.
        }
    }

    private suspend fun persistCredentialsAfterSuccessfulLogin(
        username: String,
        password: String,
        rememberCredentials: Boolean,
        provider: AuthenticationProvider,
    ): String? =
        if (rememberCredentials) {
            runCatching {
                autoLoginSettings.setAutoLoginEnabled(false)
                autoLoginSettings.setAuthenticationProvider(provider)
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
                autoLoginSettings.setAuthenticationProvider(provider)
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

    private fun observeLoggedInUser() {
        viewModelScope.launch {
            currentSession.loggedInUser.collect { user ->
                _uiState.update { state -> state.copy(loggedInUser = user) }
            }
        }
    }

    private fun loadLoggedInUser() {
        val generation = currentSession.generation()
        viewModelScope.launch {
            val user = try {
                messagesRepository.getMailboxes().toLoggedInUser()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                null
            }
            if (user != null) currentSession.setLoggedInUserIfCurrent(generation, user)
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
