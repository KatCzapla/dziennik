package pl.usagi.edziennik.network.session

import android.app.Application
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import pl.usagi.edziennik.network.cookie.PersistentCookieJar
import pl.usagi.edziennik.network.html.UonetHtmlFormParser
import pl.usagi.edziennik.network.messages.MessagesSessionStore
import java.io.IOException
import pl.usagi.edziennik.network.UonetApiClient
import pl.usagi.edziennik.network.context.UonetContextDataSource
import pl.usagi.edziennik.repository.UonetStudentRepository
import pl.usagi.edziennik.repository.UonetTimetableRepository
import pl.usagi.edziennik.network.timetable.UonetTimetableDataSource
import pl.usagi.edziennik.domain.ActiveStudentManager
import java.time.LocalDate
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class UonetSessionVerificationTest {
    private val url = "https://projekt-uczen.edu.lublin.eu/lublin/api/Context".toHttpUrl()
    private val jar = PersistentCookieJar(RuntimeEnvironment.getApplication(), Json).apply {
        saveFromResponse(url, listOf(Cookie.Builder().name("test-session").value("test")
            .domain(url.host).expiresAt(System.currentTimeMillis() + 60_000).build()))
    }

    @Test fun missingFormFailureIsReproducedBeforeCredentialsPostOrContext() = runBlocking {
        val requests = mutableListOf<okhttp3.Request>()
        val result = manager(body = "<html>private-page-marker</html>", contentType = "text/html", requests = requests)
            .login("private-login-marker", "private-password-marker")
        assertTrue(result.exceptionOrNull() is AuthenticationException.LoginFormNotFound)
        assertEquals(1, requests.size)
        assertEquals("GET", requests.single().method)
        assertEquals("/lublin/", requests.single().url.encodedPath)
        val logs = ShadowLog.getLogsForTag("UonetAuth").map { it.msg }
        assertTrue(logs.any { it == "stage=PROJECT_UONET_LOGIN_FORM result=NO_PASSWORD_INPUT" })
        assertTrue(logs.any { it == "stage=PROJECT_UONET_LOGIN_PAGE result=http=200 target=PROJECT_UONET content=HTML" })
        assertFalse(logs.joinToString().contains("private-"))
    }

    @Test fun loginResponseDiagnosticNeverIncludesUrlParametersOrHeaderValues() {
        val response = Response.Builder().request(okhttp3.Request.Builder()
            .url("https://private-host.example/login?token=private-token").build())
            .protocol(Protocol.HTTP_1_1).code(200).message("private-message")
            .header("Content-Type", "text/html; private-header-value")
            .body("private-body".toResponseBody()).build()
        response.use { assertEquals("http=200 target=OTHER content=HTML", it.safeLoginPageDiagnostic()) }
    }

    @Test fun realProjectLoginAndContextMappingAcceptOptionalUnitIdLocally() = runBlocking {
        for (field in listOf("", ",\"idJednostkaSkladowa\":null", ",\"idJednostkaSkladowa\":1220")) {
            val body = """{"uczniowie":[{"key":"runtime","idDziennik":1,"uczen":"Uczeń","oddzial":"1A","jednostka":"Szkoła"$field}]}"""
            val (session, client) = projectFixture(body)
            assertTrue(session.login("test", "test").isSuccess)
            val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
            val students = UonetStudentRepository(UonetContextDataSource(UonetApiClient(client, json))).getStudents()
            assertEquals(if (field.contains("1220")) 1220 else null, students.single().organizationalUnitId)
        }
    }

    @Test fun daysOffFailureAfterAuthenticationDoesNotInvalidateSession() = runBlocking {
        val body = """{"uczniowie":[{"key":"runtime","idDziennik":1,"uczen":"Uczeń","oddzial":"1A","jednostka":"Szkoła","idJednostkaSkladowa":1220}]}"""
        val (session, client) = projectFixture(body)
        val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
        assertTrue(session.login("test", "test").isSuccess)
        val students = UonetStudentRepository(UonetContextDataSource(UonetApiClient(client, json))).getStudents()
        val active = ActiveStudentManager().apply { initialize(students) }
        val timetable = UonetTimetableRepository(active, UonetTimetableDataSource(UonetApiClient(client, json)))
        val date = LocalDate.of(2026, 10, 14)
        assertTrue(runCatching { timetable.getDaysOff(date, date) }.isFailure)
        assertEquals(SessionCheck.VALID, session.checkSession())
        assertEquals(students.single(), active.activeStudent.value)
    }

    private fun projectFixture(contextBody: String): Pair<UonetSessionManager, OkHttpClient> {
        val current = CurrentAuthenticationSession(jar)
        val client = OkHttpClient.Builder().cookieJar(current).addInterceptor { chain ->
            val request = chain.request()
            val (code, type, body) = when (request.url.encodedPath to request.method) {
                "/lublin/" to "GET" -> Triple(200, "text/html", """
                    <form action="https://logowanie.edu.lublin.eu/Account/Login">
                      <input name="username" type="text"><input name="password" type="password">
                    </form>
                """.trimIndent())
                "/Account/Login" to "POST" -> Triple(200, "text/html", """
                    <form action="https://projekt-uczen.edu.lublin.eu/lublin/">
                      <input name="wresult" type="hidden" value="test">
                    </form>
                """.trimIndent())
                "/lublin/" to "POST" -> Triple(200, "text/html", "<html>test</html>")
                "/lublin/api/Context" to "GET" -> Triple(200, "application/json", contextBody)
                "/lublin/api/DniWolne" to "GET" -> Triple(503, "application/json", "{}")
                else -> error("Unmocked request is forbidden in this local test")
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("test")
                .header("Content-Type", type).body(body.toResponseBody()).build()
        }.build()
        val parser = UonetHtmlFormParser()
        val session = UonetSessionManager(client, jar, current, parser,
            WsFederationNavigator(client, parser), MessagesSessionStore(), Json { ignoreUnknownKeys = true; explicitNulls = false })
        return session to client
    }

    @Test fun validJsonContextIsValidSession() = runBlocking {
        assertEquals(SessionCheck.VALID, manager().checkSession())
    }

    @Test fun transportFailureIsNotExpirationAndDoesNotDeleteCookies() = runBlocking {
        val manager = manager(transportFailure = true)
        assertEquals(SessionCheck.TRANSPORT_ERROR, manager.checkSession())
        assertFalse(manager.validatePersistedSession())
        assertEquals(1, jar.loadForRequest(url).size)
    }

    @Test fun serverFailureDoesNotDeleteCookies() = runBlocking {
        for (code in listOf(408, 429, 500, 503)) {
            val manager = manager(code = code)
            assertEquals(SessionCheck.SERVER_ERROR, manager.checkSession())
            assertFalse(manager.validatePersistedSession())
            assertEquals(1, jar.loadForRequest(url).size)
        }
    }

    @Test fun unauthorizedContextIsExpiredAndPersistedValidationClearsCookies() = runBlocking {
        val manager = manager(code = 401)
        assertEquals(SessionCheck.EXPIRED, manager.checkSession())
        assertFalse(manager.validatePersistedSession())
        assertTrue(jar.loadForRequest(url).isEmpty())
    }

    @Test fun knownLoginRedirectMeansExpirationForBothProviders() = runBlocking {
        for (redirect in listOf("https://logowanie.edu.lublin.eu/LoginPage.aspx", "https://eduvulcan.pl/logowanie")) {
            assertEquals(SessionCheck.EXPIRED, manager(redirect = redirect, contentType = "text/html").checkSession())
        }
    }

    @Test fun malformedJsonIsNotExpiration() = runBlocking {
        assertEquals(SessionCheck.INVALID_RESPONSE, manager(body = "invalid-json").checkSession())
        assertFalse(manager(body = "invalid-json").validatePersistedSession())
        assertEquals(1, jar.loadForRequest(url).size)
    }

    @Test fun forbiddenResponseDoesNotTriggerAssumedExpiration() = runBlocking {
        assertEquals(SessionCheck.INVALID_RESPONSE, manager(code = 403).checkSession())
        assertEquals(1, jar.loadForRequest(url).size)
    }

    private fun manager(
        code: Int = 200,
        body: String = "{\"uczniowie\":[]}",
        transportFailure: Boolean = false,
        redirect: String? = null,
        contentType: String = "application/json",
        requests: MutableList<okhttp3.Request>? = null,
    ): UonetSessionManager {
        val current = CurrentAuthenticationSession(jar)
        val client = OkHttpClient.Builder().cookieJar(current).addInterceptor { chain ->
            requests?.add(chain.request())
            if (transportFailure) throw IOException("test transport failure")
            val request = if (redirect == null) chain.request() else chain.request().newBuilder().url(redirect).build()
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("test")
                .header("Content-Type", contentType).body(body.toResponseBody()).build()
        }.build()
        val parser = UonetHtmlFormParser()
        return UonetSessionManager(client, jar, current, parser, WsFederationNavigator(client, parser), MessagesSessionStore(), Json)
    }
}
