package pl.usagi.edziennik.background

import kotlinx.coroutines.CancellationException
import pl.usagi.edziennik.domain.ActiveStudentManager
import pl.usagi.edziennik.domain.changes.LocalAccountId
import pl.usagi.edziennik.network.session.AuthenticationProvider
import pl.usagi.edziennik.network.session.AutoDetectLogin
import pl.usagi.edziennik.network.session.AutoDetectLoginResult
import pl.usagi.edziennik.network.session.AutoLoginSettings
import pl.usagi.edziennik.network.session.CurrentAuthenticationSession
import pl.usagi.edziennik.network.session.SessionManager
import pl.usagi.edziennik.network.credentials.CredentialStore
import pl.usagi.edziennik.persistence.changes.LocalSnapshotIdentity
import pl.usagi.edziennik.repository.StudentRepository
import javax.inject.Inject

fun interface SyncSession {
    suspend fun prepare(): Boolean
    fun isCurrent(): Boolean = true
}

class BackgroundSessionPreparer @Inject constructor(
    private val identity: LocalSnapshotIdentity,
    private val settings: AutoLoginSettings,
    private val credentials: CredentialStore,
    private val login: AutoDetectLogin,
    private val currentSession: CurrentAuthenticationSession,
    private val sessionManager: SessionManager,
    private val students: StudentRepository,
    private val activeStudentManager: ActiveStudentManager,
) : SyncSession {
    private var preparedAccountId: LocalAccountId? = null

    override suspend fun prepare(): Boolean {
        preparedAccountId = null
        val accountId = identity.currentAccountId() ?: return false
        try {
            var savedProvider: AuthenticationProvider? = null
            if (currentSession.provider == null) {
                savedProvider = settings.getAuthenticationProvider()
                currentSession.restore(savedProvider ?: AuthenticationProvider.PROJECT_UONET)
            }
            if (!sessionManager.isSessionValid()) {
                if (!settings.isAutoLoginEnabled()) return false
                val provider = savedProvider ?: settings.getAuthenticationProvider() ?: return false
                val storedCredentials = credentials.load() ?: return false
                sessionManager.clearSession()
                val renewed = login.authenticateWithProvider(
                    provider,
                    storedCredentials.username,
                    storedCredentials.password,
                )
                if (renewed !is AutoDetectLoginResult.Success || renewed.provider != provider) return false
                currentSession.activate(renewed)
                if (!sessionManager.isSessionValid()) {
                    sessionManager.clearSession()
                    return false
                }
            }
            if (activeStudentManager.activeStudent.value == null) {
                val available = students.getStudents()
                val selected = identity.selectedStudentScopeId()
                activeStudentManager.initialize(available)
                if (selected != null) {
                    available.firstOrNull { identity.studentScopeId(it.diaryId) == selected }
                        ?.let(activeStudentManager::setActiveStudent)
                }
            }
            preparedAccountId = accountId
            return isCurrent()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return false
        }
    }

    override fun isCurrent(): Boolean =
        preparedAccountId != null && preparedAccountId == identity.currentAccountId()
}
