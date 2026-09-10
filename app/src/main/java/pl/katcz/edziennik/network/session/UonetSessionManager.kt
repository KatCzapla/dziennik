package pl.katcz.edziennik.network.session

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import pl.katcz.edziennik.network.cookie.PersistentCookieJar
import pl.katcz.edziennik.network.html.HtmlForm
import pl.katcz.edziennik.network.html.UonetHtmlFormParser
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UonetSessionManager @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val cookieJar: PersistentCookieJar,
    private val htmlFormParser: UonetHtmlFormParser,
    private val json: Json,
) {
    suspend fun login(
        username: String,
        password: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatchingAuthentication {
            val startResponse = executeGet(START_URL)
            val startUrl = startResponse.request.url.toString()
            val loginForm = startResponse.use { response ->
                htmlFormParser.parseLoginForm(
                    html = response.body.string(),
                    currentUrl = startUrl,
                    username = username,
                    password = password,
                ) ?: throw AuthenticationException.LoginFormNotFound()
            }

            val loginResponse = executeFormPost(loginForm, referer = startUrl)
            val afterLoginResponse = if (loginResponse.isLoginPage()) {
                loginResponse.close()
                throw AuthenticationException.InvalidCredentials()
            } else {
                followWsFederation(loginResponse, MAIN_HOST)
            }

            afterLoginResponse.use { response ->
                val finalHost = response.request.url.host
                if (finalHost != MAIN_HOST) {
                    throw AuthenticationException.MainApplicationAuthenticationFailed(finalHost)
                }
            }

            if (!isSessionValid()) {
                throw AuthenticationException.SessionNotActive()
            }
        }
    }

    suspend fun isSessionValid(): Boolean = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(CONTEXT_URL)
            .header("Accept", ACCEPT_JSON)
            .get()
            .build()

        try {
            okHttpClient.newCall(request).execute().use { response ->
                if (response.code != 200) return@withContext false
                if (!response.isJson()) return@withContext false

                val body = response.body.string()
                val root = json.parseToJsonElement(body).jsonObject
                "uczniowie" in root
            }
        } catch (exception: IOException) {
            false
        } catch (exception: SerializationException) {
            false
        } catch (exception: IllegalArgumentException) {
            false
        }
    }

    suspend fun validatePersistedSession(): Boolean {
        val isValid = isSessionValid()
        if (!isValid) {
            clearSession()
        }
        return isValid
    }

    suspend fun clearSession() = withContext(Dispatchers.IO) {
        cookieJar.clear()
    }

    private fun followWsFederation(
        initialResponse: Response,
        targetHost: String,
        maxSteps: Int = 10,
    ): Response {
        var currentResponse = initialResponse

        repeat(maxSteps) {
            if (currentResponse.request.url.host == targetHost) {
                return currentResponse
            }

            val statusCode = currentResponse.code
            val currentUrl = currentResponse.request.url.toString()
            val body = currentResponse.body.string()
            currentResponse.close()

            val form = htmlFormParser.parseAutoPostForm(body, currentUrl)
                ?: throw AuthenticationException.WsFederationFormNotFound(statusCode, currentUrl)

            currentResponse = executeFormPost(form, referer = currentUrl)
        }

        currentResponse.close()
        throw AuthenticationException.WsFederationStepLimitExceeded(targetHost)
    }

    private fun executeGet(url: String): Response {
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        return execute(request)
    }

    private fun executeFormPost(
        form: HtmlForm,
        referer: String,
    ): Response {
        val body = FormBody.Builder().apply {
            form.fields.forEach { (name, value) -> add(name, value) }
        }.build()

        val request = Request.Builder()
            .url(form.actionUrl)
            .header("Referer", referer)
            .post(body)
            .build()

        return execute(request)
    }

    private fun execute(request: Request): Response =
        try {
            okHttpClient.newCall(request).execute().also { response ->
                if (!response.isSuccessful && response.code !in REDIRECT_STATUS_CODES) {
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
            Result.failure(exception)
        }

    private companion object {
        const val START_URL = "https://projekt-uczen.edu.lublin.eu/lublin/"
        const val CONTEXT_URL = "https://projekt-uczen.edu.lublin.eu/lublin/api/Context"
        const val MAIN_HOST = "projekt-uczen.edu.lublin.eu"
        const val LOGIN_HOST = "logowanie.edu.lublin.eu"
        const val ACCEPT_JSON = "application/json, text/plain, */*"

        val REDIRECT_STATUS_CODES = 300..399
    }
}
