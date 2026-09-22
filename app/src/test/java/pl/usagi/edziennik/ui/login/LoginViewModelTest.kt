package pl.usagi.edziennik.ui.login

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import pl.usagi.edziennik.MainDispatcherRule
import pl.usagi.edziennik.domain.ActiveStudentManager
import pl.usagi.edziennik.domain.Student
import pl.usagi.edziennik.network.credentials.CredentialStore
import pl.usagi.edziennik.network.credentials.StoredCredentials
import pl.usagi.edziennik.network.session.AuthenticationException
import pl.usagi.edziennik.network.session.AutoLoginSettings
import pl.usagi.edziennik.network.session.SessionManager
import pl.usagi.edziennik.repository.StudentRepository

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun validSessionDoesNotReadCredentialsOrLoginAgain() = runTest {
        val sessionManager = FakeSessionManager(validatePersistedSessionResult = true)
        val credentialStore = FakeCredentialStore(
            storedCredentials = StoredCredentials("user", "password"),
        )
        val autoLoginSettings = FakeAutoLoginSettings(enabled = false)

        createViewModel(sessionManager, credentialStore, autoLoginSettings)
        advanceUntilIdle()

        assertEquals(0, credentialStore.loadCalls)
        assertEquals(0, sessionManager.loginCalls)
        assertTrue(autoLoginSettings.isAutoLoginEnabledCalls == 0)
    }

    @Test
    fun invalidSessionWithStoredCredentialsAutoLogsIn() = runTest {
        val sessionManager = FakeSessionManager(
            validatePersistedSessionResult = false,
            loginResult = Result.success(Unit),
            isSessionValidResult = true,
        )
        val credentialStore = FakeCredentialStore(
            storedCredentials = StoredCredentials("user", "password"),
        )
        val autoLoginSettings = FakeAutoLoginSettings(enabled = true)
        val viewModel = createViewModel(sessionManager, credentialStore, autoLoginSettings)

        advanceUntilIdle()

        assertEquals(1, autoLoginSettings.isAutoLoginEnabledCalls)
        assertEquals(1, credentialStore.loadCalls)
        assertEquals(1, sessionManager.loginCalls)
        assertTrue(viewModel.uiState.value.isSessionValid)
    }

    @Test
    fun invalidSessionWithAutoLoginDisabledDoesNotReadCredentialsOrLogin() = runTest {
        val sessionManager = FakeSessionManager(validatePersistedSessionResult = false)
        val credentialStore = FakeCredentialStore(
            storedCredentials = StoredCredentials("user", "password"),
        )
        val autoLoginSettings = FakeAutoLoginSettings(enabled = false)
        val viewModel = createViewModel(sessionManager, credentialStore, autoLoginSettings)

        advanceUntilIdle()

        assertEquals(1, autoLoginSettings.isAutoLoginEnabledCalls)
        assertEquals(0, credentialStore.loadCalls)
        assertEquals(0, sessionManager.loginCalls)
        assertFalse(viewModel.uiState.value.isCheckingSession)
        assertFalse(viewModel.uiState.value.isSessionValid)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun invalidSessionWithoutCredentialsRequiresLogin() = runTest {
        val sessionManager = FakeSessionManager(validatePersistedSessionResult = false)
        val credentialStore = FakeCredentialStore()
        val autoLoginSettings = FakeAutoLoginSettings(enabled = true)
        val viewModel = createViewModel(sessionManager, credentialStore, autoLoginSettings)

        advanceUntilIdle()

        assertEquals(1, credentialStore.loadCalls)
        assertFalse(autoLoginSettings.enabled)
        assertEquals(0, sessionManager.loginCalls)
        assertFalse(viewModel.uiState.value.isCheckingSession)
        assertFalse(viewModel.uiState.value.isSessionValid)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun failedAutoLoginRequiresLoginWithoutLooping() = runTest {
        val sessionManager = FakeSessionManager(
            validatePersistedSessionResult = false,
            loginResult = Result.failure(AuthenticationException.InvalidCredentials()),
            isSessionValidResult = false,
        )
        val credentialStore = FakeCredentialStore(
            storedCredentials = StoredCredentials("user", "bad-password"),
        )
        val autoLoginSettings = FakeAutoLoginSettings(enabled = true)
        val viewModel = createViewModel(sessionManager, credentialStore, autoLoginSettings)

        advanceUntilIdle()

        assertEquals(1, sessionManager.loginCalls)
        assertFalse(viewModel.uiState.value.isCheckingSession)
        assertFalse(viewModel.uiState.value.isSessionValid)
        assertEquals("Sesja wygasła. Zaloguj się ponownie.", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun manualLoginSuccessWithRememberSavesCredentials() = runTest {
        val sessionManager = FakeSessionManager(
            validatePersistedSessionResult = false,
            loginResult = Result.success(Unit),
            isSessionValidResult = true,
        )
        val credentialStore = FakeCredentialStore()
        val autoLoginSettings = FakeAutoLoginSettings(enabled = false)
        val viewModel = createViewModel(sessionManager, credentialStore, autoLoginSettings)
        advanceUntilIdle()

        viewModel.onLoginChanged("user")
        viewModel.onPasswordChanged("password")
        viewModel.onRememberCredentialsChanged(true)
        viewModel.login()
        advanceUntilIdle()

        assertEquals(StoredCredentials("user", "password"), credentialStore.savedCredentials)
        assertTrue(autoLoginSettings.enabled)
        assertEquals("", viewModel.uiState.value.password)
    }

    @Test
    fun manualLoginSuccessWithoutRememberDoesNotSaveCredentials() = runTest {
        val sessionManager = FakeSessionManager(
            validatePersistedSessionResult = false,
            loginResult = Result.success(Unit),
            isSessionValidResult = true,
        )
        val credentialStore = FakeCredentialStore(
            storedCredentials = StoredCredentials("old-user", "old-password"),
        )
        val autoLoginSettings = FakeAutoLoginSettings(enabled = false)
        val viewModel = createViewModel(sessionManager, credentialStore, autoLoginSettings)
        advanceUntilIdle()

        viewModel.onLoginChanged("user")
        viewModel.onPasswordChanged("password")
        viewModel.onRememberCredentialsChanged(false)
        viewModel.login()
        advanceUntilIdle()

        assertNull(credentialStore.savedCredentials)
        assertFalse(credentialStore.clearCalled)
        assertFalse(autoLoginSettings.enabled)
    }

    @Test
    fun manualLoginFailureWithRememberDoesNotSaveCredentials() = runTest {
        val sessionManager = FakeSessionManager(
            validatePersistedSessionResult = false,
            loginResult = Result.failure(AuthenticationException.InvalidCredentials()),
            isSessionValidResult = false,
        )
        val credentialStore = FakeCredentialStore()
        val autoLoginSettings = FakeAutoLoginSettings(enabled = false)
        val viewModel = createViewModel(sessionManager, credentialStore, autoLoginSettings)
        advanceUntilIdle()

        viewModel.onLoginChanged("user")
        viewModel.onPasswordChanged("bad-password")
        viewModel.onRememberCredentialsChanged(true)
        viewModel.login()
        advanceUntilIdle()

        assertNull(credentialStore.savedCredentials)
        assertFalse(autoLoginSettings.enabled)
        assertEquals("", viewModel.uiState.value.password)
    }

    @Test
    fun explicitLogoutClearsSessionAndDisablesAutoLoginWithoutClearingCredentials() = runTest {
        val sessionManager = FakeSessionManager(validatePersistedSessionResult = true)
        val credentialStore = FakeCredentialStore(
            storedCredentials = StoredCredentials("user", "password"),
        )
        val autoLoginSettings = FakeAutoLoginSettings(enabled = true)
        val viewModel = createViewModel(sessionManager, credentialStore, autoLoginSettings)
        advanceUntilIdle()

        viewModel.clearSession()
        advanceUntilIdle()

        assertTrue(sessionManager.clearSessionCalled)
        assertFalse(autoLoginSettings.enabled)
        assertFalse(credentialStore.clearCalled)
        assertFalse(viewModel.uiState.value.isSessionValid)
    }

    @Test
    fun clearStoredCredentialsClearsCredentialsAndDisablesAutoLogin() = runTest {
        val credentialStore = FakeCredentialStore(
            storedCredentials = StoredCredentials("user", "password"),
        )
        val autoLoginSettings = FakeAutoLoginSettings(enabled = true)
        val viewModel = createViewModel(
            sessionManager = FakeSessionManager(validatePersistedSessionResult = false),
            credentialStore = credentialStore,
            autoLoginSettings = autoLoginSettings,
        )
        advanceUntilIdle()

        viewModel.clearStoredCredentials()
        advanceUntilIdle()

        assertTrue(credentialStore.clearCalled)
        assertFalse(autoLoginSettings.enabled)
        assertFalse(viewModel.uiState.value.rememberCredentials)
    }

    private fun createViewModel(
        sessionManager: FakeSessionManager = FakeSessionManager(),
        credentialStore: FakeCredentialStore = FakeCredentialStore(),
        autoLoginSettings: FakeAutoLoginSettings = FakeAutoLoginSettings(),
        studentRepository: StudentRepository = FakeStudentRepository(),
    ) = LoginViewModel(
        sessionManager = sessionManager,
        credentialStore = credentialStore,
        autoLoginSettings = autoLoginSettings,
        studentRepository = studentRepository,
        activeStudentManager = ActiveStudentManager(),
    )

    private class FakeSessionManager(
        private val validatePersistedSessionResult: Boolean = false,
        private val loginResult: Result<Unit> = Result.success(Unit),
        private val isSessionValidResult: Boolean = false,
    ) : SessionManager {
        var loginCalls = 0
        var clearSessionCalled = false

        override suspend fun login(
            username: String,
            password: String,
        ): Result<Unit> {
            loginCalls += 1
            return loginResult
        }

        override suspend fun isSessionValid(): Boolean = isSessionValidResult

        override suspend fun validatePersistedSession(): Boolean = validatePersistedSessionResult

        override suspend fun clearSession() {
            clearSessionCalled = true
        }
    }

    private class FakeCredentialStore(
        private val storedCredentials: StoredCredentials? = null,
    ) : CredentialStore {
        var loadCalls = 0
        var savedCredentials: StoredCredentials? = null
        var clearCalled = false

        override suspend fun save(username: String, password: String) {
            savedCredentials = StoredCredentials(username, password)
        }

        override suspend fun load(): StoredCredentials? {
            loadCalls += 1
            return storedCredentials
        }

        override suspend fun clear() {
            clearCalled = true
            savedCredentials = null
        }

        override suspend fun hasCredentials(): Boolean = storedCredentials != null || savedCredentials != null
    }

    private class FakeAutoLoginSettings(
        var enabled: Boolean = false,
    ) : AutoLoginSettings {
        var isAutoLoginEnabledCalls = 0

        override suspend fun isAutoLoginEnabled(): Boolean {
            isAutoLoginEnabledCalls += 1
            return enabled
        }

        override suspend fun setAutoLoginEnabled(enabled: Boolean) {
            this.enabled = enabled
        }
    }

    private class FakeStudentRepository : StudentRepository {
        override suspend fun getStudents(): List<Student> = listOf(
            Student(
                key = "student-key",
                diaryId = 1,
                name = "Jan Kowalski",
                className = "4A",
                schoolName = "Szkola",
            ),
        )
    }
}
