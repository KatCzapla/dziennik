package pl.usagi.edziennik.network.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.asContextElement
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

class CurrentAuthenticationSession(
    private val projectCookieJar: CookieJar,
    private val clearStoredSession: () -> Unit = {},
    private val replaceProjectCookies: ((List<Cookie>) -> Unit)? = null,
) : CookieJar {
    private class AuthenticationOperation(
        val owner: CurrentAuthenticationSession,
        val job: Job?,
        var generation: Long,
        var loginStarted: Boolean = false,
        var manualLogin: Boolean = false,
        var projectCandidate: CandidateCookies? = null,
    ) : AbstractCoroutineContextElement(Key) {
        companion object Key : CoroutineContext.Key<AuthenticationOperation>
    }

    private data class RequestSession(
        val generation: Long,
        val provider: AuthenticationProvider?,
        val cookies: CookieJar,
        val operation: AuthenticationOperation?,
    )

    private class CandidateCookies : CookieJar {
        private val cookies = mutableListOf<Cookie>()

        @Synchronized
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            this.cookies.removeAll { stored ->
                cookies.any { it.name == stored.name && it.domain == stored.domain && it.path == stored.path }
            }
            this.cookies += cookies.filter { it.expiresAt > System.currentTimeMillis() }
        }

        @Synchronized
        override fun loadForRequest(url: HttpUrl): List<Cookie> = snapshot().filter { it.matches(url) }

        @Synchronized
        fun snapshot(): List<Cookie> {
            cookies.removeAll { it.expiresAt <= System.currentTimeMillis() }
            return cookies.toList()
        }
    }

    private val authenticationMutex = Mutex()
    private val operationOnThread = ThreadLocal<AuthenticationOperation?>()
    private val requestOnThread = ThreadLocal<RequestSession?>()
    private val authenticationJobs = mutableSetOf<Job>()
    private var activeOperation: AuthenticationOperation? = null
    private data class ActiveSession(
        val provider: AuthenticationProvider,
        val cookieJar: CookieJar,
    )

    @Volatile
    private var activeSession: ActiveSession? = null
    private var generation = 0L
    private val _loggedInUser = MutableStateFlow<LoggedInUser?>(null)
    val loggedInUser: StateFlow<LoggedInUser?> = _loggedInUser.asStateFlow()

    val provider: AuthenticationProvider?
        get() = activeSession?.provider

    @Synchronized
    fun hasSessionCookies(provider: AuthenticationProvider): Boolean {
        val cookies = activeSession?.takeIf { it.provider == provider }?.cookieJar ?: projectCookieJar
        val host = if (provider == AuthenticationProvider.EDUVULCAN) EDUVULCAN_UONET_HOST else PROJECT_HOST
        val url = "https://$host/lublin/api/Context".toHttpUrl()
        val now = System.currentTimeMillis()
        return cookies.loadForRequest(url).any { it.expiresAt > now && it.matches(url) }
    }

    suspend fun <T> withAuthentication(block: suspend () -> T): T {
        val context = currentCoroutineContext()
        if (context[AuthenticationOperation]?.owner === this) {
            ensureAuthenticationCurrent()
            return block().also { ensureAuthenticationCurrent() }
        }
        val job = context[Job]
        synchronized(this) {
            job?.ensureActive()
            job?.let(authenticationJobs::add)
        }
        try {
            return authenticationMutex.withLock {
                val operation = synchronized(this) {
                    job?.ensureActive()
                    AuthenticationOperation(this, job, generation).also { activeOperation = it }
                }
                try {
                    withContext(operation + operationOnThread.asContextElement(operation)) {
                        ensureAuthenticationCurrent()
                        block().also { ensureAuthenticationCurrent() }
                    }
                } catch (cancelled: CancellationException) {
                    synchronized(this) {
                        if (operation.loginStarted && !operation.manualLogin && operation.generation == generation) {
                            invalidateSession()
                            clearStoredSession()
                        }
                    }
                    throw cancelled
                } finally {
                    synchronized(this) { if (activeOperation === operation) activeOperation = null }
                }
            }
        } finally {
            synchronized(this) { authenticationJobs.remove(job) }
        }
    }

    suspend fun <T> withManualLogin(block: suspend () -> T): T = withAuthentication {
        val operation = checkNotNull(currentCoroutineContext()[AuthenticationOperation])
        synchronized(this) {
            ensureAuthenticationCurrent()
            operation.manualLogin = true
        }
        try {
            block()
        } finally {
            synchronized(this) { operation.projectCandidate = null }
        }
    }

    @Synchronized
    fun prepareProjectLoginCandidate() {
        ensureAuthenticationCurrent()
        operationOnThread.get()?.takeIf { it.manualLogin }?.let {
            if (it.projectCandidate == null) it.projectCandidate = CandidateCookies()
        }
    }

    @Synchronized
    fun hasProjectLoginCandidate(): Boolean = operationOnThread.get()?.projectCandidate != null

    @Synchronized
    fun ensureAuthenticationCurrent() {
        operationOnThread.get()?.let { operation ->
            operation.job?.ensureActive()
            if (operation.generation != generation) throw CancellationException("Session changed")
        }
    }

    @Synchronized
    fun beginLogin() {
        ensureAuthenticationCurrent()
        operationOnThread.get()?.takeIf { it.manualLogin }?.let { operation ->
            // Nested UI/provider beginLogin calls belong to one attempt, not new sessions.
            if (!operation.loginStarted) {
                generation++
                operation.generation = generation
                operation.loginStarted = true
            }
            return
        }
        operationOnThread.get()?.loginStarted = true
        clear()
    }

    // Invalidate immediately, before waiting for a blocking HTTP login to finish.
    @Synchronized
    fun invalidateAuthentication() {
        authenticationJobs.toList().forEach { it.cancel(CancellationException("Session ended")) }
        invalidateSession()
        clearStoredSession()
    }

    @Synchronized
    fun commitAuthenticationState(block: () -> Unit) {
        ensureAuthenticationCurrent()
        block()
    }

    @Synchronized
    fun clearCookies(block: () -> Unit) {
        clear()
        block()
    }

    // OkHttp's synchronous interceptor chain and CookieJar callbacks use the same thread.
    // Bind redirects to the original session as well, not to the session active at response time.
    fun <T> withRequestSession(block: () -> T): T {
        val requestSession = synchronized(this) {
            ensureAuthenticationCurrent()
            val operation = operationOnThread.get()
            val candidate = operation?.projectCandidate
            RequestSession(generation,
                if (candidate != null) AuthenticationProvider.PROJECT_UONET else activeSession?.provider,
                candidate ?: activeSession?.cookieJar ?: projectCookieJar, operation)
        }
        val previous = requestOnThread.get()
        requestOnThread.set(requestSession)
        try {
            return block()
        } finally {
            requestOnThread.set(previous)
        }
    }

    @Synchronized
    fun activate(success: AutoDetectLoginResult.Success) {
        ensureAuthenticationCurrent()
        val operation = operationOnThread.get()
        val candidate = operation?.projectCandidate?.takeIf { success.provider == AuthenticationProvider.PROJECT_UONET }
        if (candidate != null) {
            // Promotion is the commit point; a rejected or cancelled candidate never reaches it.
            checkNotNull(replaceProjectCookies) { "Project cookie replacement is not configured" }(candidate.snapshot())
            operation.projectCandidate = null
        }
        val cookieJar = when (success.provider) {
            AuthenticationProvider.EDUVULCAN -> requireNotNull(success.eduVulcanClient).cookieJar
            AuthenticationProvider.PROJECT_UONET -> projectCookieJar
        }
        activeSession = ActiveSession(success.provider, cookieJar)
        generation++
        operationOnThread.get()?.generation = generation
        _loggedInUser.value = null
    }

    @Synchronized
    fun restore(provider: AuthenticationProvider) {
        ensureAuthenticationCurrent()
        activeSession = ActiveSession(provider, projectCookieJar)
        generation++
        operationOnThread.get()?.generation = generation
        _loggedInUser.value = null
    }

    @Synchronized
    fun clear() {
        ensureAuthenticationCurrent()
        invalidateSession()
        operationOnThread.get()?.generation = generation
    }

    private fun invalidateSession() {
        activeSession = null
        generation++
        _loggedInUser.value = null
    }

    @Synchronized
    fun generation(): Long = generation

    @Synchronized
    fun setLoggedInUserIfCurrent(expectedGeneration: Long, user: LoggedInUser) {
        if (generation == expectedGeneration) _loggedInUser.value = user
    }

    fun route(request: Request): Request {
        val binding = requestOnThread.get()
        val requestProvider = if (binding != null) binding.provider else provider
        if (requestProvider != AuthenticationProvider.EDUVULCAN || request.url.host != PROJECT_HOST) {
            return request
        }
        return request.newBuilder()
            .url(request.url.newBuilder().host(EDUVULCAN_UONET_HOST).build())
            .build()
    }

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        // Unbound callbacks (including late body trailers) cannot modify a newer session.
        val request = requestOnThread.get() ?: return
        if (request.isCurrent()) request.cookies.saveFromResponse(url, cookies)
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> =
        requestOnThread.get()?.let { request ->
            if (request.isCurrent()) request.cookies.loadForRequest(url) else emptyList()
        } ?: (operationOnThread.get()?.projectCandidate ?: activeSession?.cookieJar ?: projectCookieJar).loadForRequest(url)

    private fun RequestSession.isCurrent(): Boolean =
        generation == this@CurrentAuthenticationSession.generation && operation?.job?.isActive != false &&
            (provider != null || operation?.loginStarted == true) &&
            (activeOperation?.loginStarted != true || operation === activeOperation)

    private companion object {
        const val PROJECT_HOST = "projekt-uczen.edu.lublin.eu"
        const val EDUVULCAN_UONET_HOST = "uczen.edu.lublin.eu"
    }
}
