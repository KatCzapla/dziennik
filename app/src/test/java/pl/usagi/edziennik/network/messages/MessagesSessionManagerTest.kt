package pl.usagi.edziennik.network.messages

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.usagi.edziennik.dto.MailboxDto
import pl.usagi.edziennik.dto.MessageDetailsDto
import pl.usagi.edziennik.dto.ReceivedMessageDto
import pl.usagi.edziennik.dto.UnreadMessageCountDto
import pl.usagi.edziennik.domain.MessageMailboxType
import pl.usagi.edziennik.network.session.SessionManager
import pl.usagi.edziennik.network.session.CurrentAuthenticationSession

class MessagesSessionManagerTest {
    @Test
    fun existingUsableMessagesSessionDoesNotRunSsoAgain() = runTest {
        val store = MessagesSessionStore()
        val context = messagesContext()
        store.save(context)
        val ssoClient = FakeMessagesSsoClient()
        val manager = manager(
            store = store,
            ssoClient = ssoClient,
        )

        val result = manager.ensureMessagesSession()

        assertSame(context, result)
        assertEquals(0, ssoClient.calls)
    }

    @Test
    fun missingMessagesSessionRunsSsoAndStoresBootstrapData() = runTest {
        val store = MessagesSessionStore()
        val ssoClient = FakeMessagesSsoClient()
        val manager = manager(
            store = store,
            ssoClient = ssoClient,
        )

        val context = manager.ensureMessagesSession()

        assertEquals(1, ssoClient.calls)
        assertSame(context, store.get())
        assertEquals("app-guid", context.appGuid)
        assertEquals("verification-token", context.requestVerificationToken)
    }

    @Test
    fun missingMessagesUrlGivesControlledError() {
        val manager = manager(
            urlProvider = FakeMessagesUrlProvider(url = null),
        )

        assertThrows(MessagesSessionException.MissingMessagesUrl::class.java) {
            runTest { manager.ensureMessagesSession() }
        }
    }

    @Test
    fun invalidMainSessionGivesControlledErrorAndClearsMessagesState() {
        val store = MessagesSessionStore().apply { save(messagesContext()) }
        val manager = manager(
            sessionManager = FakeSessionManager(valid = false),
            store = store,
        )

        assertThrows(MessagesSessionException.MainSessionInvalid::class.java) {
            runTest { manager.ensureMessagesSession() }
        }
        assertNull(store.get())
    }

    @Test
    fun concurrentCallsRunOnlyOneSso() = runTest {
        val ssoClient = FakeMessagesSsoClient()
        val manager = manager(ssoClient = ssoClient)

        val results = awaitAll(
            async { manager.ensureMessagesSession() },
            async { manager.ensureMessagesSession() },
        )

        assertEquals(1, ssoClient.calls)
        assertEquals(results[0], results[1])
    }

    @Test
    fun clearRemovesInMemoryBootstrapState() = runTest {
        val store = MessagesSessionStore().apply { save(messagesContext()) }
        val manager = manager(store = store)

        manager.clear()

        assertNull(store.get())
    }

    @Test
    fun unreadCountAndReceivedMessagesReuseOneMessagesSsoSession() = runTest {
        val ssoClient = FakeMessagesSsoClient()
        val apiClient = FakeMessagesApiClient()
        val manager = manager(
            ssoClient = ssoClient,
            apiClient = apiClient,
        )
        val dataSource = MessagesDataSource(
            messagesSessionManager = manager,
            messagesApiClient = apiClient,
        )

        dataSource.getUnreadCounts()
        dataSource.getReceivedMessages(lastMessageId = 0, pageSize = 50)

        assertEquals(1, ssoClient.calls)
        assertEquals(2, apiClient.unreadCalls)
        assertEquals(1, apiClient.receivedCalls)
    }

    @Test fun logoutDuringSsoCannotRestoreMessagesContext() = runTest {
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val store = MessagesSessionStore()
        val current = CurrentAuthenticationSession(CookieJar.NO_COOKIES, store::clear)
        val manager = manager(store = store, currentSession = current, ssoClient = FakeMessagesSsoClient {
            started.complete(Unit)
            withContext(NonCancellable) { finish.await() }
        })
        val sso = async { manager.ensureMessagesSession() }
        started.await()
        current.invalidateAuthentication()
        finish.complete(Unit)
        sso.join()
        assertTrue(sso.isCancelled)
        assertNull(store.get())
    }

