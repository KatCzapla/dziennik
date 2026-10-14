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
import pl.usagi.edziennik.network.session.SessionCheck
import pl.usagi.edziennik.network.credentials.CredentialStore
import pl.usagi.edziennik.persistence.changes.LocalSnapshotIdentity
import pl.usagi.edziennik.persistence.changes.syncFailureCause
import pl.usagi.edziennik.repository.StudentRepository
import javax.inject.Inject

fun interface SyncSession {
    suspend fun prepare(): Boolean
    suspend fun prepareResult(): SessionPreparation =
        if (prepare()) SessionPreparation.READY else SessionPreparation.NO_ACCOUNT
    fun isCurrent(): Boolean = true
}

enum class SessionPreparation(val retryable: Boolean = false) {
    READY, NO_ACCOUNT, ACCOUNT_CHANGED, AUTO_LOGIN_DISABLED, MISSING_PROVIDER, MISSING_CREDENTIALS,
    LOGIN_REJECTED, LOGIN_TECHNICAL_ERROR, RENEWED_SESSION_EXPIRED, SESSION_INVALID_RESPONSE,
    SESSION_TRANSPORT_ERROR(true), SESSION_SERVER_ERROR(true), LOGIN_TRANSIENT_ERROR(true),
    PREPARATION_TRANSIENT_ERROR(true), PREPARATION_ERROR,
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

    override suspend fun prepare(): Boolean = prepareResult() == SessionPreparation.READY

    override suspend fun prepareResult(): SessionPreparation {
        preparedAccountId = null
        val accountId = identity.currentAccountId() ?: return SessionPreparation.NO_ACCOUNT
        try {
            var savedProvider: AuthenticationProvider? = null
            if (currentSession.provider == null) {
                savedProvider = settings.getAuthenticationProvider()
                if (identity.currentAccountId() != accountId) return SessionPreparation.ACCOUNT_CHANGED
                currentSession.restore(savedProvider ?: AuthenticationProvider.PROJECT_UONET)
            }
            val checked = sessionManager.checkSession()
            if (checked != SessionCheck.VALID && checked != SessionCheck.EXPIRED) return checked.preparationFailure()
            if (checked == SessionCheck.EXPIRED) {
                if (!settings.isAutoLoginEnabled()) return SessionPreparation.AUTO_LOGIN_DISABLED
                val provider = savedProvider ?: settings.getAuthenticationProvider() ?: return SessionPreparation.MISSING_PROVIDER
                val storedCredentials = credentials.load() ?: return SessionPreparation.MISSING_CREDENTIALS
                if (identity.currentAccountId() != accountId) return SessionPreparation.ACCOUNT_CHANGED
                sessionManager.clearSession()
                if (identity.currentAccountId() != accountId) return SessionPreparation.ACCOUNT_CHANGED
                val renewed = login.authenticateWithProvider(
                    provider,
                    storedCredentials.username,
                    storedCredentials.password,
                )
                if (identity.currentAccountId() != accountId) return SessionPreparation.ACCOUNT_CHANGED
                when (renewed) {
                    AutoDetectLoginResult.InvalidCredentials -> {
                        settings.setAutoLoginEnabled(false)
                        return SessionPreparation.LOGIN_REJECTED
                    }
                    AutoDetectLoginResult.TransientError -> return SessionPreparation.LOGIN_TRANSIENT_ERROR
                    AutoDetectLoginResult.TechnicalError -> return SessionPreparation.LOGIN_TECHNICAL_ERROR
                    is AutoDetectLoginResult.Success -> if (renewed.provider != provider) return SessionPreparation.LOGIN_TECHNICAL_ERROR
                }
                currentSession.activate(renewed)
                val verified = sessionManager.checkSession()
                if (verified != SessionCheck.VALID) {
                    if (verified == SessionCheck.EXPIRED) sessionManager.clearSession()
                    return verified.preparationFailure()
                }
            }
            if (activeStudentManager.activeStudent.value == null) {
                val available = students.getStudents()
                if (identity.currentAccountId() != accountId) return SessionPreparation.ACCOUNT_CHANGED
                val selected = identity.selectedStudentScopeId()
                activeStudentManager.initialize(available)
                if (selected != null) {
                    available.firstOrNull { identity.studentScopeId(it.diaryId) == selected }
                        ?.let(activeStudentManager::setActiveStudent)
                }
            }
            preparedAccountId = accountId
            return if (isCurrent()) SessionPreparation.READY else SessionPreparation.ACCOUNT_CHANGED
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            return if (failure.syncFailureCause().retryable) SessionPreparation.PREPARATION_TRANSIENT_ERROR else SessionPreparation.PREPARATION_ERROR
        }
    }

    override fun isCurrent(): Boolean =
        preparedAccountId != null && preparedAccountId == identity.currentAccountId()
}

private fun SessionCheck.preparationFailure(): SessionPreparation = when (this) {
    SessionCheck.TRANSPORT_ERROR -> SessionPreparation.SESSION_TRANSPORT_ERROR
    SessionCheck.SERVER_ERROR -> SessionPreparation.SESSION_SERVER_ERROR
    SessionCheck.EXPIRED -> SessionPreparation.RENEWED_SESSION_EXPIRED
    else -> SessionPreparation.SESSION_INVALID_RESPONSE
}
