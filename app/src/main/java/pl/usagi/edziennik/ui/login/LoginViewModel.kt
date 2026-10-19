package pl.usagi.edziennik.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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
import pl.usagi.edziennik.network.session.SessionCheck
import pl.usagi.edziennik.network.session.toLoggedInUser
import pl.usagi.edziennik.network.session.authenticationDiagnostic
import pl.usagi.edziennik.repository.MessagesRepository
import pl.usagi.edziennik.repository.StudentRepository
import pl.usagi.edziennik.persistence.changes.SnapshotStore
import javax.inject.Inject
import pl.usagi.edziennik.background.SyncScheduler
import pl.usagi.edziennik.widget.WidgetUpdater
import pl.usagi.edziennik.widget.updateSafely

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
    private val syncScheduler: SyncScheduler,
    private val widgetUpdater: WidgetUpdater,
) : ViewModel() {
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()
    private var sessionVerificationJob: Job? = null
    private var loginJob: Job? = null

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
        if (!credentials.canSubmit || loginJob?.isActive == true) return

        loginJob = viewModelScope.launch {
            currentSession.withManualLogin {
                _uiState.update { state ->
                    state.copy(
                        password = "",
                        isLoading = true,
                        errorMessage = null,
                    )
                }

                currentSession.beginLogin()
                val result = autoDetectLogin.authenticate(
                    username = credentials.login,
                    password = credentials.password,
                )
                currentSession.ensureAuthenticationCurrent()

                when (result) {
                    is AutoDetectLoginResult.Success -> {
                        val pendingProject = currentSession.hasProjectLoginCandidate()
                        if (!pendingProject) currentSession.activate(result)
                        val isValid = sessionManager.isSessionValid()
                        currentSession.ensureAuthenticationCurrent()
                        if (isValid && pendingProject) currentSession.activate(result)
                        if (!isValid && !pendingProject) currentSession.clear()
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
                        currentSession.ensureAuthenticationCurrent()
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
                            scheduleSnapshotSyncIfAccountAvailable()
                        }
                    }
                    AutoDetectLoginResult.InvalidCredentials,
                    AutoDetectLoginResult.TransientError,
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
    }

    fun selectStudent(student: Student) {
        activeStudentManager.setActiveStudent(student)
        _uiState.update { state ->
            state.copy(errorMessage = null)
        }
    }

    fun clearSession() {
        sessionVerificationJob?.cancel()
        loginJob?.cancel()
        val oldAccountId = localSnapshotIdentity.clearAccount()
        currentSession.invalidateAuthentication()
        viewModelScope.launch {
            authenticationDiagnostic("UI_LOGOUT", "EXPLICIT_REQUEST")
            widgetUpdater.updateSafely()
            syncScheduler.cancel()
            currentSession.withAuthentication {
                sessionManager.clearSession()
                currentSession.clear()
                autoLoginSettings.setAutoLoginEnabled(false)
                autoLoginSettings.setAuthenticationProvider(null)
                activeStudentManager.clear()
                oldAccountId?.let { deleteSnapshotsSafely(it) }
                _uiState.update {
                    LoginUiState(
                        isCheckingSession = false,
                        rememberCredentials = credentialStore.hasCredentials(),
                    )
                }
            }
        }
    }

    fun clearStoredCredentials() {
        viewModelScope.launch {
            currentSession.withAuthentication {
                authenticationDiagnostic("UI_CREDENTIALS", "EXPLICIT_REMOVE_REQUEST")
                credentialStore.clear()
                autoLoginSettings.setAutoLoginEnabled(false)
                autoLoginSettings.setAuthenticationProvider(null)
                _uiState.update { state ->
                    state.copy(rememberCredentials = false)
                }
            }
        }
    }

    fun retrySessionVerification() {
        if (_uiState.value.sessionVerificationError != null && !_uiState.value.isCheckingSession) {
            checkPersistedSession()
        }
    }

    fun openManualLogin() {
        if (_uiState.value.sessionVerificationError != null && !_uiState.value.isCheckingSession) {
            showLoginForm()
        }
    }

    private fun showLoginForm() {
        _uiState.update { it.copy(
            isCheckingSession = false,
            isSessionValid = false,
            isLoadingStudents = false,
            sessionVerificationError = null,
            errorMessage = null,
        ) }
    }

    private fun checkPersistedSession() {
        _uiState.update { it.copy(isCheckingSession = true, sessionVerificationError = null, errorMessage = null) }
        sessionVerificationJob = viewModelScope.launch {
            currentSession.withAuthentication {
                if (localSnapshotIdentity.currentAccountId() == null) {
                    showLoginForm()
                    return@withAuthentication
                }
                val savedProvider = autoLoginSettings.getAuthenticationProvider()
                val provider = currentSession.provider ?: savedProvider ?: AuthenticationProvider.PROJECT_UONET
                val hasCookies = currentSession.hasSessionCookies(provider)
                val credentialsWithoutCookies = if (!hasCookies && savedProvider != null && autoLoginSettings.isAutoLoginEnabled()) {
                    credentialStore.load()?.takeIf { it.username.isNotBlank() && it.password.isNotBlank() }
                } else null
                currentSession.ensureAuthenticationCurrent()
                if (!hasCookies && credentialsWithoutCookies == null) {
                    showLoginForm()
                    return@withAuthentication
                }
                if (hasCookies) {
                    if (currentSession.provider == null) currentSession.restore(provider)
                    val checked = sessionManager.checkSession()
                    currentSession.ensureAuthenticationCurrent()
                    authenticationDiagnostic("UI_RESTORE_SESSION", checked.name)
                    if (checked != SessionCheck.VALID && checked != SessionCheck.EXPIRED) {
                        showSessionVerificationError(checked)
                        return@withAuthentication
                    }
                    if (checked == SessionCheck.VALID) {
                        if (savedProvider == null) autoLoginSettings.setAuthenticationProvider(provider)
                        loadLoggedInUser()
                        _uiState.update { state ->
                            state.copy(
                                isCheckingSession = false,
                                isSessionValid = true,
                                errorMessage = null,
                            )
                        }
                        loadStudents()
                        scheduleSnapshotSyncIfAccountAvailable()
                        return@withAuthentication
                    }

                    sessionManager.clearSession()
                    currentSession.clear()
                    authenticationDiagnostic("UI_RESTORE_SESSION", "IN_MEMORY_SESSION_DETACHED")
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
                    return@withAuthentication
                }

                val storedCredentials = credentialsWithoutCookies ?: credentialStore.load()
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
                    return@withAuthentication
                }

                _uiState.update { state ->
                    state.copy(rememberCredentials = true)
                }

                if (savedProvider == null) autoLoginSettings.setAutoLoginEnabled(false)
                val renewed = savedProvider?.let { renewSession(storedCredentials, it) } ?: false
                if (_uiState.value.sessionVerificationError != null) return@withAuthentication
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
                    scheduleSnapshotSyncIfAccountAvailable()
                }
            }
        }
    }

    private suspend fun renewSession(
        credentials: StoredCredentials,
        provider: AuthenticationProvider,
    ): Boolean {
        currentSession.beginLogin()
        val result = autoDetectLogin.authenticateWithProvider(
            provider = provider,
            username = credentials.username,
            password = credentials.password,
        )
        currentSession.ensureAuthenticationCurrent()
        authenticationDiagnostic("UI_AUTO_LOGIN", when (result) {
            is AutoDetectLoginResult.Success -> "AUTHENTICATED"
            AutoDetectLoginResult.InvalidCredentials -> "INVALID_CREDENTIALS"
            AutoDetectLoginResult.TransientError -> "TRANSIENT_ERROR"
            AutoDetectLoginResult.TechnicalError -> "TECHNICAL_ERROR"
        })
        if (result !is AutoDetectLoginResult.Success) return false
        currentSession.activate(result)
        val checked = sessionManager.checkSession()
        currentSession.ensureAuthenticationCurrent()
        if (checked != SessionCheck.VALID && checked != SessionCheck.EXPIRED) {
            showSessionVerificationError(checked)
            return false
        }
        if (checked == SessionCheck.EXPIRED) {
            authenticationDiagnostic("UI_AUTO_LOGIN_VERIFY", "EXPIRED_CLEAR_SESSION")
            sessionManager.clearSession()
        }
        return checked == SessionCheck.VALID
    }

    private fun showSessionVerificationError(checked: SessionCheck) {
        _uiState.update { it.copy(isCheckingSession = false, sessionVerificationError = checked) }
    }

    private fun scheduleSnapshotSyncIfAccountAvailable() {
        runCatching {
            if (localSnapshotIdentity.currentAccountId() != null) syncScheduler.schedule()
        }
    }

    private suspend fun replaceSnapshotAccount() {
        val oldId = runCatching { localSnapshotIdentity.currentAccountId() }.getOrNull()
        runCatching { localSnapshotIdentity.replaceAccountId() }
            .onFailure { runCatching { localSnapshotIdentity.clearAccount() } }
        widgetUpdater.updateSafely()
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
                currentSession.ensureAuthenticationCurrent()
                activeStudentManager.initialize(students)
                val selected = runCatching { localSnapshotIdentity.selectedStudentScopeId() }.getOrNull()
                if (selected != null) {
                    students.firstOrNull {
                        runCatching { localSnapshotIdentity.studentScopeId(it.diaryId) }.getOrNull() == selected
                    }?.let(activeStudentManager::setActiveStudent)
                }
                _uiState.update { state ->
                    state.copy(
                        isLoadingStudents = false,
                        students = students,
                        errorMessage = if (students.isEmpty()) "Konto nie ma przypisanych uczniów." else null,
                    )
                }
            }
            .onFailure {
                if (it is CancellationException) throw it
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
