package pl.usagi.edziennik.ui.login

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import okhttp3.CookieJar
import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import pl.usagi.edziennik.MainDispatcherRule
import pl.usagi.edziennik.domain.ActiveStudentManager
import pl.usagi.edziennik.domain.Student
import pl.usagi.edziennik.domain.changes.ChangeEvent
import pl.usagi.edziennik.domain.changes.LocalAccountId
import pl.usagi.edziennik.persistence.changes.LocalIdentityStore
import pl.usagi.edziennik.persistence.changes.LocalSnapshotIdentity
import pl.usagi.edziennik.domain.changes.SnapshotScope
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
import pl.usagi.edziennik.network.session.SessionCheck
import pl.usagi.edziennik.repository.MessagesRepository
import pl.usagi.edziennik.repository.ReceivedMessagesPage
import pl.usagi.edziennik.repository.StudentRepository
import pl.usagi.edziennik.persistence.changes.SnapshotStore
import pl.usagi.edziennik.persistence.changes.SnapshotWrite
import pl.usagi.edziennik.background.SyncScheduler
import androidx.work.ExistingPeriodicWorkPolicy
import pl.usagi.edziennik.background.PeriodicSyncScheduler
import pl.usagi.edziennik.widget.WidgetUpdater
import pl.usagi.edziennik.widget.LocalWidgetsUpdater
import pl.usagi.edziennik.widget.TimetableWidgetUpdater
import pl.usagi.edziennik.widget.TasksWidgetUpdater
import pl.usagi.edziennik.widget.TodayTimetableContentReader
import pl.usagi.edziennik.widget.UpcomingTasksContentReader
import pl.usagi.edziennik.widget.TodayTimetablePresentation
import pl.usagi.edziennik.widget.UpcomingTasksPresentation
import pl.usagi.edziennik.persistence.changes.LocalTimetableReader
import pl.usagi.edziennik.persistence.changes.LocalTasksReader
import pl.usagi.edziennik.persistence.changes.SnapshotSyncManager
import pl.usagi.edziennik.persistence.changes.CurrentSnapshotData
import pl.usagi.edziennik.persistence.changes.SyncCompleteness
import pl.usagi.edziennik.persistence.changes.taskSnapshotWeeks
import pl.usagi.edziennik.domain.changes.SnapshotArea
import pl.usagi.edziennik.domain.SchoolTask
import pl.usagi.edziennik.domain.SchoolTaskDetails
import pl.usagi.edziennik.domain.SchoolTaskType
import pl.usagi.edziennik.domain.TimetableEntry
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import pl.usagi.edziennik.dto.ContextDto
import pl.usagi.edziennik.mapper.toDomainStudents
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test fun bothProvidersCanLogInWithContextUnitIdMissingNullOrNumeric() = runTest {
        for (provider in AuthenticationProvider.entries) {
            for (field in listOf("", ",\"idJednostkaSkladowa\":null", ",\"idJednostkaSkladowa\":1220")) {
                val current = sessionWithCookies()
                val session = FakeSessionManager(isSessionValidResult = true)
                val auth = AutoDetectLogin(
                    authenticateEduVulcan = { _, _ ->
                        if (provider == AuthenticationProvider.EDUVULCAN) EduVulcanLoginResult.Authenticated(OkHttpClient())
                        else EduVulcanLoginResult.InvalidCredentials
                    },
                    loginProjectUonet = session::login,
                )
                val vm = createViewModel(sessionManager = session, currentSession = current,
                    autoDetectLogin = auth, studentRepository = contextRepository(field))
                advanceUntilIdle()
                vm.onLoginChanged("test")
                vm.onPasswordChanged("test")
                vm.login()
                advanceUntilIdle()
                assertTrue(vm.uiState.value.isSessionValid)
                assertFalse(vm.uiState.value.isLoadingStudents)
                assertNull(vm.uiState.value.errorMessage)
                assertEquals(provider, current.provider)
                assertEquals(if (field.contains("1220")) 1220 else null, vm.uiState.value.activeStudent!!.organizationalUnitId)
            }
        }
    }

    @Test fun savedSessionsRestoreWithContextUnitIdForBothProviders() = runTest {
        for (provider in AuthenticationProvider.entries) {
            val current = sessionWithCookies()
            val vm = createViewModel(sessionManager = FakeSessionManager(validatePersistedSessionResult = true),
                autoLoginSettings = FakeAutoLoginSettings(provider = provider), currentSession = current,
                studentRepository = contextRepository(",\"idJednostkaSkladowa\":1220"))
            advanceUntilIdle()
            assertTrue(vm.uiState.value.isSessionValid)
            assertEquals(provider, current.provider)
            assertEquals(1220, vm.uiState.value.activeStudent!!.organizationalUnitId)
        }
    }

    @Test fun multipleContextStudentsKeepSessionValidAndCanBeSelected() = runTest {
        val vm = createViewModel(sessionManager = FakeSessionManager(validatePersistedSessionResult = true),
            studentRepository = contextRepository(",\"idJednostkaSkladowa\":1220", multiple = true))
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isSessionValid)
        assertEquals(2, vm.uiState.value.students.size)
        assertNull(vm.uiState.value.activeStudent)
        vm.selectStudent(vm.uiState.value.students.last())
        advanceUntilIdle()
        assertEquals(999, vm.uiState.value.activeStudent!!.organizationalUnitId)
    }

    @Test fun contextDecodingFailureDoesNotProduceAuthenticationTechnicalError() = runTest {
        val vm = createViewModel(sessionManager = FakeSessionManager(validatePersistedSessionResult = true),
            studentRepository = contextRepository(",\"idJednostkaSkladowa\":{}"))
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isSessionValid)
        assertEquals("Nie udało się pobrać danych ucznia.", vm.uiState.value.errorMessage)
    }

    private fun contextRepository(field: String, multiple: Boolean = false) = object : StudentRepository {
        override suspend fun getStudents(): List<Student> {
            val second = if (multiple) "," + """{"key":"other-runtime","idDziennik":2,"uczen":"Drugi uczeń","oddzial":"2A","jednostka":"Szkoła","idJednostkaSkladowa":999}""" else ""
            val body = """{"uczniowie":[{"key":"runtime","idDziennik":1,"uczen":"Uczeń","oddzial":"1A","jednostka":"Szkoła"$field}$second]}"""
            return Json { ignoreUnknownKeys = true; explicitNulls = false }.decodeFromString<ContextDto>(body).students.toDomainStudents()
        }
    }

    @Test
    fun validSessionDoesNotReadCredentialsOrLoginAgain() = runTest {
        val sessionManager = FakeSessionManager(validatePersistedSessionResult = true)
        val credentialStore = FakeCredentialStore(
            storedCredentials = StoredCredentials("user", "password"),
        )
        val autoLoginSettings = FakeAutoLoginSettings(enabled = false)
        val currentSession = sessionWithCookies()
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
        val session = sessionWithCookies()
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
        val session = sessionWithCookies()
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
        val session = sessionWithCookies()
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
    fun successfulManualLoginReplacesPreviousLocalSnapshotAccount() = runTest {
        val identity = LocalSnapshotIdentity(FakeLocalIdentityStore())
        val previousId = identity.ensureAccountId()
        val snapshots = FakeSnapshotStore()
        val sessionManager = FakeSessionManager(isSessionValidResult = true)
        val viewModel = createViewModel(
            sessionManager = sessionManager,
            localSnapshotIdentity = identity,
            snapshotStore = snapshots,
        )
        advanceUntilIdle()

        viewModel.onLoginChanged("user")
        viewModel.onPasswordChanged("password")
        viewModel.login()
        advanceUntilIdle()

        assertNotEquals(previousId, identity.currentAccountId())
        assertEquals(listOf(previousId), snapshots.deletedAccounts)
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
        val currentSession = sessionWithCookies()
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
        val currentSession = sessionWithCookies()
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
        val currentSession = sessionWithCookies()
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
        val currentSession = sessionWithCookies()
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
    fun logoutInvalidatesLocalSnapshotAccountAndDeletesItsRows() = runTest {
        val identity = LocalSnapshotIdentity(FakeLocalIdentityStore()).apply { ensureAccountId() }
        val snapshots = FakeSnapshotStore()
        val scheduler = FakeSyncScheduler()
        val viewModel = createViewModel(
            sessionManager = FakeSessionManager(validatePersistedSessionResult = true),
            localSnapshotIdentity = identity,
            snapshotStore = snapshots,
            syncScheduler = scheduler,
        )
        advanceUntilIdle()
        val accountId = requireNotNull(identity.currentAccountId())
        assertEquals(1, scheduler.scheduleCalls)

        viewModel.clearSession()
        advanceUntilIdle()

        assertNull(identity.currentAccountId())
        assertEquals(listOf(accountId), snapshots.deletedAccounts)
        assertEquals(1, scheduler.cancelCalls)
    }

    @Test fun logoutRedrawsBothWidgetsWithoutOldDataBeforeDeletingSnapshots() = runTest {
        val fixture = WidgetFixture()
        fixture.seed()
        fixture.updater.update()
        assertEquals("Stary przedmiot", fixture.timetableViews.last().lessons.single().subject)
        assertEquals("Stary opis", fixture.taskViews.last().items.single().description)
        val viewModel = widgetViewModel(fixture)
        advanceUntilIdle()
        fixture.snapshots.onDelete = {
            assertNull(fixture.identity.currentAccountId())
            fixture.assertSignedOut()
            assertTrue(fixture.snapshots.payloads.isNotEmpty())
        }
        viewModel.clearSession()
        advanceUntilIdle()
        fixture.assertSignedOut()
        assertTrue(fixture.snapshots.payloads.isEmpty())
    }

    @Test fun roomDeletionFailureStillClearsBothWidgetViewsAndDoesNotReadOldRows() = runTest {
        val fixture = WidgetFixture()
        fixture.seed()
        fixture.updater.update()
        val viewModel = widgetViewModel(fixture)
        advanceUntilIdle()
        val readsBeforeLogout = fixture.snapshots.reads
        fixture.snapshots.failDelete = true
        viewModel.clearSession()
        advanceUntilIdle()
        fixture.assertSignedOut()
        assertTrue(fixture.snapshots.payloads.isNotEmpty())
        assertEquals(readsBeforeLogout, fixture.snapshots.reads)
        assertFalse(viewModel.uiState.value.isSessionValid)
    }

    @Test fun processRestartAfterLogoutCannotRestoreOrphanedSnapshots() = runTest {
        val fixture = WidgetFixture()
        fixture.seed()
        val viewModel = widgetViewModel(fixture)
        advanceUntilIdle()
        fixture.snapshots.failDelete = true
        viewModel.clearSession()
        advanceUntilIdle()
        val restoredIdentity = LocalSnapshotIdentity(fixture.identityStore)
        assertNull(restoredIdentity.currentAccountId())
        val plan = TodayTimetableContentReader(LocalTimetableReader(fixture.snapshots, restoredIdentity, Json, fixture.clock))
            .read(fixture.clock.instant(), fixture.clock.zone)
        val tasks = UpcomingTasksContentReader(LocalTasksReader(fixture.snapshots, restoredIdentity, Json, fixture.clock))
            .read(fixture.clock.instant(), fixture.clock.zone)
        assertTrue(plan.isLoggedOut)
        assertTrue(tasks.isLoggedOut)
        assertTrue(plan.lessons.isEmpty())
        assertTrue(tasks.items.isEmpty())
        assertNull(plan.lastCompleteLabel)
        assertNull(tasks.lastCompleteLabel)
    }

    @Test fun loggingInAgainCannotDisplayPreviousAccountsRowsEvenIfDeletionFailed() = runTest {
        val fixture = WidgetFixture()
        fixture.seed()
        val oldId = fixture.identity.currentAccountId()
        val viewModel = widgetViewModel(fixture)
        advanceUntilIdle()
        fixture.snapshots.failDelete = true
        viewModel.clearSession()
        advanceUntilIdle()
        viewModel.onLoginChanged("new-user")
        viewModel.onPasswordChanged("new-password")
        viewModel.login()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isSessionValid)
        assertNotEquals(oldId, fixture.identity.currentAccountId())
        assertTrue(fixture.timetableViews.last().lessons.isEmpty())
        assertTrue(fixture.taskViews.last().items.isEmpty())
        assertFalse(fixture.timetableViews.last().isLoggedOut)
        assertFalse(fixture.taskViews.last().isLoggedOut)
        fixture.identity.selectStudent(1)
        fixture.updater.update()
        assertTrue(fixture.timetableViews.last().lessons.isEmpty())
        assertTrue(fixture.taskViews.last().items.isEmpty())
        fixture.seed("Nowy przedmiot", "Nowy opis")
        fixture.updater.update()
        assertEquals("Nowy przedmiot", fixture.timetableViews.last().lessons.single().subject)
        assertEquals("Nowy opis", fixture.taskViews.last().items.single().description)
        assertTrue(fixture.snapshots.payloads.keys.any { it.startsWith(oldId!!.value) })
    }

    @Test fun widgetRefreshFailureDoesNotStopLogoutAndIdentityInvalidation() = runTest {
        val fixture = WidgetFixture()
        fixture.seed()
        val session = FakeSessionManager(validatePersistedSessionResult = true)
        val viewModel = createViewModel(sessionManager = session, localSnapshotIdentity = fixture.identity,
            snapshotStore = fixture.snapshots, widgetUpdater = WidgetUpdater { error("Launcher error") })
        advanceUntilIdle()
        viewModel.clearSession()
        advanceUntilIdle()
        assertNull(fixture.identity.currentAccountId())
        assertTrue(session.clearSessionCalled)
        assertFalse(viewModel.uiState.value.isSessionValid)
    }

    @Test fun logoutWhileSnapshotReadIsInFlightReturnsSignedOutRatherThanOldData() = runTest {
        val fixture = WidgetFixture()
        fixture.seed()
        fixture.snapshots.onRead = { fixture.identity.clearAccount() }
        val plan = fixture.plan.read(fixture.clock.instant(), fixture.clock.zone)
        assertTrue(plan.isLoggedOut)
        assertTrue(plan.lessons.isEmpty())
        assertNull(plan.lastCompleteLabel)
        val tasks = fixture.tasks.read(fixture.clock.instant(), fixture.clock.zone)
        assertTrue(tasks.isLoggedOut)
        assertTrue(tasks.items.isEmpty())
    }

    private fun widgetViewModel(fixture: WidgetFixture) = createViewModel(
        sessionManager = FakeSessionManager(validatePersistedSessionResult = true, isSessionValidResult = true),
        localSnapshotIdentity = fixture.identity, snapshotStore = fixture.snapshots, widgetUpdater = fixture.updater,
    )

    private class WidgetFixture {
        val date = LocalDate.of(2026, 10, 12)
        val clock = Clock.fixed(date.atTime(12, 0).atZone(ZoneId.of("Europe/Warsaw")).toInstant(), ZoneId.of("Europe/Warsaw"))
        val identityStore = FakeLocalIdentityStore()
        val identity = LocalSnapshotIdentity(identityStore).apply { ensureAccountId(); selectStudent(1) }
        val snapshots = FakeSnapshotStore()
        val plan = TodayTimetableContentReader(LocalTimetableReader(snapshots, identity, Json, clock))
        val tasks = UpcomingTasksContentReader(LocalTasksReader(snapshots, identity, Json, clock))
        val timetableViews = mutableListOf<TodayTimetablePresentation>()
        val taskViews = mutableListOf<UpcomingTasksPresentation>()
        val updater = LocalWidgetsUpdater(
            TimetableWidgetUpdater { timetableViews += plan.read(clock.instant(), clock.zone) },
            TasksWidgetUpdater { taskViews += tasks.read(clock.instant(), clock.zone) },
        )

        suspend fun seed(subject: String = "Stary przedmiot", description: String = "Stary opis") {
            val manager = SnapshotSyncManager(snapshots, Json, identity, clock)
            val week = taskSnapshotWeeks(date).first()
            manager.sync(SnapshotScope(identity.currentAccountId()!!, identity.selectedStudentScopeId(),
                SnapshotArea.TIMETABLE, week.scopeRange), SyncCompleteness.Complete,
                CurrentSnapshotData.Timetable(listOf(TimetableEntry(date, null, LocalTime.of(9, 0),
                    LocalTime.of(9, 45), subject, null, room = "215", changes = emptyList()))))
            for (taskWeek in taskSnapshotWeeks(date)) {
                val items = if (date in taskWeek.from..taskWeek.to) listOf(SchoolTask(SchoolTaskType.Homework,
                    4, date, subject, false, 7, SchoolTaskDetails(description, null, null))) else emptyList()
                manager.sync(SnapshotScope(identity.currentAccountId()!!, identity.selectedStudentScopeId(),
                    SnapshotArea.TASKS, taskWeek.scopeRange), SyncCompleteness.Complete, CurrentSnapshotData.Tasks(items, true))
            }
        }

        fun assertSignedOut() {
            val plan = timetableViews.last()
            val tasks = taskViews.last()
            assertEquals("Zaloguj się, aby zobaczyć plan lekcji", plan.message)
            assertEquals("Zaloguj się, aby zobaczyć sprawdziany i zadania", tasks.message)
            assertTrue(plan.isLoggedOut)
            assertTrue(tasks.isLoggedOut)
            assertTrue(plan.lessons.isEmpty())
            assertTrue(tasks.items.isEmpty())
            assertNull(plan.lastCompleteLabel)
            assertNull(tasks.lastCompleteLabel)
        }
    }

    @Test
    fun repeatedSchedulingUsesUniqueKeepPolicy() {
        assertEquals("snapshot_sync", PeriodicSyncScheduler.WORK_NAME)
        assertEquals(ExistingPeriodicWorkPolicy.KEEP, PeriodicSyncScheduler.POLICY)
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

    @Test fun expiredRestoreAndMissingLoginFormPreserveRememberedCredentialsAndSnapshotAccount() = runTest {
        val stored = StoredCredentials("fixture-user", "fixture-password")
        val credentials = FakeCredentialStore(stored)
        val settings = FakeAutoLoginSettings(enabled = true, provider = AuthenticationProvider.PROJECT_UONET)
        val session = FakeSessionManager(loginResult = Result.failure(AuthenticationException.LoginFormNotFound()))
        val identity = LocalSnapshotIdentity(FakeLocalIdentityStore())
        val account = identity.ensureAccountId()
        val snapshots = FakeSnapshotStore()
        val vm = createViewModel(sessionManager = session, credentialStore = credentials,
            autoLoginSettings = settings, localSnapshotIdentity = identity, snapshotStore = snapshots,
            autoDetectLogin = AutoDetectLogin(
                authenticateEduVulcan = { _, _ -> error("No provider autodetection or fallback") },
                loginProjectUonet = session::login,
            ))
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isSessionValid)
        assertEquals(1, session.loginCalls)
        assertTrue(session.clearSessionCalled)
        assertFalse(credentials.clearCalled)
        assertEquals(stored, credentials.load())
        assertTrue(settings.enabled)
        assertEquals(AuthenticationProvider.PROJECT_UONET, settings.provider)
        assertEquals(account, identity.currentAccountId())
        assertTrue(snapshots.deletedAccounts.isEmpty())
    }

    @Test fun failedRestoreWithoutAutoLoginDoesNotPerformLogoutOrRemoveCredentials() = runTest {
        val session = FakeSessionManager(sessionChecks = mutableListOf(SessionCheck.INVALID_RESPONSE))
        val credentials = FakeCredentialStore(StoredCredentials("fixture-user", "fixture-password"))
        val identity = LocalSnapshotIdentity(FakeLocalIdentityStore())
        val account = identity.ensureAccountId()
        val vm = createViewModel(sessionManager = session, credentialStore = credentials,
            autoLoginSettings = FakeAutoLoginSettings(enabled = false), localSnapshotIdentity = identity)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isSessionValid)
        assertEquals(0, session.loginCalls)
        assertFalse(session.clearSessionCalled)
        assertFalse(credentials.clearCalled)
        assertTrue(credentials.hasCredentials())
        assertEquals(account, identity.currentAccountId())
    }

    @Test fun failedVerificationAfterAuthenticatedAutoLoginClearsCookiesButNotCredentialsOrSnapshotAccount() = runTest {
        val session = FakeSessionManager(loginResult = Result.success(Unit), isSessionValidResult = false)
        val credentials = FakeCredentialStore(StoredCredentials("fixture-user", "fixture-password"))
        val identity = LocalSnapshotIdentity(FakeLocalIdentityStore())
        val account = identity.ensureAccountId()
        val vm = createViewModel(sessionManager = session, credentialStore = credentials,
            autoLoginSettings = FakeAutoLoginSettings(enabled = true), localSnapshotIdentity = identity,
            autoDetectLogin = AutoDetectLogin(
                authenticateEduVulcan = { _, _ -> error("No autodetection") },
                loginProjectUonet = session::login,
            ))
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isSessionValid)
        assertTrue(session.clearSessionCalled)
        assertFalse(credentials.clearCalled)
        assertTrue(credentials.hasCredentials())
        assertEquals(account, identity.currentAccountId())
    }

    @Test fun verificationErrorsPreserveActiveSessionAndCredentialsWithoutAutoLoginForBothProviders() = runTest {
        for (provider in AuthenticationProvider.entries) {
            for (error in listOf(SessionCheck.TRANSPORT_ERROR, SessionCheck.INVALID_RESPONSE, SessionCheck.SERVER_ERROR)) {
                val session = FakeSessionManager(sessionChecks = mutableListOf(error))
                val current = sessionWithCookies().apply { restore(provider) }
                val generation = current.generation()
                val credentials = FakeCredentialStore(StoredCredentials("fixture", "fixture"))
                val settings = FakeAutoLoginSettings(enabled = true, provider = provider)
                val identity = LocalSnapshotIdentity(FakeLocalIdentityStore())
                val account = identity.ensureAccountId()
                val vm = createViewModel(sessionManager = session, currentSession = current,
                    credentialStore = credentials, autoLoginSettings = settings, localSnapshotIdentity = identity,
                    autoDetectLogin = AutoDetectLogin(
                        authenticateEduVulcan = { _, _ -> error("Must not authenticate") },
                        loginProjectUonet = { _, _ -> error("Must not authenticate") },
                    ))
                advanceUntilIdle()
                assertEquals(error, vm.uiState.value.sessionVerificationError)
                assertEquals(LoginScreenMode.SessionVerificationError, vm.uiState.value.screenMode)
                assertFalse(vm.uiState.value.isCheckingSession)
                assertFalse(session.clearSessionCalled)
                assertEquals(provider, current.provider)
                assertEquals(generation, current.generation())
                assertEquals(0, credentials.loadCalls)
                assertFalse(credentials.clearCalled)
                assertTrue(credentials.hasCredentials())
                assertTrue(settings.enabled)
                assertEquals(account, identity.currentAccountId())
            }
        }
    }

    @Test fun retryVerifiesExistingSessionAndReturnsHomeWithoutLoggingInOrDuplicateRequests() = runTest {
        for (error in listOf(SessionCheck.TRANSPORT_ERROR, SessionCheck.INVALID_RESPONSE)) {
            val session = FakeSessionManager(sessionChecks = mutableListOf(error, SessionCheck.VALID))
            val current = sessionWithCookies()
            val credentials = FakeCredentialStore(StoredCredentials("fixture", "fixture"))
            val vm = createViewModel(sessionManager = session, currentSession = current,
                credentialStore = credentials, autoLoginSettings = FakeAutoLoginSettings(enabled = true))
            advanceUntilIdle()
            val generation = current.generation()
            vm.retrySessionVerification()
            vm.retrySessionVerification()
            assertEquals(LoginScreenMode.CheckingSession, vm.uiState.value.screenMode)
            advanceUntilIdle()
            assertEquals(2, session.checkCalls)
            assertEquals(0, session.loginCalls)
            assertFalse(session.clearSessionCalled)
            assertEquals(generation, current.generation())
            assertNull(vm.uiState.value.sessionVerificationError)
            assertTrue(vm.uiState.value.isSessionValid)
            assertEquals(LoginScreenMode.Home, vm.uiState.value.screenMode)
            assertFalse(credentials.clearCalled)
        }
    }

    @Test fun repeatedVerificationErrorRemainsRetryableInsteadOfShowingLogin() = runTest {
        val session = FakeSessionManager(sessionChecks = mutableListOf(
            SessionCheck.TRANSPORT_ERROR, SessionCheck.INVALID_RESPONSE))
        val vm = createViewModel(sessionManager = session)
        advanceUntilIdle()
        vm.retrySessionVerification()
        advanceUntilIdle()
        assertEquals(LoginScreenMode.SessionVerificationError, vm.uiState.value.screenMode)
        assertEquals(SessionCheck.INVALID_RESPONSE, vm.uiState.value.sessionVerificationError)
        assertEquals(0, session.loginCalls)
        assertFalse(session.clearSessionCalled)
    }

    @Test fun confirmedExpiredSessionStillClearsCookiesAndShowsLoginWithoutRemovingCredentials() = runTest {
        val session = FakeSessionManager(sessionChecks = mutableListOf(SessionCheck.EXPIRED))
        val current = sessionWithCookies()
        val credentials = FakeCredentialStore(StoredCredentials("fixture", "fixture"))
        val vm = createViewModel(sessionManager = session, currentSession = current,
            credentialStore = credentials, autoLoginSettings = FakeAutoLoginSettings(enabled = false))
        advanceUntilIdle()
        assertTrue(session.clearSessionCalled)
        assertNull(current.provider)
        assertEquals(LoginScreenMode.Login, vm.uiState.value.screenMode)
        assertNull(vm.uiState.value.sessionVerificationError)
        assertFalse(credentials.clearCalled)
        assertTrue(credentials.hasCredentials())
    }

    @Test fun transientVerificationAfterAutoLoginRetainsRenewedSessionAndRetryDoesNotLoginAgain() = runTest {
        val session = FakeSessionManager(sessionChecks = mutableListOf(
            SessionCheck.EXPIRED, SessionCheck.TRANSPORT_ERROR, SessionCheck.VALID))
        val current = sessionWithCookies()
        val credentials = FakeCredentialStore(StoredCredentials("fixture", "fixture"))
        val vm = createViewModel(sessionManager = session, currentSession = current,
            credentialStore = credentials, autoLoginSettings = FakeAutoLoginSettings(enabled = true))
        advanceUntilIdle()
        assertEquals(1, session.loginCalls)
        assertEquals(AuthenticationProvider.PROJECT_UONET, current.provider)
        assertEquals(SessionCheck.TRANSPORT_ERROR, vm.uiState.value.sessionVerificationError)
        val generation = current.generation()
        vm.retrySessionVerification()
        advanceUntilIdle()
        assertEquals(1, session.loginCalls)
        assertEquals(generation, current.generation())
        assertEquals(LoginScreenMode.Home, vm.uiState.value.screenMode)
        assertFalse(credentials.clearCalled)
    }

    @Test fun logoutFromVerificationErrorRetainsExplicitLogoutBehavior() = runTest {
        val session = FakeSessionManager(sessionChecks = mutableListOf(SessionCheck.TRANSPORT_ERROR))
        val identity = LocalSnapshotIdentity(FakeLocalIdentityStore()).apply { ensureAccountId() }
        val current = sessionWithCookies()
        val credentials = FakeCredentialStore(StoredCredentials("fixture", "fixture"))
        val settings = FakeAutoLoginSettings(enabled = true)
        val vm = createViewModel(sessionManager = session, currentSession = current,
            credentialStore = credentials, autoLoginSettings = settings, localSnapshotIdentity = identity)
        advanceUntilIdle()
        vm.clearSession()
        advanceUntilIdle()
        assertTrue(session.clearSessionCalled)
        assertNull(current.provider)
        assertNull(identity.currentAccountId())
        assertFalse(settings.enabled)
        assertNull(vm.uiState.value.sessionVerificationError)
        assertEquals(LoginScreenMode.Login, vm.uiState.value.screenMode)
        assertFalse(credentials.clearCalled)
    }

    @Test fun logoutDuringNonCooperativeLoginCannotReactivateSessionOrSnapshotAccount() = runTest {
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val session = FakeSessionManager(isSessionValidResult = true, onLogin = {
            started.complete(Unit)
            withContext(NonCancellable) { finish.await() }
        })
        val identity = LocalSnapshotIdentity(FakeLocalIdentityStore()).apply { ensureAccountId() }
        val current = sessionWithCookies()
        var widgetUpdates = 0
        val vm = createViewModel(sessionManager = session, currentSession = current, localSnapshotIdentity = identity,
            widgetUpdater = WidgetUpdater { widgetUpdates++ })
        advanceUntilIdle()
        vm.onLoginChanged("fixture")
        vm.onPasswordChanged("fixture")
        vm.login()
        runCurrent()
        started.await()
        vm.clearSession()
        assertNull(current.provider)
        assertNull(identity.currentAccountId())
        runCurrent()
        assertEquals(1, widgetUpdates)
        finish.complete(Unit)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isSessionValid)
        assertEquals(LoginScreenMode.Login, vm.uiState.value.screenMode)
        assertNull(current.provider)
        assertNull(identity.currentAccountId())
    }

    @Test fun firstLaunchWithoutLocalAccountNeverVerifiesEvenWithLeftoverCookiesAndCredentials() = runTest {
        val session = FakeSessionManager(sessionChecks = mutableListOf(SessionCheck.INVALID_RESPONSE))
        val identity = LocalSnapshotIdentity(FakeLocalIdentityStore())
        val current = sessionWithCookies()
        val generation = current.generation()
        val credentials = FakeCredentialStore(StoredCredentials("fixture", "fixture"))
        val settings = FakeAutoLoginSettings(enabled = true)
        val vm = createViewModel(sessionManager = session, localSnapshotIdentity = identity,
            currentSession = current, credentialStore = credentials, autoLoginSettings = settings)
        advanceUntilIdle()
        assertEquals(LoginScreenMode.Login, vm.uiState.value.screenMode)
        assertEquals(0, session.checkCalls)
        assertEquals(0, session.loginCalls)
        assertEquals(0, credentials.loadCalls)
        assertFalse(session.clearSessionCalled)
        assertNull(identity.currentAccountId())
        assertEquals(generation, current.generation())
        assertTrue(current.hasSessionCookies(AuthenticationProvider.PROJECT_UONET))
        assertTrue(settings.enabled)
    }

    @Test fun cleanInstallAndProcessRecreationShowLoginWithoutCreatingAccountOrProbingServer() = runTest {
        val identity = LocalSnapshotIdentity(FakeLocalIdentityStore())
        for (restart in 1..2) {
            val session = FakeSessionManager(sessionChecks = mutableListOf(SessionCheck.INVALID_RESPONSE))
            val current = CurrentAuthenticationSession(CookieJar.NO_COOKIES)
            val vm = createViewModel(sessionManager = session, localSnapshotIdentity = identity,
                currentSession = current, autoLoginSettings = FakeAutoLoginSettings(provider = null))
            advanceUntilIdle()
            assertEquals(LoginScreenMode.Login, vm.uiState.value.screenMode)
            assertEquals(0, session.checkCalls)
            assertEquals(0, session.loginCalls)
            assertNull(identity.currentAccountId())
            assertNull(current.provider)
            assertFalse(session.clearSessionCalled)
        }
    }

    @Test fun restoredBackupMetadataWithoutCookiesOrCredentialsCannotTriggerSessionCheck() = runTest {
        val identity = LocalSnapshotIdentity(FakeLocalIdentityStore()).apply { ensureAccountId(); selectStudent(1) }
        val account = identity.currentAccountId()
        val selected = identity.selectedStudentScopeId()
        val settings = FakeAutoLoginSettings(enabled = true, provider = AuthenticationProvider.PROJECT_UONET)
        val credentials = FakeCredentialStore()
        val session = FakeSessionManager(sessionChecks = mutableListOf(SessionCheck.INVALID_RESPONSE))
        val snapshots = FakeSnapshotStore()
        val current = CurrentAuthenticationSession(CookieJar.NO_COOKIES)
        val vm = createViewModel(sessionManager = session, localSnapshotIdentity = identity,
            currentSession = current, credentialStore = credentials, autoLoginSettings = settings, snapshotStore = snapshots)
        advanceUntilIdle()
        assertEquals(LoginScreenMode.Login, vm.uiState.value.screenMode)
        assertEquals(0, session.checkCalls)
        assertEquals(0, session.loginCalls)
        assertFalse(session.clearSessionCalled)
        assertFalse(credentials.clearCalled)
        assertEquals(account, identity.currentAccountId())
        assertEquals(selected, identity.selectedStudentScopeId())
        assertTrue(snapshots.deletedAccounts.isEmpty())
        assertTrue(settings.enabled)
        assertEquals(AuthenticationProvider.PROJECT_UONET, settings.provider)
        assertNull(current.provider)
    }

    @Test fun providerNameAloneDoesNotConstituteAuthenticationContext() = runTest {
        val session = FakeSessionManager()
        val current = CurrentAuthenticationSession(CookieJar.NO_COOKIES).apply { restore(AuthenticationProvider.PROJECT_UONET) }
        val credentials = FakeCredentialStore(StoredCredentials("fixture", "fixture"))
        val vm = createViewModel(sessionManager = session, currentSession = current, credentialStore = credentials,
            autoLoginSettings = FakeAutoLoginSettings(enabled = false))
        advanceUntilIdle()
        assertEquals(LoginScreenMode.Login, vm.uiState.value.screenMode)
        assertEquals(0, session.checkCalls)
        assertEquals(0, credentials.loadCalls)
        assertFalse(session.clearSessionCalled)
    }

    @Test fun completeSavedAutoLoginContextWithoutCookiesRenewsBeforeVerifyingForBothProviders() = runTest {
        for (provider in AuthenticationProvider.entries) {
            var logins = 0
            val session = FakeSessionManager(sessionChecks = mutableListOf(SessionCheck.VALID))
            val credentials = FakeCredentialStore(StoredCredentials("fixture", "fixture"))
            val identity = LocalSnapshotIdentity(FakeLocalIdentityStore()).apply { ensureAccountId() }
            val account = identity.currentAccountId()
            val vm = createViewModel(sessionManager = session, credentialStore = credentials,
                currentSession = CurrentAuthenticationSession(CookieJar.NO_COOKIES), localSnapshotIdentity = identity,
                autoLoginSettings = FakeAutoLoginSettings(enabled = true, provider = provider),
                autoDetectLogin = AutoDetectLogin(
                    authenticateEduVulcan = { _, _ ->
                        assertEquals(AuthenticationProvider.EDUVULCAN, provider)
                        logins++
                        EduVulcanLoginResult.Authenticated(OkHttpClient())
                    },
                    loginProjectUonet = { _, _ ->
                        assertEquals(AuthenticationProvider.PROJECT_UONET, provider)
                        logins++
                        Result.success(Unit)
                    },
                ))
            advanceUntilIdle()
            assertEquals(1, logins)
            assertEquals(1, session.checkCalls)
            assertEquals(1, credentials.loadCalls)
            assertFalse(session.clearSessionCalled)
            assertEquals(LoginScreenMode.Home, vm.uiState.value.screenMode)
            assertEquals(account, identity.currentAccountId())
        }
    }

    @Test fun openingManualLoginFromVerificationErrorsPreservesAllLocalDataAndSessionGeneration() = runTest {
        for (error in listOf(SessionCheck.TRANSPORT_ERROR, SessionCheck.SERVER_ERROR, SessionCheck.INVALID_RESPONSE)) {
            val session = FakeSessionManager(sessionChecks = mutableListOf(error))
            val current = sessionWithCookies()
            val identity = LocalSnapshotIdentity(FakeLocalIdentityStore()).apply { ensureAccountId(); selectStudent(1) }
            val account = requireNotNull(identity.currentAccountId())
            val selected = identity.selectedStudentScopeId()
            val credentials = FakeCredentialStore(StoredCredentials("fixture", "fixture"))
            val settings = FakeAutoLoginSettings(enabled = true)
            val snapshots = FakeSnapshotStore().apply {
                payloads["${account.value}:fixture"] = "snapshot"
                pendingEvents[account] = listOf("pending")
            }
            val vm = createViewModel(sessionManager = session, currentSession = current, localSnapshotIdentity = identity,
                credentialStore = credentials, autoLoginSettings = settings, snapshotStore = snapshots)
            advanceUntilIdle()
            val generation = current.generation()
            assertEquals(LoginScreenMode.SessionVerificationError, vm.uiState.value.screenMode)
            vm.openManualLogin()
            advanceUntilIdle()
            assertEquals(LoginScreenMode.Login, vm.uiState.value.screenMode)
            assertNull(vm.uiState.value.sessionVerificationError)
            assertEquals(1, session.checkCalls)
            assertEquals(0, session.loginCalls)
            assertFalse(session.clearSessionCalled)
            assertEquals(generation, current.generation())
            assertTrue(current.hasSessionCookies(AuthenticationProvider.PROJECT_UONET))
            assertEquals(account, identity.currentAccountId())
            assertEquals(selected, identity.selectedStudentScopeId())
            assertEquals(mapOf("${account.value}:fixture" to "snapshot"), snapshots.payloads)
            assertEquals(listOf("pending"), snapshots.pendingEvents[account])
            assertTrue(snapshots.deletedAccounts.isEmpty())
            assertFalse(credentials.clearCalled)
            assertEquals(StoredCredentials("fixture", "fixture"), credentials.load())
            assertTrue(settings.enabled)
            assertEquals(AuthenticationProvider.PROJECT_UONET, settings.provider)
        }
    }

    @Test fun manualLoginCanCompleteAfterInvalidContextResponse() = runTest {
        val session = FakeSessionManager(sessionChecks = mutableListOf(SessionCheck.INVALID_RESPONSE), isSessionValidResult = true)
        val vm = createViewModel(sessionManager = session)
        advanceUntilIdle()
        assertEquals(LoginScreenMode.SessionVerificationError, vm.uiState.value.screenMode)
        vm.openManualLogin()
        vm.onLoginChanged("fixture")
        vm.onPasswordChanged("fixture")
        vm.login()
        advanceUntilIdle()
        assertEquals(1, session.loginCalls)
        assertEquals(LoginScreenMode.Home, vm.uiState.value.screenMode)
        assertNull(vm.uiState.value.sessionVerificationError)
    }

    // Existing restore tests model a previously authenticated installation.
    private fun sessionWithCookies(): CurrentAuthenticationSession = CurrentAuthenticationSession(object : CookieJar {
        private val cookies = listOf("projekt-uczen.edu.lublin.eu", "uczen.edu.lublin.eu").map { host ->
            Cookie.Builder().name("fixture-session").value("fixture").hostOnlyDomain(host).build()
        }
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) = Unit
        override fun loadForRequest(url: HttpUrl) = cookies.filter { it.matches(url) }
    })

    private fun createViewModel(
        sessionManager: FakeSessionManager = FakeSessionManager(),
        credentialStore: FakeCredentialStore = FakeCredentialStore(),
        autoLoginSettings: FakeAutoLoginSettings = FakeAutoLoginSettings(),
        studentRepository: StudentRepository = FakeStudentRepository(),
        autoDetectLogin: AutoDetectLogin = AutoDetectLogin(
            authenticateEduVulcan = { _, _ -> EduVulcanLoginResult.InvalidCredentials },
            loginProjectUonet = sessionManager::login,
        ),
        currentSession: CurrentAuthenticationSession = sessionWithCookies(),
        messagesRepository: MessagesRepository = FakeMessagesRepository(),
        localSnapshotIdentity: LocalSnapshotIdentity = LocalSnapshotIdentity(FakeLocalIdentityStore()).apply { ensureAccountId() },
        snapshotStore: SnapshotStore = FakeSnapshotStore(),
        syncScheduler: SyncScheduler = FakeSyncScheduler(),
        widgetUpdater: WidgetUpdater = WidgetUpdater {},
    ) = LoginViewModel(
        sessionManager = sessionManager,
        autoDetectLogin = autoDetectLogin,
        currentSession = currentSession,
        messagesRepository = messagesRepository,
        credentialStore = credentialStore,
        autoLoginSettings = autoLoginSettings,
        studentRepository = studentRepository,
        activeStudentManager = ActiveStudentManager(),
        localSnapshotIdentity = localSnapshotIdentity,
        snapshotStore = snapshotStore,
        syncScheduler = syncScheduler,
        widgetUpdater = widgetUpdater,
    )

    private class FakeSyncScheduler : SyncScheduler {
        var scheduleCalls = 0
        var cancelCalls = 0
        override fun schedule() { scheduleCalls++ }
        override fun cancel() { cancelCalls++ }
    }

    private class FakeLocalIdentityStore : LocalIdentityStore {
        private val values = mutableMapOf<String, String>()
        override fun get(key: String): String? = values[key]
        override fun put(key: String, value: String) { values[key] = value }
        override fun clear() { values.clear() }
    }

    private class FakeSnapshotStore : SnapshotStore {
        val deletedAccounts = mutableListOf<LocalAccountId>()
        val payloads = mutableMapOf<String, String>()
        val pendingEvents = mutableMapOf<LocalAccountId, List<String>>()
        var failDelete = false
        var reads = 0
        var onDelete: (() -> Unit)? = null
        var onRead: (() -> Unit)? = null
        override suspend fun read(scope: SnapshotScope): String? {
            reads++
            onRead?.also { onRead = null }?.invoke()
            return payloads[scope.storageKey]
        }
        override suspend fun update(scope: SnapshotScope, transform: (String?) -> SnapshotWrite): List<ChangeEvent> {
            val write = transform(payloads[scope.storageKey])
            write.payload?.let { payloads[scope.storageKey] = it }
            return write.events
        }
        override suspend fun deleteAccount(accountId: LocalAccountId) {
            deletedAccounts += accountId
            onDelete?.invoke()
            if (failDelete) error("Delete failed")
            payloads.keys.removeAll { it.startsWith(accountId.value) }
            pendingEvents.remove(accountId)
        }
    }

    @Test fun rejectedManualProjectCandidateDoesNotReplaceSessionOrStoredCredentials() = runTest {
        val current = sessionWithCookies()
        val credentials = FakeCredentialStore(StoredCredentials("previous", "previous"))
        val session = FakeSessionManager(
            validatePersistedSessionResult = true,
            isSessionValidResult = false,
            onLogin = { current.prepareProjectLoginCandidate() },
        )
        val vm = createViewModel(sessionManager = session, currentSession = current, credentialStore = credentials)
        advanceUntilIdle()
        assertEquals(AuthenticationProvider.PROJECT_UONET, current.provider)
        vm.onLoginChanged("new")
        vm.onPasswordChanged("new")
        vm.onRememberCredentialsChanged(true)
        vm.login()
        advanceUntilIdle()
        assertEquals(AuthenticationProvider.PROJECT_UONET, current.provider)
        assertFalse(vm.uiState.value.isSessionValid)
        assertFalse(credentials.clearCalled)
        assertNull(credentials.savedCredentials)
        assertEquals(StoredCredentials("previous", "previous"), credentials.load())
        assertFalse(session.clearSessionCalled)
    }

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
        private val sessionChecks: MutableList<SessionCheck> = mutableListOf(),
        private val onLogin: suspend () -> Unit = {},
    ) : SessionManager {
        var loginCalls = 0
        var clearSessionCalled = false
        var checkCalls = 0

        override suspend fun login(
            username: String,
            password: String,
        ): Result<Unit> {
            loginCalls += 1
            onLogin()
            return loginResult
        }

        override suspend fun isSessionValid(): Boolean = isSessionValidResult

        override suspend fun checkSession(): SessionCheck {
            checkCalls++
            if (sessionChecks.isNotEmpty()) return sessionChecks.removeAt(0)
            val valid = if (checkCalls == 1) validatePersistedSessionResult else isSessionValidResult
            return if (valid) SessionCheck.VALID else SessionCheck.EXPIRED
        }

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
