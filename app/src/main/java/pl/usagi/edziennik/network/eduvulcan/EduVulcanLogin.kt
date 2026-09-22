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

sealed interface EduVulcanLoginResult {
    data object Accepted : EduVulcanLoginResult
    class Authenticated(val client: OkHttpClient) : EduVulcanLoginResult
    data object InvalidCredentials : EduVulcanLoginResult
    data object TechnicalError : EduVulcanLoginResult
}

class EduVulcanLogin(
    private val baseClient: OkHttpClient,
    private val json: Json = Json,
    private val persistentCookies: CookieJar? = null,
) {
    suspend fun checkCredentials(username: String, password: String): EduVulcanLoginResult =
        withContext(Dispatchers.IO) {
            try {
                checkCredentials(newSessionClient(), username, password)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                EduVulcanLoginResult.TechnicalError
            }
        }

    suspend fun authenticate(username: String, password: String): EduVulcanLoginResult =
        withContext(Dispatchers.IO) {
            try {
                val sessionClient = newSessionClient()
                when (val result = checkCredentials(sessionClient, username, password)) {
                    EduVulcanLoginResult.Accepted -> {
                        val authenticated = authenticateJournal(sessionClient)
                        if (authenticated is EduVulcanLoginResult.Authenticated && persistentCookies != null) {
                            copyAuthenticatedCookies(
                                (sessionClient.cookieJar as SessionCookies).snapshot(), persistentCookies,
                            )
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
                EduVulcanLoginResult.TechnicalError
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
            if (!response.isSuccessful) return EduVulcanLoginResult.TechnicalError
            response.body.string()
        }

        val document = Jsoup.parse(loginPage)
        val csrf = document.selectFirst("input[name=__RequestVerificationToken]")
            ?.attr("value")?.takeIf(String::isNotEmpty)
            ?: return EduVulcanLoginResult.TechnicalError
        val captcha = document.selectFirst(".captcha-wrapper")
            ?: return EduVulcanLoginResult.TechnicalError
        val challenge = captcha.attr("data-challenge").takeIf(String::isNotEmpty)
            ?: return EduVulcanLoginResult.TechnicalError
        val difficulty = captcha.attr("data-difficulty").toLongOrNull()
            ?: return EduVulcanLoginResult.TechnicalError
        val rounds = captcha.attr("data-rounds").toIntOrNull()
            ?: return EduVulcanLoginResult.TechnicalError

        val queryResponse = sessionClient.postForm(
            QUERY_USER_URL,
            FormBody.Builder()
                .add("alias", username)
                .add("__RequestVerificationToken", csrf)
                .build(),
        )
        val querySucceeded = queryResponse.use { response ->
            response.isSuccessful &&
                json.parseToJsonElement(response.body.string())
                    .jsonObject["success"]?.jsonPrimitive?.booleanOrNull == true
        }
        if (!querySucceeded) return EduVulcanLoginResult.TechnicalError

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
                else -> EduVulcanLoginResult.TechnicalError
            }
        }
    }

    private fun authenticateJournal(sessionClient: OkHttpClient): EduVulcanLoginResult {
        val journalsResponse = sessionClient.newCall(
            Request.Builder().url(JOURNALS_URL).get().build(),
        ).execute()
        val journalUrl = journalsResponse.use { response ->
            if (!response.isSuccessful) return EduVulcanLoginResult.TechnicalError
            val document = Jsoup.parse(response.body.string())
            val links = document.select("a[href]")
                .mapNotNull { link ->
                    link.attr("href").takeIf { "/dziennik?" in it }
                        ?.let(response.request.url::resolve)
                }
            if (links.size != 1) return EduVulcanLoginResult.TechnicalError
            links.single()
        }

        val journalResponse = sessionClient.newCall(
            Request.Builder().url(journalUrl).get().build(),
        ).execute()
        if (!journalResponse.isSuccessful) {
            journalResponse.close()
            return EduVulcanLoginResult.TechnicalError
        }

        WsFederationNavigator(sessionClient, UonetHtmlFormParser())
            .follow(journalResponse, UONET_HOST)
            .use { response ->
                if (!response.isSuccessful) return EduVulcanLoginResult.TechnicalError
            }

        val contextRequest = Request.Builder()
            .url(CONTEXT_URL)
            .header("Accept", "application/json, text/plain, */*")
            .get()
            .build()
        val isSessionValid = sessionClient.newCall(contextRequest).execute().use { response ->
            response.code == 200 && response.request.url.host == UONET_HOST &&
                response.header("Content-Type").orEmpty()
                    .contains("application/json", ignoreCase = true) &&
                "uczniowie" in json.parseToJsonElement(response.body.string()).jsonObject
        }
        return if (isSessionValid) {
            EduVulcanLoginResult.Authenticated(sessionClient)
        } else {
            EduVulcanLoginResult.TechnicalError
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
