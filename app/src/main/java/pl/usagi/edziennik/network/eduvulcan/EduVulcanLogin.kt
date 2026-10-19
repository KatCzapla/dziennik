package pl.usagi.edziennik.network.eduvulcan

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import pl.usagi.edziennik.network.html.UonetHtmlFormParser
import pl.usagi.edziennik.network.session.WsFederationNavigator
import pl.usagi.edziennik.network.session.AuthenticationException
import pl.usagi.edziennik.network.session.CurrentAuthenticationSession
import pl.usagi.edziennik.network.session.authenticationDiagnostic

sealed interface EduVulcanLoginResult {
    data object Accepted : EduVulcanLoginResult
    class Authenticated(val client: OkHttpClient) : EduVulcanLoginResult
    data object InvalidCredentials : EduVulcanLoginResult
    data object TechnicalError : EduVulcanLoginResult
    data object TransientError : EduVulcanLoginResult
}

class EduVulcanLogin(
    private val baseClient: OkHttpClient,
    private val json: Json = Json,
    private val persistentCookies: CookieJar? = null,
    private val currentSession: CurrentAuthenticationSession? = null,
) {
    suspend fun checkCredentials(username: String, password: String): EduVulcanLoginResult =
        withContext(Dispatchers.IO) {
            try {
                checkCredentials(newSessionClient(), username, password)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                exception.loginFailureResult()
            }
        }

    suspend fun authenticate(username: String, password: String): EduVulcanLoginResult =
        if (currentSession == null) authenticateSession(username, password)
        else currentSession.withAuthentication {
            currentSession.beginLogin()
            authenticateSession(username, password)
        }

    private suspend fun authenticateSession(username: String, password: String): EduVulcanLoginResult =
        withContext(Dispatchers.IO) {
            try {
                val sessionClient = newSessionClient()
                when (val result = checkCredentials(sessionClient, username, password)) {
                    EduVulcanLoginResult.Accepted -> {
                        val authenticated = authenticateJournal(sessionClient)
                        if (authenticated is EduVulcanLoginResult.Authenticated && persistentCookies != null) {
                            val copyCookies = {
                                copyAuthenticatedCookies(
                                    (sessionClient.cookieJar as SessionCookies).snapshot(), persistentCookies,
                                )
                            }
                            if (currentSession != null) currentSession.commitAuthenticationState(copyCookies)
                            else copyCookies()
                            EduVulcanLoginResult.Authenticated(
                                sessionClient.newBuilder().cookieJar(persistentCookies).build(),
                            )
                        } else authenticated
                    }
                    else -> result
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                exception.loginFailureResult()
            }
        }

    private fun newSessionClient(): OkHttpClient = baseClient.newBuilder()
        .cookieJar(SessionCookies())
        .build()

    private fun checkCredentials(
        sessionClient: OkHttpClient,
        username: String,
        password: String,
    ): EduVulcanLoginResult {
        val loginPage = sessionClient.newCall(
            Request.Builder().url(LOGIN_URL).get().build(),
        ).execute().use { response ->
            if (!response.isSuccessful) return response.loginFailureResult()
            response.body.string()
        }

        val document = Jsoup.parse(loginPage)
        val csrf = document.selectFirst("input[name=__RequestVerificationToken]")
            ?.attr("value")?.takeIf(String::isNotEmpty)
            ?: return technicalFailure("EDUVULCAN_CSRF_INPUT")
        val captcha = document.selectFirst(".captcha-wrapper")
            ?: return technicalFailure("EDUVULCAN_CAPTCHA_ELEMENT")
        val challenge = captcha.attr("data-challenge").takeIf(String::isNotEmpty)
            ?: return technicalFailure("EDUVULCAN_CAPTCHA_CHALLENGE")
        val difficulty = captcha.attr("data-difficulty").toLongOrNull()
            ?: return technicalFailure("EDUVULCAN_CAPTCHA_DIFFICULTY")
        val rounds = captcha.attr("data-rounds").toIntOrNull()
            ?: return technicalFailure("EDUVULCAN_CAPTCHA_ROUNDS")

        val queryResponse = sessionClient.postForm(
            QUERY_USER_URL,
            FormBody.Builder()
                .add("alias", username)
                .add("__RequestVerificationToken", csrf)
                .build(),
        )
        val querySucceeded = queryResponse.use { response ->
            if (!response.isSuccessful) return response.loginFailureResult()
            response.isSuccessful &&
                json.parseToJsonElement(response.body.string())
                    .jsonObject["success"]?.jsonPrimitive?.booleanOrNull == true
        }
        if (!querySucceeded) return technicalFailure("EDUVULCAN_QUERY_USER")

        val proof = EduVulcanProofOfWork.solve(challenge, difficulty, rounds)
        val loginResponse = sessionClient.newBuilder().followRedirects(false).build()
            .postForm(
                LOGIN_URL,
                FormBody.Builder()
                    .add("UserName", username)
                    .add("Password", password)
                    .add("captcha-response", proof)
                    .add("__RequestVerificationToken", csrf)
                    .build(),
            )
        return loginResponse.use { response ->
            when {
                response.code == 302 -> EduVulcanLoginResult.Accepted
                response.code == 200 &&
                    "Zła nazwa użytkownika lub hasło" in response.body.string() ->
                    EduVulcanLoginResult.InvalidCredentials
                else -> response.loginFailureResult()
            }
        }
    }

    private fun authenticateJournal(sessionClient: OkHttpClient): EduVulcanLoginResult {
        val journalsResponse = sessionClient.newCall(
            Request.Builder().url(JOURNALS_URL).get().build(),
        ).execute()
        val journalUrl = journalsResponse.use { response ->
            if (!response.isSuccessful) return response.loginFailureResult()
            val document = Jsoup.parse(response.body.string())
            val links = document.select("a[href]")
                .mapNotNull { link ->
                    link.attr("href").takeIf { "/dziennik?" in it }
                        ?.let(response.request.url::resolve)
                }
            if (links.size != 1) return technicalFailure("EDUVULCAN_JOURNAL_SELECTION")
            links.single()
        }

        val journalResponse = sessionClient.newCall(
            Request.Builder().url(journalUrl).get().build(),
        ).execute()
        if (!journalResponse.isSuccessful) {
            val failure = journalResponse.loginFailureResult()
            journalResponse.close()
            return failure
        }

        WsFederationNavigator(sessionClient, UonetHtmlFormParser())
            .follow(journalResponse, UONET_HOST)
            .use { response ->
                if (!response.isSuccessful) return response.loginFailureResult()
            }

        val contextRequest = Request.Builder()
            .url(CONTEXT_URL)
            .header("Accept", "application/json, text/plain, */*")
            .get()
            .build()
        val isSessionValid = sessionClient.newCall(contextRequest).execute().use { response ->
            if (!response.isSuccessful) return response.loginFailureResult()
            response.code == 200 && response.request.url.host == UONET_HOST &&
                response.header("Content-Type").orEmpty()
                    .contains("application/json", ignoreCase = true) &&
                "uczniowie" in json.parseToJsonElement(response.body.string()).jsonObject
        }
        return if (isSessionValid) {
            EduVulcanLoginResult.Authenticated(sessionClient)
        } else {
            technicalFailure("EDUVULCAN_CONTEXT_VERIFICATION")
        }
    }

    private fun OkHttpClient.postForm(url: String, body: FormBody): Response =
        newCall(
            Request.Builder()
                .url(url)
                .header("Referer", LOGIN_URL)
                .post(body)
                .build(),
        ).execute()

    private class SessionCookies : CookieJar {
        private val cookies = mutableListOf<Cookie>()

        @Synchronized
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            this.cookies.removeAll { stored ->
                cookies.any { incoming ->
                    stored.name == incoming.name &&
                        stored.domain == incoming.domain &&
                        stored.path == incoming.path
                }
            }
            this.cookies += cookies.filter { it.expiresAt > System.currentTimeMillis() }
        }

        @Synchronized
        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            cookies.removeAll { it.expiresAt <= System.currentTimeMillis() }
            return cookies.filter { it.matches(url) }
        }

        @Synchronized
        fun snapshot(): List<Cookie> = cookies.filter { it.expiresAt > System.currentTimeMillis() }
    }

    private companion object {
        const val LOGIN_URL = "https://eduvulcan.pl/logowanie"
        const val QUERY_USER_URL = "https://eduvulcan.pl/Account/QueryUserInfo"
        const val JOURNALS_URL = "https://eduvulcan.pl/dostep-do-dziennika/"
        const val UONET_HOST = "uczen.edu.lublin.eu"
        const val CONTEXT_URL = "https://uczen.edu.lublin.eu/lublin/api/Context"
    }
}

internal fun copyAuthenticatedCookies(cookies: List<Cookie>, target: CookieJar) {
    cookies.forEach { cookie ->
        target.saveFromResponse("https://${cookie.domain}/".toHttpUrl(), listOf(cookie))
    }
}

private fun Response.loginFailureResult(): EduVulcanLoginResult {
    authenticationDiagnostic("EDUVULCAN_HTTP", code.toString())
    return if (code in setOf(408, 429) || code >= 500) EduVulcanLoginResult.TransientError else EduVulcanLoginResult.TechnicalError
}

private fun Throwable.loginFailureResult(): EduVulcanLoginResult {
    authenticationDiagnostic("EDUVULCAN_AUTH_EXCEPTION", this::class.java.simpleName)
    return when {
    this is java.io.IOException || this is AuthenticationException.Network -> EduVulcanLoginResult.TransientError
    this is AuthenticationException.Http && (statusCode in setOf(408, 429) || statusCode >= 500) -> EduVulcanLoginResult.TransientError
    else -> EduVulcanLoginResult.TechnicalError
    }
}

private fun technicalFailure(stage: String): EduVulcanLoginResult {
    authenticationDiagnostic(stage, "TECHNICAL_ERROR")
    return EduVulcanLoginResult.TechnicalError
}
