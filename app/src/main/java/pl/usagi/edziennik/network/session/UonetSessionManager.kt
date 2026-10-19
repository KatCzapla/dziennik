package pl.usagi.edziennik.network.session

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import pl.usagi.edziennik.network.cookie.PersistentCookieJar
import pl.usagi.edziennik.network.html.UonetHtmlFormParser
import pl.usagi.edziennik.network.messages.MessagesSessionStore
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UonetSessionManager @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val cookieJar: PersistentCookieJar,
    private val currentSession: CurrentAuthenticationSession,
    private val htmlFormParser: UonetHtmlFormParser,
    private val wsFederationNavigator: WsFederationNavigator,
    private val messagesSessionStore: MessagesSessionStore,
    private val json: Json,
) : SessionManager {
    override suspend fun login(
        username: String,
        password: String,
    ): Result<Unit> = currentSession.withAuthentication {
        currentSession.beginLogin()
        currentSession.prepareProjectLoginCandidate()
        withContext(Dispatchers.IO) {
            runCatchingAuthentication {
                val startResponse = executeGet(START_URL)
                startResponse.httpDiagnostic("PROJECT_UONET_START", inspectForm = true)
                val passwordPage = wsFederationNavigator.followBeforeLogin(startResponse, PROJECT_AUTH_HOSTS)
                val startUrl = passwordPage.request.url.toString()
                val loginForm = passwordPage.use { response ->
                    htmlFormParser.parseLoginForm(
                        html = response.body.string(),
                        currentUrl = startUrl,
                        username = username,
                        password = password,
                        onFailure = { reason -> authenticationDiagnostic("PROJECT_UONET_LOGIN_FORM", reason.name) },
                    ) ?: run {
                        authenticationDiagnostic("PROJECT_UONET_LOGIN_PAGE", response.safeLoginPageDiagnostic())
                        throw AuthenticationException.LoginFormNotFound()
                    }
                }

                val loginResponse = wsFederationNavigator.executeForm(loginForm, referer = startUrl, allowedHosts = PROJECT_AUTH_HOSTS)
                loginResponse.httpDiagnostic("PROJECT_UONET_SUBMIT", inspectForm = true)
                val afterLoginResponse = if (loginResponse.isLoginPage()) {
                    loginResponse.close()
                    throw AuthenticationException.InvalidCredentials()
                } else {
                    wsFederationNavigator.follow(loginResponse, MAIN_HOST, allowedHosts = PROJECT_AUTH_HOSTS)
                }

                afterLoginResponse.use { response ->
                    response.httpDiagnostic("PROJECT_UONET_FINAL")
                    val finalHost = response.request.url.host
                    if (finalHost != MAIN_HOST) {
                        throw AuthenticationException.MainApplicationAuthenticationFailed(finalHost)
                    }
                }

                when (checkSession()) {
                    SessionCheck.VALID -> Unit
                    SessionCheck.TRANSPORT_ERROR, SessionCheck.SERVER_ERROR ->
                        throw AuthenticationException.SessionVerificationUnavailable()
                    else -> throw AuthenticationException.SessionNotActive()
                }
            }
        }
    }

    override suspend fun isSessionValid(): Boolean = checkSession() == SessionCheck.VALID

    override suspend fun checkSession(): SessionCheck = currentSession.withAuthentication {
        withContext(Dispatchers.IO) {
            fun checked(result: SessionCheck, reason: String, response: Response? = null): SessionCheck {
                sessionCheckDiagnostic(result, reason, response)
                return result
            }
            val request = Request.Builder()
                .url(CONTEXT_URL)
                .header("Accept", ACCEPT_JSON)
                .get()
                .build()

            try {
                okHttpClient.newCall(request).execute().use { response ->
                    if (response.code == 401) return@withContext checked(SessionCheck.EXPIRED, "HTTP_UNAUTHORIZED", response)
                    if (response.code in setOf(408, 429) || response.code >= 500) {
                        return@withContext checked(SessionCheck.SERVER_ERROR, "HTTP_TRANSIENT_ERROR", response)
                    }
                    val finalUrl = response.request.url
                    val loginRedirect = (finalUrl.host == LOGIN_HOST && finalUrl.encodedPath.contains("/LoginPage.aspx")) ||
                        (finalUrl.host == "eduvulcan.pl" && finalUrl.encodedPath.startsWith("/logowanie"))
                    if (loginRedirect) return@withContext checked(SessionCheck.EXPIRED, "KNOWN_LOGIN_REDIRECT", response)
                    if (response.code != 200) return@withContext checked(SessionCheck.INVALID_RESPONSE, "UNEXPECTED_HTTP_STATUS", response)
                    if (!response.isJson()) {
                        response.httpDiagnostic("CONTEXT_UNEXPECTED_CONTENT", inspectForm = true)
                        return@withContext checked(SessionCheck.INVALID_RESPONSE,
                            if (response.header("Content-Type") == null) "MISSING_CONTENT_TYPE" else "UNEXPECTED_CONTENT_TYPE", response)
                    }

                    val body = response.body.string()
                    val root = json.parseToJsonElement(body).jsonObject
                    if ("uczniowie" in root) checked(SessionCheck.VALID, "STUDENTS_FIELD_PRESENT", response)
                    else checked(SessionCheck.INVALID_RESPONSE, "MISSING_STUDENTS_FIELD", response)
                }
            } catch (exception: IOException) {
                checked(SessionCheck.TRANSPORT_ERROR, "TRANSPORT_FAILURE")
            } catch (exception: SerializationException) {
                checked(SessionCheck.INVALID_RESPONSE, "INVALID_JSON")
            } catch (exception: IllegalArgumentException) {
                checked(SessionCheck.INVALID_RESPONSE, "INVALID_JSON_OBJECT")
            }
        }
    }

    override suspend fun validatePersistedSession(): Boolean = currentSession.withAuthentication {
        val result = checkSession()
        if (result == SessionCheck.EXPIRED) {
            clearSession()
        }
        result == SessionCheck.VALID
    }

    override suspend fun clearSession() = currentSession.withAuthentication {
        withContext(Dispatchers.IO) {
            authenticationDiagnostic("SESSION_CLEAR", "COOKIES_AND_MESSAGE_SESSION")
            currentSession.clearCookies {
                cookieJar.clear()
                messagesSessionStore.clear()
            }
        }
    }

    private fun executeGet(url: String): Response {
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        return execute(request)
    }

    private fun execute(request: Request): Response =
        try {
            okHttpClient.newCall(request).execute().also { response ->
                if (!response.isSuccessful && response.code !in REDIRECT_STATUS_CODES) {
                    response.httpDiagnostic("PROJECT_UONET_HTTP_ERROR")
                    val exception = AuthenticationException.Http(
                        statusCode = response.code,
                        url = response.request.url.toString(),
                    )
                    response.close()
                    throw exception
                }
            }
        } catch (exception: IOException) {
            throw AuthenticationException.Network(exception)
        }

    private fun Response.isLoginPage(): Boolean =
        request.url.host == LOGIN_HOST && request.url.encodedPath.contains("/LoginPage.aspx")

    private fun Response.isJson(): Boolean =
        header("Content-Type").orEmpty().contains("application/json", ignoreCase = true)

    private inline fun runCatchingAuthentication(block: () -> Unit): Result<Unit> =
        try {
            block()
            Result.success(Unit)
        } catch (exception: AuthenticationException) {
            authenticationDiagnostic("PROJECT_UONET_AUTH", exception::class.java.simpleName)
            Result.failure(exception)
        }

    private companion object {
        const val START_URL = "https://projekt-uczen.edu.lublin.eu/lublin/"
        const val CONTEXT_URL = "https://projekt-uczen.edu.lublin.eu/lublin/api/Context"
        const val MAIN_HOST = "projekt-uczen.edu.lublin.eu"
        const val LOGIN_HOST = "logowanie.edu.lublin.eu"
        val PROJECT_AUTH_HOSTS = setOf(MAIN_HOST, LOGIN_HOST, "cufs.edu.lublin.eu", "dziennik-logowanie.edu.lublin.eu")
        const val ACCEPT_JSON = "application/json, text/plain, */*"

        val REDIRECT_STATUS_CODES = 300..399
    }
}
