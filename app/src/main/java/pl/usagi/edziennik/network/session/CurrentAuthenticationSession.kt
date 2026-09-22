package pl.usagi.edziennik.network.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.Request

class CurrentAuthenticationSession(
    private val projectCookieJar: CookieJar,
) : CookieJar {
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
    fun activate(success: AutoDetectLoginResult.Success) {
        val cookieJar = when (success.provider) {
            AuthenticationProvider.EDUVULCAN -> requireNotNull(success.eduVulcanClient).cookieJar
            AuthenticationProvider.PROJECT_UONET -> projectCookieJar
        }
        activeSession = ActiveSession(success.provider, cookieJar)
        generation++
        _loggedInUser.value = null
    }

    @Synchronized
    fun restore(provider: AuthenticationProvider) {
        activeSession = ActiveSession(provider, projectCookieJar)
        generation++
        _loggedInUser.value = null
    }

    @Synchronized
    fun clear() {
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
        if (provider != AuthenticationProvider.EDUVULCAN || request.url.host != PROJECT_HOST) {
            return request
        }
        return request.newBuilder()
            .url(request.url.newBuilder().host(EDUVULCAN_UONET_HOST).build())
            .build()
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        (activeSession?.cookieJar ?: projectCookieJar).saveFromResponse(url, cookies)
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> =
        (activeSession?.cookieJar ?: projectCookieJar).loadForRequest(url)

    private companion object {
        const val PROJECT_HOST = "projekt-uczen.edu.lublin.eu"
        const val EDUVULCAN_UONET_HOST = "uczen.edu.lublin.eu"
    }
}
