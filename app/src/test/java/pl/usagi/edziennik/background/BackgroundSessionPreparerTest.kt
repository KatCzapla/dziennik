package pl.usagi.edziennik.background

import kotlinx.coroutines.test.runTest
import okhttp3.CookieJar
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
import pl.usagi.edziennik.persistence.changes.LocalIdentityStore
import pl.usagi.edziennik.persistence.changes.LocalSnapshotIdentity
import pl.usagi.edziennik.persistence.changes.OneShotSyncResult
import pl.usagi.edziennik.persistence.changes.SnapshotSynchronizer
import pl.usagi.edziennik.repository.StudentRepository
import pl.usagi.edziennik.domain.changes.ChangeEvent

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

    private class Fixture(
        private val provider: AuthenticationProvider? = AuthenticationProvider.PROJECT_UONET,
        private val autoLoginEnabled: Boolean = true,
        storedCredentials: StoredCredentials? = StoredCredentials("saved-user", "saved-password"),
        initiallyValid: Boolean = false,
        private val loginSucceeds: Boolean = true,
        private val sessionValidAfterLogin: Boolean = true,
    ) {
        private val identityStore = object : LocalIdentityStore {
            private val values = mutableMapOf<String, String>()
            override fun get(key: String): String? = values[key]
            override fun put(key: String, value: String) { values[key] = value }
            override fun clear() { values.clear() }
        }
        val identity = LocalSnapshotIdentity(identityStore).also { it.ensureAccountId() }
        val currentSession = CurrentAuthenticationSession(CookieJar.NO_COOKIES)
        val activeStudentManager = ActiveStudentManager()
        var credentialLoads = 0
        private val credentials = object : CredentialStore {
            private var saved = storedCredentials
            override suspend fun save(username: String, password: String) { saved = StoredCredentials(username, password) }
            override suspend fun load(): StoredCredentials? { credentialLoads++; return saved }
            override suspend fun clear() { saved = null }
            override suspend fun hasCredentials(): Boolean = saved != null
        }
        private val settings = object : AutoLoginSettings {
            override suspend fun isAutoLoginEnabled() = autoLoginEnabled
            override suspend fun setAutoLoginEnabled(enabled: Boolean) = Unit
            override suspend fun getAuthenticationProvider() = provider
            override suspend fun setAuthenticationProvider(provider: AuthenticationProvider?) = Unit
        }
        private var valid = initiallyValid
        var sessionClears = 0
        private val sessionManager = object : SessionManager {
            override suspend fun login(username: String, password: String): Result<Unit> =
                error("Direct login is not used by this test")
            override suspend fun isSessionValid() = valid
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
                if (loginSucceeds) {
                    valid = sessionValidAfterLogin
                    EduVulcanLoginResult.Authenticated(OkHttpClient())
                } else EduVulcanLoginResult.InvalidCredentials
            },
            loginProjectUonet = { _, _ ->
                projectLoginCalls++
                if (loginSucceeds) {
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

        suspend fun runSync() {
            PeriodicSyncRun(preparer, sync, object : ChangeNotifier {
                override fun publish(events: List<ChangeEvent>) = Unit
            }).run()
        }
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