    private fun manager(
        sessionManager: SessionManager = FakeSessionManager(),
        urlProvider: MessagesUrlProvider = FakeMessagesUrlProvider(),
        ssoClient: FakeMessagesSsoClient = FakeMessagesSsoClient(),
        apiClient: MessagesApiClient = FakeMessagesApiClient(),
        store: MessagesSessionStore = MessagesSessionStore(),
        currentSession: CurrentAuthenticationSession = CurrentAuthenticationSession(CookieJar.NO_COOKIES),
    ): MessagesSessionManager =
        MessagesSessionManager(
            sessionManager = sessionManager,
            messagesUrlProvider = urlProvider,
            messagesSsoClient = ssoClient,
            messagesApiClient = apiClient,
            sessionStore = store,
            currentSession = currentSession,
        )

    private class FakeSessionManager(
        private val valid: Boolean = true,
    ) : SessionManager {
        override suspend fun login(
            username: String,
            password: String,
        ): Result<Unit> = Result.success(Unit)

        override suspend fun isSessionValid(): Boolean = valid

        override suspend fun validatePersistedSession(): Boolean = valid

        override suspend fun clearSession() = Unit
    }

    private class FakeMessagesUrlProvider(
        private val url: HttpUrl? = "https://messages.test/lublin/App/odebrane".toHttpUrl(),
    ) : MessagesUrlProvider {
        override suspend fun getMessagesAppUrl(): HttpUrl =
            url ?: throw MessagesSessionException.MissingMessagesUrl
    }

    private class FakeMessagesSsoClient(private val onAuthenticate: suspend () -> Unit = {}) : MessagesSsoClient {
        var calls = 0

        override suspend fun authenticate(appUrl: HttpUrl): MessagesSessionContext {
            calls += 1
            onAuthenticate()
            return messagesContext(appUrl)
        }
    }

    private class FakeMessagesApiClient : MessagesApiClient {
        var unreadCalls = 0
        var receivedCalls = 0

        override suspend fun getUnreadCounts(
            context: MessagesSessionContext,
        ): List<UnreadMessageCountDto> {
            unreadCalls += 1
            return listOf(UnreadMessageCountDto(globalKey = "mailbox-key", count = 1))
        }

        override suspend fun getMailboxes(context: MessagesSessionContext): List<MailboxDto> =
            emptyList()

        override suspend fun getReceivedMessages(
            context: MessagesSessionContext,
            lastMessageId: Int,
            pageSize: Int,
        ): List<ReceivedMessageDto> =
            getMessages(
                context = context,
                mailboxType = MessageMailboxType.Received,
                lastMessageId = lastMessageId,
                pageSize = pageSize,
            )

        override suspend fun getMessages(
            context: MessagesSessionContext,
            mailboxType: MessageMailboxType,
            lastMessageId: Int,
            pageSize: Int,
        ): List<ReceivedMessageDto> {
            receivedCalls += 1
            return emptyList()
        }

        override suspend fun getMessageDetails(
            context: MessagesSessionContext,
            apiGlobalKey: String,
        ): MessageDetailsDto =
            error("not used")

        override suspend fun downloadAttachment(
            context: MessagesSessionContext,
            url: String,
            destination: java.io.File,
        ): String? = error("not used")

        override suspend fun markMessageAsRead(
            context: MessagesSessionContext,
            apiGlobalKey: String,
        ) = Unit
    }

    private companion object {
        fun messagesContext(
            appUrl: HttpUrl = "https://messages.test/lublin/App/odebrane".toHttpUrl(),
        ): MessagesSessionContext =
            MessagesSessionContext(
                appUrl = appUrl,
                apiBaseUrl = appUrl.toMessagesApiBaseUrl(),
                appGuid = "app-guid",
                requestVerificationToken = "verification-token",
            )
    }
}
