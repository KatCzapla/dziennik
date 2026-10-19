package pl.usagi.edziennik.background

import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import okhttp3.CookieJar
import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.usagi.edziennik.domain.ActiveStudentManager
import pl.usagi.edziennik.domain.Student
import pl.usagi.edziennik.network.credentials.CredentialStore
import pl.usagi.edziennik.network.credentials.StoredCredentials
import pl.usagi.edziennik.network.eduvulcan.EduVulcanLoginResult
import pl.usagi.edziennik.network.session.AuthenticationProvider
import pl.usagi.edziennik.network.session.AutoDetectLogin
import pl.usagi.edziennik.network.session.AutoLoginSettings
import pl.usagi.edziennik.network.session.CurrentAuthenticationSession
import pl.usagi.edziennik.network.session.SessionManager
import pl.usagi.edziennik.network.session.SessionCheck
import pl.usagi.edziennik.network.session.AuthenticationException
import kotlinx.coroutines.CancellationException
import pl.usagi.edziennik.persistence.changes.LocalIdentityStore
import pl.usagi.edziennik.persistence.changes.LocalSnapshotIdentity
import pl.usagi.edziennik.persistence.changes.OneShotSyncResult
import pl.usagi.edziennik.persistence.changes.SnapshotSynchronizer
import pl.usagi.edziennik.repository.StudentRepository
import pl.usagi.edziennik.domain.changes.ChangeEvent
import pl.usagi.edziennik.network.NetworkModule
import android.app.Application
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class BackgroundSessionPreparerTest {
    @Test fun validSessionDoesNotLogInAgain() = runTest {
        val fixture = Fixture(initiallyValid = true)

        fixture.runSync()

        assertEquals(1, fixture.sync.calls)
        assertEquals(0, fixture.projectLoginCalls + fixture.eduLoginCalls)
        assertEquals(0, fixture.credentialLoads)
        assertEquals(0, fixture.sessionClears)
    }

    @Test fun expiredProjectSessionRenewsAndSyncsExactlyOnce() = runTest {
        val fixture = Fixture(provider = AuthenticationProvider.PROJECT_UONET)

        fixture.runSync()

        assertEquals(1, fixture.projectLoginCalls)
        assertEquals(0, fixture.eduLoginCalls)
        assertEquals(AuthenticationProvider.PROJECT_UONET, fixture.currentSession.provider)
        assertEquals(1, fixture.sync.calls)
        assertTrue(fixture.activeStudentManager.activeStudent.value != null)
    }

    @Test fun expiredEduVulcanSessionUsesOnlySavedProvider() = runTest {
        val fixture = Fixture(provider = AuthenticationProvider.EDUVULCAN)

        fixture.runSync()

        assertEquals(1, fixture.eduLoginCalls)
        assertEquals(0, fixture.projectLoginCalls)
        assertEquals(AuthenticationProvider.EDUVULCAN, fixture.currentSession.provider)
        assertEquals(1, fixture.sync.calls)
    }

    @Test fun disabledAutoLoginDoesNotRenewOrSync() = runTest {
        val fixture = Fixture(autoLoginEnabled = false)

        fixture.runSync()

        assertEquals(0, fixture.credentialLoads)
        assertEquals(0, fixture.projectLoginCalls + fixture.eduLoginCalls)
        assertEquals(0, fixture.sync.calls)
    }

    @Test fun missingCredentialsDoesNotRenewOrSync() = runTest {
        val fixture = Fixture(storedCredentials = null)

        fixture.runSync()

        assertEquals(1, fixture.credentialLoads)
        assertEquals(0, fixture.projectLoginCalls + fixture.eduLoginCalls)
        assertEquals(0, fixture.sync.calls)
    }

    @Test fun missingSavedProviderDoesNotGuessOrSync() = runTest {
        val fixture = Fixture(provider = null)

        fixture.runSync()

        assertEquals(0, fixture.credentialLoads)
        assertEquals(0, fixture.projectLoginCalls + fixture.eduLoginCalls)
        assertEquals(0, fixture.sync.calls)
    }

    @Test fun failedProjectLoginDoesNotFallbackOrTouchBaseline() = runTest {
        val fixture = Fixture(loginSucceeds = false)
        val accountId = fixture.identity.currentAccountId()
        val baseline = fixture.sync.baseline

        fixture.runSync()

        assertEquals(1, fixture.projectLoginCalls)
        assertEquals(0, fixture.eduLoginCalls)
        assertEquals(0, fixture.sync.calls)
        assertEquals(baseline, fixture.sync.baseline)
        assertEquals(accountId, fixture.identity.currentAccountId())
        assertFalse(fixture.preparer.isCurrent())
    }

    @Test fun failedEduVulcanLoginDoesNotFallback() = runTest {
        val fixture = Fixture(provider = AuthenticationProvider.EDUVULCAN, loginSucceeds = false)

        fixture.runSync()

        assertEquals(1, fixture.eduLoginCalls)
        assertEquals(0, fixture.projectLoginCalls)
        assertEquals(0, fixture.sync.calls)
    }

    @Test fun renewalMustPassSessionValidationBeforeSync() = runTest {
        val fixture = Fixture(sessionValidAfterLogin = false)

        fixture.runSync()

        assertEquals(1, fixture.projectLoginCalls)
        assertEquals(0, fixture.sync.calls)
        assertFalse(fixture.preparer.isCurrent())
    }

    @Test fun transportFailureDuringVerificationDoesNotClearCookiesOrLogIn() = runTest {
        val fixture = Fixture(checkResults = mutableListOf(SessionCheck.TRANSPORT_ERROR))
        val result = fixture.runSync()
        assertTrue(result.retryable)
        assertEquals("SESSION_TRANSPORT_ERROR", result.reason)
        assertEquals(0, fixture.sessionClears)
        assertEquals(0, fixture.projectLoginCalls + fixture.eduLoginCalls)
        assertEquals(0, fixture.sync.calls)
    }

    @Test fun serverFailureDuringVerificationDoesNotTriggerAutoLogin() = runTest {
        val fixture = Fixture(checkResults = mutableListOf(SessionCheck.SERVER_ERROR))
        assertEquals("SESSION_SERVER_ERROR", fixture.runSync().reason)
        assertEquals(0, fixture.sessionClears)
        assertEquals(0, fixture.projectLoginCalls + fixture.eduLoginCalls)
    }

    @Test fun transientAutoLoginFailureRetriesWithoutFallbackForBothProviders() = runTest {
        AuthenticationProvider.entries.forEach { provider ->
            val fixture = Fixture(provider = provider, transientLoginFailure = true)
            val before = fixture.sync.baseline
            val result = fixture.runSync()
            assertEquals("LOGIN_TRANSIENT_ERROR", result.reason)
            assertTrue(result.retryable)
            assertEquals(1, fixture.projectLoginCalls + fixture.eduLoginCalls)
            assertEquals(0, fixture.sync.calls)
            assertEquals(before, fixture.sync.baseline)
        }
    }

    @Test fun rejectedCredentialsDisableFurtherAutomaticLoginAttempts() = runTest {
        AuthenticationProvider.entries.forEach { provider ->
            val fixture = Fixture(provider = provider, invalidCredentials = true)
            assertEquals("LOGIN_REJECTED", fixture.runSync().reason)
            val second = fixture.runSync()
            assertEquals("AUTO_LOGIN_DISABLED", second.reason)
            assertFalse(second.retryable)
            assertEquals(1, fixture.projectLoginCalls + fixture.eduLoginCalls)
            assertEquals(0, fixture.sync.calls)
        }
    }

    @Test fun unavailableAutoLoginConditionsDoNotRetry() = runTest {
        val cases = listOf(
            Fixture(autoLoginEnabled = false) to "AUTO_LOGIN_DISABLED",
            Fixture(storedCredentials = null) to "MISSING_CREDENTIALS",
            Fixture(provider = null) to "MISSING_PROVIDER",
        )
        cases.forEach { (fixture, reason) ->
            val result = fixture.runSync()
            assertEquals(reason, result.reason)
            assertFalse(result.retryable)
            assertEquals(0, fixture.projectLoginCalls + fixture.eduLoginCalls)
        }
    }

    @Test fun expiredSessionWithSuccessfulLoginReturnsCompleteResult() = runTest {
        val fixture = Fixture()
        assertEquals(SyncRunStatus.COMPLETE, fixture.runSync().status)
        assertEquals(1, fixture.sync.calls)
    }

    @Test fun transportFailureAfterLoginRetainsRenewedSessionForRetry() = runTest {
        val fixture = Fixture(checkResults = mutableListOf(SessionCheck.EXPIRED, SessionCheck.TRANSPORT_ERROR))
        assertTrue(fixture.runSync().retryable)
        assertEquals(1, fixture.sessionClears)
        assertEquals(AuthenticationProvider.PROJECT_UONET, fixture.currentSession.provider)
        assertEquals(0, fixture.sync.calls)
    }

    @Test fun cancelledSessionVerificationIsPropagated() = runTest {
        val fixture = Fixture(checkException = CancellationException())
        var cancelled = false
        try { fixture.runSync() } catch (_: CancellationException) { cancelled = true }
        assertTrue(cancelled)
        assertEquals(0, fixture.projectLoginCalls + fixture.eduLoginCalls)
        assertEquals(0, fixture.sync.calls)
    }

    @Test fun malformedVerificationResponseDoesNotPretendSessionExpired() = runTest {
        val fixture = Fixture(checkResults = mutableListOf(SessionCheck.INVALID_RESPONSE))
        val result = fixture.runSync()
        assertEquals("SESSION_INVALID_RESPONSE", result.reason)
        assertFalse(result.retryable)
        assertEquals(0, fixture.sessionClears)
        assertEquals(0, fixture.projectLoginCalls + fixture.eduLoginCalls)
        assertEquals(0, fixture.sync.calls)
    }

    @Test fun initialInvalidResponseBlocksBothProvidersEvenWithCredentialsAndEnabledAutoLogin() = runTest {
        AuthenticationProvider.entries.forEach { provider ->
            val fixture = Fixture(provider = provider, checkResults = mutableListOf(SessionCheck.INVALID_RESPONSE))
            val baseline = fixture.sync.baseline
            val result = fixture.runSync()
            assertEquals("SESSION_INVALID_RESPONSE", result.reason)
            assertEquals(SyncRunStatus.FAILED, result.status)
            assertFalse(result.retryable)
            assertEquals(0, fixture.credentialLoads)
            assertEquals(0, fixture.sessionClears)
            assertEquals(0, fixture.eduLoginCalls + fixture.projectLoginCalls)
            assertEquals(0, fixture.sync.calls)
            assertEquals(baseline, fixture.sync.baseline)
        }
    }

    @Test fun invalidResponseAfterSuccessfulAutoLoginAlsoPreventsSyncForBothProviders() = runTest {
        AuthenticationProvider.entries.forEach { provider ->
            val fixture = Fixture(provider = provider,
                checkResults = mutableListOf(SessionCheck.EXPIRED, SessionCheck.INVALID_RESPONSE))
            val baseline = fixture.sync.baseline
            assertEquals("SESSION_INVALID_RESPONSE", fixture.runSync().reason)
            assertEquals(1, fixture.eduLoginCalls + fixture.projectLoginCalls)
            assertEquals(1, fixture.sessionClears)
            assertEquals(provider, fixture.currentSession.provider)
            assertEquals(0, fixture.sync.calls)
            assertEquals(baseline, fixture.sync.baseline)
        }
    }

    @Test fun diagnosticsDistinguishExistingAndRenewedSessionFailureWithoutSecrets() = runTest {
        val cases = listOf(
            mutableListOf(SessionCheck.INVALID_RESPONSE) to "VERIFY_EXISTING_SESSION",
            mutableListOf(SessionCheck.EXPIRED, SessionCheck.INVALID_RESPONSE) to "VERIFY_RENEWED_SESSION",
        )
        for ((checks, stage) in cases) {
            ShadowLog.clear()
            Fixture(checkResults = checks).runSync()
            val logs = ShadowLog.getLogsForTag("SnapshotSync").map { it.msg }
            assertTrue(logs.any { it.startsWith("stage=RESTORE_SESSION") })
            assertTrue(logs.contains("stage=$stage result=SESSION_INVALID_RESPONSE"))
            assertEquals(stage == "VERIFY_RENEWED_SESSION", logs.contains("stage=AUTO_LOGIN result=SUCCESS"))
            assertFalse(logs.contains("stage=FETCH_DATA result=START"))
            assertFalse(logs.joinToString().contains("saved-user"))
            assertFalse(logs.joinToString().contains("saved-password"))
            assertFalse(logs.joinToString().contains("runtime-key"))
        }
    }

    @Test fun readySessionDiagnosticsReachDataFetchingWithoutAutoLogin() = runTest {
        ShadowLog.clear()
        Fixture(initiallyValid = true).runSync()
        val logs = ShadowLog.getLogsForTag("SnapshotSync").map { it.msg }
        assertTrue(logs.contains("stage=SESSION_PREPARATION result=READY"))
        assertTrue(logs.contains("stage=FETCH_DATA result=START"))
        assertTrue(logs.contains("stage=FETCH_DATA result=FINISHED"))
        assertFalse(logs.any { it.startsWith("stage=AUTO_LOGIN") })
    }

    @Test fun backgroundRestoreAndRenewalUseCookieStateVisibleToAnAlreadyCreatedUiClient() = runTest {
        val url = "https://projekt-uczen.edu.lublin.eu/lublin/api/Context".toHttpUrl()
        val original = Cookie.Builder().name("fixture-session").value("before")
            .hostOnlyDomain(url.host).build()
        val renewed = Cookie.Builder().name("fixture-session").value("after")
            .hostOnlyDomain(url.host).build()
        for (initiallyValid in listOf(true, false)) {
            val jar = object : CookieJar {
                var stored = listOf(original)
                override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) { stored = cookies }
                override fun loadForRequest(url: HttpUrl) = stored.filter { it.matches(url) }
            }
            val fixture = Fixture(initiallyValid = initiallyValid, cookieJar = jar,
                onProjectLogin = { jar.saveFromResponse(url, listOf(renewed)) })
            val uiClient = NetworkModule.provideOkHttpClient(fixture.currentSession)
            assertEquals(listOf(original), uiClient.cookieJar.loadForRequest(url))

            assertEquals(SyncRunStatus.COMPLETE, fixture.runSync().status)

            assertTrue(uiClient.cookieJar === fixture.currentSession)
            assertEquals(AuthenticationProvider.PROJECT_UONET, fixture.currentSession.provider)
            assertEquals(listOf(if (initiallyValid) original else renewed), uiClient.cookieJar.loadForRequest(url))
            assertEquals(if (initiallyValid) 0 else 1, fixture.projectLoginCalls)
            assertEquals(1, fixture.sync.calls)
        }
    }

    @Test fun uiLoginWaitsForWorkerAutoLoginUsingTheSameSession() = runTest {
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val fixture = Fixture(onProjectLogin = { started.complete(Unit); finish.await() })
        var uiStarted = false
        val worker = launch { fixture.runSync() }
        started.await()
        val ui = launch {
            fixture.currentSession.withAuthentication {
                uiStarted = true
                fixture.currentSession.beginLogin()
                fixture.currentSession.activate(pl.usagi.edziennik.network.session.AutoDetectLoginResult.Success(
                    AuthenticationProvider.PROJECT_UONET))
            }
        }
        yield()
        assertFalse(uiStarted)
        finish.complete(Unit)
        worker.join()
        ui.join()
        assertTrue(uiStarted)
        assertEquals(1, fixture.projectLoginCalls)
        assertEquals(1, fixture.sync.calls)
        assertEquals(AuthenticationProvider.PROJECT_UONET, fixture.currentSession.provider)
    }

    private class Fixture(
        private val provider: AuthenticationProvider? = AuthenticationProvider.PROJECT_UONET,
        private val autoLoginEnabled: Boolean = true,
        storedCredentials: StoredCredentials? = StoredCredentials("saved-user", "saved-password"),
        initiallyValid: Boolean = false,
        private val loginSucceeds: Boolean = true,
        private val sessionValidAfterLogin: Boolean = true,
        private val checkResults: MutableList<SessionCheck> = mutableListOf(),
        private val checkException: Exception? = null,
        private val transientLoginFailure: Boolean = false,
        private val invalidCredentials: Boolean = false,
        cookieJar: CookieJar = CookieJar.NO_COOKIES,
        private val onProjectLogin: suspend () -> Unit = {},
    ) {
        private val identityStore = object : LocalIdentityStore {
            private val values = mutableMapOf<String, String>()
            override fun get(key: String): String? = values[key]
            override fun put(key: String, value: String) { values[key] = value }
            override fun clear() { values.clear() }
        }
        val identity = LocalSnapshotIdentity(identityStore).also { it.ensureAccountId() }
        val currentSession = CurrentAuthenticationSession(cookieJar)
        val activeStudentManager = ActiveStudentManager()
        var credentialLoads = 0
        private val credentials = object : CredentialStore {
            private var saved = storedCredentials
            override suspend fun save(username: String, password: String) { saved = StoredCredentials(username, password) }
            override suspend fun load(): StoredCredentials? { credentialLoads++; return saved }
            override suspend fun clear() { saved = null }
            override suspend fun hasCredentials(): Boolean = saved != null
        }
        private var enabled = autoLoginEnabled
        private val settings = object : AutoLoginSettings {
            override suspend fun isAutoLoginEnabled() = enabled
            override suspend fun setAutoLoginEnabled(enabled: Boolean) { this@Fixture.enabled = enabled }
            override suspend fun getAuthenticationProvider() = provider
            override suspend fun setAuthenticationProvider(provider: AuthenticationProvider?) = Unit
        }
        private var valid = initiallyValid
        var sessionClears = 0
        private val sessionManager = object : SessionManager {
            override suspend fun login(username: String, password: String): Result<Unit> =
                error("Direct login is not used by this test")
            override suspend fun isSessionValid() = valid
            override suspend fun checkSession(): SessionCheck {
                checkException?.let { throw it }
                return if (checkResults.isNotEmpty()) checkResults.removeAt(0)
                else if (valid) SessionCheck.VALID else SessionCheck.EXPIRED
            }
            override suspend fun validatePersistedSession() = valid
            override suspend fun clearSession() {
                sessionClears++
                valid = false
                currentSession.clear()
            }
        }
        var eduLoginCalls = 0
        var projectLoginCalls = 0
        private val login = AutoDetectLogin(
            authenticateEduVulcan = { _, _ ->
                eduLoginCalls++
                if (transientLoginFailure) EduVulcanLoginResult.TransientError
                else if (invalidCredentials) EduVulcanLoginResult.InvalidCredentials
                else if (loginSucceeds) {
                    valid = sessionValidAfterLogin
                    EduVulcanLoginResult.Authenticated(OkHttpClient())
                } else EduVulcanLoginResult.InvalidCredentials
            },
            loginProjectUonet = { _, _ ->
                projectLoginCalls++
                if (transientLoginFailure) Result.failure(AuthenticationException.Network(java.io.IOException()))
                else if (invalidCredentials) Result.failure(AuthenticationException.InvalidCredentials())
                else if (loginSucceeds) {
                    onProjectLogin()
                    valid = sessionValidAfterLogin
                    Result.success(Unit)
                } else Result.failure(IllegalStateException("Authentication failed"))
            },
        )
        val preparer = BackgroundSessionPreparer(
            identity,
            settings,
            credentials,
            login,
            currentSession,
            sessionManager,
            object : StudentRepository {
                override suspend fun getStudents() = listOf(Student("runtime-key", 7, "Student", "1A", "School"))
            },
            activeStudentManager,
        )
        val sync = RecordingSync()

        suspend fun runSync() = PeriodicSyncRun(preparer, sync, PendingNotificationPublisher { }).run()
    }

    private class RecordingSync : SnapshotSynchronizer {
        var calls = 0
        var baseline = "existing-baseline"
        override suspend fun syncOnce(): OneShotSyncResult {
            calls++
            baseline = "updated-baseline"
            return OneShotSyncResult(emptyList(), emptyMap())
        }
    }
}
