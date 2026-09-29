package pl.usagi.edziennik.ui.login

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import pl.usagi.edziennik.MainDispatcherRule
import pl.usagi.edziennik.domain.ActiveStudentManager
import pl.usagi.edziennik.domain.Student
import pl.usagi.edziennik.domain.Mailbox
import pl.usagi.edziennik.domain.MessageDetails
import pl.usagi.edziennik.domain.MessageMailboxType
import pl.usagi.edziennik.network.credentials.CredentialStore
import pl.usagi.edziennik.network.credentials.StoredCredentials
import pl.usagi.edziennik.network.session.AuthenticationException
import pl.usagi.edziennik.network.eduvulcan.EduVulcanLoginResult
import pl.usagi.edziennik.network.session.AuthenticationProvider
import pl.usagi.edziennik.network.session.AutoDetectLogin
import pl.usagi.edziennik.network.session.AutoLoginSettings
import pl.usagi.edziennik.network.session.CurrentAuthenticationSession
import pl.usagi.edziennik.network.session.LoggedInUser
import pl.usagi.edziennik.network.session.LoggedInUserType
import pl.usagi.edziennik.network.session.SessionManager
import pl.usagi.edziennik.repository.MessagesRepository
import pl.usagi.edziennik.repository.ReceivedMessagesPage
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
        val currentSession = CurrentAuthenticationSession(CookieJar.NO_COOKIES)
        val user = LoggedInUser("Anna Kowalska", LoggedInUserType.PARENT)

        val viewModel = createViewModel(
            sessionManager, credentialStore, autoLoginSettings,
            currentSession = currentSession,
            messagesRepository = FakeMessagesRepository(
                mailboxes = listOf(Mailbox("key", "Anna Kowalska - R - Jan Kowalski - (ABC)", 0)),
            ),
            autoDetectLogin = AutoDetectLogin(
                authenticateEduVulcan = { _, _ -> error("Must not authenticate again") },
                loginProjectUonet = { _, _ -> error("Must not authenticate again") },
            ),
        )
        advanceUntilIdle()

        assertEquals(0, credentialStore.loadCalls)
        assertEquals(0, sessionManager.loginCalls)
        assertTrue(autoLoginSettings.isAutoLoginEnabledCalls == 0)
        assertEquals(AuthenticationProvider.PROJECT_UONET, currentSession.provider)
        assertEquals(user, currentSession.loggedInUser.value)
        assertEquals(user, viewModel.uiState.value.loggedInUser)
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
    fun rememberedEduVulcanReauthenticatesWithoutProjectFallback() = runTest {
        val settings = FakeAutoLoginSettings(enabled = true, provider = AuthenticationProvider.EDUVULCAN)
        val session = CurrentAuthenticationSession(CookieJar.NO_COOKIES)
        val client = OkHttpClient.Builder().cookieJar(CookieJar.NO_COOKIES).build()
        var eduCalls = 0
        val viewModel = createViewModel(
            sessionManager = FakeSessionManager(isSessionValidResult = true),
            credentialStore = FakeCredentialStore(StoredCredentials("user", "password")),
            autoLoginSettings = settings,
            currentSession = session,
            autoDetectLogin = AutoDetectLogin(
                authenticateEduVulcan = { _, _ ->
                    eduCalls++
                    EduVulcanLoginResult.Authenticated(client)
                },
                loginProjectUonet = { _, _ -> error("Project provider must not run") },
            ),
        )

        advanceUntilIdle()

        assertEquals(1, eduCalls)
        assertEquals(AuthenticationProvider.EDUVULCAN, session.provider)
        assertTrue(viewModel.uiState.value.isSessionValid)
    }

    @Test
    fun rememberedProjectReauthenticatesWithoutEduDetection() = runTest {
        val sessionManager = FakeSessionManager(isSessionValidResult = true)
        val session = CurrentAuthenticationSession(CookieJar.NO_COOKIES)
        val viewModel = createViewModel(
            sessionManager = sessionManager,
            credentialStore = FakeCredentialStore(StoredCredentials("user", "password")),
            autoLoginSettings = FakeAutoLoginSettings(enabled = true, provider = AuthenticationProvider.PROJECT_UONET),
            currentSession = session,
            autoDetectLogin = AutoDetectLogin(
                authenticateEduVulcan = { _, _ -> error("EduVULCAN detection must not run") },
                loginProjectUonet = sessionManager::login,
            ),
        )

        advanceUntilIdle()

        assertEquals(1, sessionManager.loginCalls)
        assertEquals(AuthenticationProvider.PROJECT_UONET, session.provider)
        assertTrue(viewModel.uiState.value.isSessionValid)
    }

    @Test
    fun technicalErrorFromRememberedEduVulcanDoesNotTryProject() = runTest {
        val session = CurrentAuthenticationSession(CookieJar.NO_COOKIES)
        val viewModel = createViewModel(
            credentialStore = FakeCredentialStore(StoredCredentials("user", "password")),
            autoLoginSettings = FakeAutoLoginSettings(enabled = true, provider = AuthenticationProvider.EDUVULCAN),
            currentSession = session,
            autoDetectLogin = AutoDetectLogin(
                authenticateEduVulcan = { _, _ -> EduVulcanLoginResult.TechnicalError },
                loginProjectUonet = { _, _ -> error("Fallback must not run") },
            ),
        )

        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSessionValid)
        assertNull(session.provider)
    }

    @Test
    fun missingRememberedProviderDoesNotGuessProviderForAutoLogin() = runTest {
        val sessionManager = FakeSessionManager()
        val settings = FakeAutoLoginSettings(enabled = true, provider = null)
        val viewModel = createViewModel(
            sessionManager = sessionManager,
            credentialStore = FakeCredentialStore(StoredCredentials("user", "password")),
            autoLoginSettings = settings,
            autoDetectLogin = AutoDetectLogin(
                authenticateEduVulcan = { _, _ -> error("Must not run") },
                loginProjectUonet = { _, _ -> error("Must not run") },
            ),
        )

        advanceUntilIdle()

        assertEquals(0, sessionManager.loginCalls)
        assertFalse(settings.enabled)
        assertFalse(viewModel.uiState.value.isSessionValid)
    }

    @Test
    fun manualLoginWithoutRememberCannotReactivateOldCredentialsOnRestart() = runTest {
        val settings = FakeAutoLoginSettings(enabled = true)
        val credentials = FakeCredentialStore(StoredCredentials("old-user", "old-password"))
        val firstLaunch = createViewModel(
            credentialStore = credentials,
            autoLoginSettings = settings,
            sessionManager = FakeSessionManager(isSessionValidResult = true),
        )
        advanceUntilIdle()
        firstLaunch.onLoginChanged("new-user")
        firstLaunch.onPasswordChanged("new-password")
        firstLaunch.onRememberCredentialsChanged(false)
        firstLaunch.login()
        advanceUntilIdle()
        val loadsBeforeRestart = credentials.loadCalls

        val nextSessionManager = FakeSessionManager()
        val nextLaunch = createViewModel(
            credentialStore = credentials,
            autoLoginSettings = settings,
            sessionManager = nextSessionManager,
        )
        advanceUntilIdle()

        assertFalse(settings.enabled)
        assertEquals(loadsBeforeRestart, credentials.loadCalls)
        assertEquals(0, nextSessionManager.loginCalls)
        assertFalse(nextLaunch.uiState.value.isSessionValid)
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
            isSessionValidResult = false,
        )
        val credentialStore = FakeCredentialStore()
        val autoLoginSettings = FakeAutoLoginSettings(enabled = false)
        val viewModel = createViewModel(
            sessionManager,
            credentialStore,
            autoLoginSettings,
            autoDetectLogin = AutoDetectLogin(
                authenticateEduVulcan = { _, _ -> EduVulcanLoginResult.InvalidCredentials },
                loginProjectUonet = { _, _ -> Result.failure(AuthenticationException.InvalidCredentials()) },
            ),
        )
        advanceUntilIdle()

        viewModel.onLoginChanged("user")
        viewModel.onPasswordChanged("bad-password")
        viewModel.onRememberCredentialsChanged(true)
        viewModel.login()
        advanceUntilIdle()

        assertNull(credentialStore.savedCredentials)
        assertFalse(autoLoginSettings.enabled)
        assertEquals("", viewModel.uiState.value.password)
        assertEquals("Nieprawidłowy login lub hasło.", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun manualEduVulcanSuccessActivatesReturnedSession() = runTest {
        val sessionClient = OkHttpClient.Builder().cookieJar(CookieJar.NO_COOKIES).build()
        val currentSession = CurrentAuthenticationSession(CookieJar.NO_COOKIES)
        val settings = FakeAutoLoginSettings()
        var projectCalls = 0
        val viewModel = createViewModel(
            sessionManager = FakeSessionManager(isSessionValidResult = true),
            currentSession = currentSession,
            autoLoginSettings = settings,
            autoDetectLogin = AutoDetectLogin(
                authenticateEduVulcan = { _, _ -> EduVulcanLoginResult.Authenticated(sessionClient) },
                loginProjectUonet = { _, _ ->
                    projectCalls++
                    Result.success(Unit)
                },
            ),
        )
        advanceUntilIdle()

        viewModel.onLoginChanged("user")
        viewModel.onPasswordChanged("password")
        viewModel.login()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isSessionValid)
        assertEquals(AuthenticationProvider.EDUVULCAN, currentSession.provider)
        assertEquals(AuthenticationProvider.EDUVULCAN, settings.provider)
        assertEquals(0, projectCalls)
        assertEquals(1, viewModel.uiState.value.students.size)
    }

    @Test
    fun manualLoginLoadsMailboxOwnerIntoCurrentSession() = runTest {
        val currentSession = CurrentAuthenticationSession(CookieJar.NO_COOKIES)
        val user = LoggedInUser("Anna Kowalska", LoggedInUserType.PARENT)
        val viewModel = createViewModel(
            sessionManager = FakeSessionManager(isSessionValidResult = true),
            currentSession = currentSession,
            messagesRepository = FakeMessagesRepository(
                mailboxes = listOf(Mailbox("key", "Anna Kowalska - R - Jan Kowalski - (ABC)", 0)),
            ),
        )
        advanceUntilIdle()

        viewModel.onLoginChanged("user")
        viewModel.onPasswordChanged("password")
        viewModel.login()
        advanceUntilIdle()

        assertEquals(user, currentSession.loggedInUser.value)
        assertEquals(user, viewModel.uiState.value.loggedInUser)
        assertTrue(viewModel.uiState.value.isSessionValid)
    }

    @Test
    fun mailboxFetchFailureDoesNotBlockDashboard() = runTest {
        val viewModel = createViewModel(
            sessionManager = FakeSessionManager(isSessionValidResult = true),
            messagesRepository = FakeMessagesRepository(fail = true),
        )
        advanceUntilIdle()

        viewModel.onLoginChanged("user")
        viewModel.onPasswordChanged("password")
        viewModel.login()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isSessionValid)
        assertEquals(1, viewModel.uiState.value.students.size)
        assertNull(viewModel.uiState.value.loggedInUser)
    }

    @Test
    fun manualProjectUonetSuccessAfterFallbackActivatesProjectSession() = runTest {
        val currentSession = CurrentAuthenticationSession(CookieJar.NO_COOKIES)
        val settings = FakeAutoLoginSettings()
        var projectCalls = 0
        val viewModel = createViewModel(
            sessionManager = FakeSessionManager(isSessionValidResult = true),
            currentSession = currentSession,
            autoLoginSettings = settings,
            autoDetectLogin = AutoDetectLogin(
                authenticateEduVulcan = { _, _ -> EduVulcanLoginResult.InvalidCredentials },
                loginProjectUonet = { _, _ ->
                    projectCalls++
                    Result.success(Unit)
                },
            ),
        )
        advanceUntilIdle()

        viewModel.onLoginChanged("user")
        viewModel.onPasswordChanged("password")
        viewModel.login()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isSessionValid)
        assertEquals(AuthenticationProvider.PROJECT_UONET, currentSession.provider)
        assertEquals(AuthenticationProvider.PROJECT_UONET, settings.provider)
        assertEquals(1, projectCalls)
        assertEquals(1, viewModel.uiState.value.students.size)
    }

    @Test
    fun manualTechnicalErrorIsNotShownAsInvalidCredentials() = runTest {
        val sessionManager = FakeSessionManager()
        val viewModel = createViewModel(
            sessionManager = sessionManager,
            autoDetectLogin = AutoDetectLogin(
                authenticateEduVulcan = { _, _ -> EduVulcanLoginResult.TechnicalError },
                loginProjectUonet = { _, _ -> error("Fallback must not run") },
            ),
        )
        advanceUntilIdle()

        viewModel.onLoginChanged("user")
        viewModel.onPasswordChanged("password")
        viewModel.login()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSessionValid)
        assertEquals("Proces logowania UONET+ nie zakończył się poprawnie.", viewModel.uiState.value.errorMessage)
        assertEquals(0, sessionManager.loginCalls)
    }

    @Test
    fun explicitLogoutClearsSessionAndDisablesAutoLoginWithoutClearingCredentials() = runTest {
        val sessionManager = FakeSessionManager(validatePersistedSessionResult = true)
        val credentialStore = FakeCredentialStore(
            storedCredentials = StoredCredentials("user", "password"),
        )
        val autoLoginSettings = FakeAutoLoginSettings(enabled = true)
        val currentSession = CurrentAuthenticationSession(CookieJar.NO_COOKIES)
        val viewModel = createViewModel(sessionManager, credentialStore, autoLoginSettings,
            currentSession = currentSession)
        advanceUntilIdle()

        viewModel.clearSession()
        advanceUntilIdle()

        assertTrue(sessionManager.clearSessionCalled)
        assertFalse(autoLoginSettings.enabled)
        assertNull(autoLoginSettings.provider)
        assertNull(currentSession.provider)
        assertFalse(credentialStore.clearCalled)
        assertFalse(viewModel.uiState.value.isSessionValid)

        val nextLaunch = createViewModel(
            sessionManager = FakeSessionManager(),
            credentialStore = credentialStore,
            autoLoginSettings = autoLoginSettings,
        )
        advanceUntilIdle()
        assertFalse(nextLaunch.uiState.value.isSessionValid)
        assertEquals(0, credentialStore.loadCalls)
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
        assertNull(autoLoginSettings.provider)
        assertFalse(viewModel.uiState.value.rememberCredentials)
    }

    private fun createViewModel(
        sessionManager: FakeSessionManager = FakeSessionManager(),
        credentialStore: FakeCredentialStore = FakeCredentialStore(),
        autoLoginSettings: FakeAutoLoginSettings = FakeAutoLoginSettings(),
        studentRepository: StudentRepository = FakeStudentRepository(),
        autoDetectLogin: AutoDetectLogin = AutoDetectLogin(
            authenticateEduVulcan = { _, _ -> EduVulcanLoginResult.InvalidCredentials },
            loginProjectUonet = sessionManager::login,
        ),
        currentSession: CurrentAuthenticationSession = CurrentAuthenticationSession(CookieJar.NO_COOKIES),
        messagesRepository: MessagesRepository = FakeMessagesRepository(),
    ) = LoginViewModel(
        sessionManager = sessionManager,
        autoDetectLogin = autoDetectLogin,
        currentSession = currentSession,
        messagesRepository = messagesRepository,
        credentialStore = credentialStore,
        autoLoginSettings = autoLoginSettings,
        studentRepository = studentRepository,
        activeStudentManager = ActiveStudentManager(),
    )

    private class FakeMessagesRepository(
        private val mailboxes: List<Mailbox> = emptyList(),
        private val fail: Boolean = false,
    ) : MessagesRepository {
        override val unreadCountInvalidations: Flow<Unit> = emptyFlow()

        override suspend fun getMailboxes(): List<Mailbox> {
            if (fail) throw IllegalStateException("Mailboxes unavailable")
            return mailboxes
        }

        override suspend fun getUnreadCount(): Int = error("Unused")
        override suspend fun getMessagesPage(
            mailboxType: MessageMailboxType,
            lastMessageId: Int,
            pageSize: Int,
        ): ReceivedMessagesPage = error("Unused")

        override suspend fun getMessageDetails(apiGlobalKey: String): MessageDetails = error("Unused")
        override suspend fun downloadAttachment(url: String, destination: java.io.File): String? = error("Unused")
        override suspend fun markMessageAsRead(apiGlobalKey: String) = error("Unused")
    }

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
        var provider: AuthenticationProvider? = AuthenticationProvider.PROJECT_UONET,
    ) : AutoLoginSettings {
        var isAutoLoginEnabledCalls = 0

        override suspend fun isAutoLoginEnabled(): Boolean {
            isAutoLoginEnabledCalls += 1
            return enabled
        }

        override suspend fun setAutoLoginEnabled(enabled: Boolean) {
            this.enabled = enabled
        }

        override suspend fun getAuthenticationProvider(): AuthenticationProvider? = provider

        override suspend fun setAuthenticationProvider(provider: AuthenticationProvider?) {
            this.provider = provider
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
