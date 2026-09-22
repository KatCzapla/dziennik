package pl.usagi.edziennik.network.messages

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import pl.usagi.edziennik.network.session.SessionManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessagesSessionManager @Inject constructor(
    private val sessionManager: SessionManager,
    private val messagesUrlProvider: MessagesUrlProvider,
    private val messagesSsoClient: MessagesSsoClient,
    private val messagesApiClient: MessagesApiClient,
    private val sessionStore: MessagesSessionStore,
) {
    private val mutex = Mutex()

    suspend fun ensureMessagesSession(): MessagesSessionContext = mutex.withLock {
        MessagesDiagnostics.debug("Messages session ensure started")
        if (!sessionManager.validatePersistedSession()) {
            sessionStore.clear()
            MessagesDiagnostics.warn("Messages session failed: mainSessionValid=false")
            throw MessagesSessionException.MainSessionInvalid
        }
        MessagesDiagnostics.debug("Messages session main session valid")

        sessionStore.get()?.let { existingContext ->
            MessagesDiagnostics.debug("Messages session already available: ${existingContext.appUrl.safeHostAndPath()}")
            return@withLock existingContext
        }

        bootstrapMessagesSession()
    }

    fun clear() {
        sessionStore.clear()
    }

    private suspend fun bootstrapMessagesSession(): MessagesSessionContext {
        MessagesDiagnostics.debug("Messages session resolving Messages URL")
        val appUrl = try {
            messagesUrlProvider.getMessagesAppUrl()
        } catch (exception: MessagesSessionException) {
            MessagesDiagnostics.warn(
                "Messages URL resolution failed: ${exception.toMessagesUrlResolutionFailureCategory()}",
            )
            throw exception
        }
        MessagesDiagnostics.debug("Messages session Messages URL resolved: ${appUrl.safeHostAndPath()}")
        val context = messagesSsoClient.authenticate(appUrl)
        MessagesDiagnostics.debug("Messages validating session using unread count")
        messagesApiClient.getUnreadCounts(context)
        sessionStore.save(context)
        MessagesDiagnostics.debug("Messages session bootstrap completed")
        return context
    }

    private fun MessagesSessionException.toMessagesUrlResolutionFailureCategory(): String =
        when (this) {
            is MessagesSessionException.UrlSourceRequestFailed -> "URL_SOURCE_REQUEST_FAILED"
            is MessagesSessionException.InvalidMessagesUrl -> "INVALID_URL"
            MessagesSessionException.MissingMessagesUrl -> "VALUE_MISSING"
            else -> "SOURCE_MISSING"
        }
}
