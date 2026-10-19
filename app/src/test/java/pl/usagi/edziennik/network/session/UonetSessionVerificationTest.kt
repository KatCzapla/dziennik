package pl.usagi.edziennik.network.session

import android.app.Application
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import pl.usagi.edziennik.network.NetworkModule
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
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress

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

    @Test fun invalidResponsesExposeSafeReasonsWithoutResponseData() = runBlocking {
        data class Case(val code: Int, val body: String, val type: String?, val reason: String)
        val cases = listOf(
            Case(403, "private-body", "application/json", "UNEXPECTED_HTTP_STATUS"),
            Case(204, "", "application/json", "UNEXPECTED_HTTP_STATUS"),
            Case(200, "<html>private-body</html>", "text/html", "UNEXPECTED_CONTENT_TYPE"),
            Case(200, "{\"uczniowie\":[]}", null, "MISSING_CONTENT_TYPE"),
            Case(200, "private-invalid-json", "application/json", "INVALID_JSON"),
            Case(200, "[]", "application/json", "INVALID_JSON_OBJECT"),
            Case(200, "null", "application/json", "INVALID_JSON_OBJECT"),
            Case(200, "{\"private-field\":\"private-value\"}", "application/json", "MISSING_STUDENTS_FIELD"),
        )
        for (case in cases) {
            ShadowLog.clear()
            val checked = manager(code = case.code, body = case.body, contentType = case.type,
                redirect = "https://private-host.example/unknown?token=private-token").checkSession()
            assertEquals(case.reason, SessionCheck.INVALID_RESPONSE, checked)
            val logs = ShadowLog.getLogsForTag("UonetAuth").map { it.msg }.joinToString()
            assertTrue(logs, logs.contains("reason=${case.reason}"))
            assertFalse(logs, logs.contains("private-"))
            assertEquals(1, jar.loadForRequest(url).size)
        }
    }

    @Test fun studentsFieldPresenceRatherThanItsContentsDeterminesValidResponse() = runBlocking {
        for (body in listOf("{\"uczniowie\":[]}", "{\"uczniowie\":null}")) {
            ShadowLog.clear()
            assertEquals(SessionCheck.VALID, manager(body = body).checkSession())
            assertTrue(ShadowLog.getLogsForTag("UonetAuth").any {
                it.msg.contains("reason=STUDENTS_FIELD_PRESENT")
            })
        }
    }

    @Test fun contextChangingFromJsonToDirectHtmlDoesNotClearCookies() = runBlocking {
        var html = false
        val client = OkHttpClient.Builder().cookieJar(jar).addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("test")
                .header("Content-Type", if (html) "text/html" else "application/json")
                .body((if (html) "<html>private-page</html>" else "{\"uczniowie\":[]}").toResponseBody()).build()
        }.build()
        val session = sessionWithClient(client)
        assertEquals(SessionCheck.VALID, session.checkSession())
        html = true
        ShadowLog.clear()
        assertEquals(SessionCheck.INVALID_RESPONSE, session.checkSession())
        assertFalse(session.validatePersistedSession())
        assertEquals(1, jar.loadForRequest(url).size)
        val logs = ShadowLog.getLogsForTag("UonetAuth").map { it.msg }.joinToString()
        assertTrue(logs.contains("redirects=0 page=CONTEXT"))
        assertFalse(logs.contains("SESSION_CLEAR"))
        assertFalse(logs.contains("private-"))
    }

    @Test fun intermediateFederationPageBeforePasswordFormIsCurrentlyRejectedWithoutPosting() = runBlocking {
        ShadowLog.clear()
        val requests = mutableListOf<okhttp3.Request>()
        val result = manager(contentType = "text/html", requests = requests, body = """
            <form action='/private-target'><input type='hidden' name='wresult' value='private-token'></form>
            <script>private-script</script>
        """.trimIndent()).login("private-user", "private-password")
        assertTrue(result.exceptionOrNull() is AuthenticationException.LoginFormNotFound)
        assertEquals(listOf("GET"), requests.map { it.method })
        val logs = ShadowLog.getLogsForTag("UonetAuth").map { it.msg }.joinToString()
        assertTrue(logs.contains("NO_PASSWORD_INPUT"))
        assertTrue(logs.contains("federation=true"))
        assertTrue(logs.contains("password=false"))
        assertFalse(logs.contains("private-"))
    }

    @Test fun formDiagnosticsDoNotConsumeBodyOrExposeFieldNamesValuesAndActions() {
        ShadowLog.clear()
        val html = """
            <form action='https://private-host.example/?token=private-token'>
              <input name='private-user' value='private-login'>
              <input type='password' name='private-password' value='private-password-value'>
              <input type='hidden' name='private-field' value='private-value'>
            </form>
        """.trimIndent()
        val response = Response.Builder().request(okhttp3.Request.Builder().url(url).build())
            .protocol(Protocol.HTTP_1_1).code(200).message("private-message")
            .header("Content-Type", "text/html").body(html.toResponseBody()).build()
        response.use {
            it.httpDiagnostic("TEST_FORM", inspectForm = true)
            assertEquals(html, it.body.string())
        }
        val logs = ShadowLog.getLogsForTag("UonetAuth").map { it.msg }.joinToString()
        assertTrue(logs.contains("password=true passwordInForm=true login=true namedPassword=true"))
        assertFalse(logs.contains("private-"))
    }

    @Test fun completeHtmlDiagnosticsFindLateLoginFormWithoutConsumingBody() {
        ShadowLog.clear()
        val html = checkNotNull(javaClass.getResource("/html/context-login.html")).readText()
            .replace("<!-- PADDING -->", "<!--" + "x".repeat(90_000) + "-->")
        val response = Response.Builder().request(okhttp3.Request.Builder().url(url).build())
            .protocol(Protocol.HTTP_1_1).code(200).message("test")
            .header("Content-Type", "text/html").body(html.toResponseBody()).build()
        response.use {
            it.httpDiagnostic("CONTEXT_UNEXPECTED_CONTENT", inspectForm = true)
            assertEquals(html, it.body.string())
        }
        val logs = ShadowLog.getLogsForTag("UonetAuth").joinToString { it.msg }
        assertTrue(logs.contains("sample=FULL document=LOGIN_PAGE"))
        assertTrue(logs.contains("projectLoginForm=true"))
        assertFalse(logs.contains("private-"))
    }

    @Test fun htmlDocumentKindNeverChangesInvalidResponseClassificationOrClearsSession() = runBlocking {
        for ((fixture, kind) in listOf(
            "aspnet-error" to "ERROR_PAGE", "login" to "LOGIN_PAGE",
            "federation" to "INTERMEDIATE_PAGE", "unknown" to "UNKNOWN",
        )) {
            ShadowLog.clear()
            val html = checkNotNull(javaClass.getResource("/html/context-$fixture.html")).readText()
            assertEquals(SessionCheck.INVALID_RESPONSE, manager(contentType = "text/html", body = html).checkSession())
            val logs = ShadowLog.getLogsForTag("UonetAuth").joinToString { it.msg }
            assertTrue(logs.contains("document=$kind"))
            assertTrue(logs.contains("reason=UNEXPECTED_CONTENT_TYPE"))
            assertFalse(logs.contains("SESSION_CLEAR"))
            assertFalse(logs.contains("private-"))
            assertEquals(1, jar.loadForRequest(url).size)
        }
    }

    @Test fun actualRedirectsRetainCookiesAndAreDistinctFromDirectContextHtml() = runBlocking {
        val paths = mutableListOf<String>()
        val cookieHeaders = mutableListOf<String?>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            paths += exchange.requestURI.path
            cookieHeaders += exchange.requestHeaders.getFirst("Cookie")
            val isContext = exchange.requestURI.path == "/lublin/api/Context"
            if (isContext) {
                exchange.responseHeaders.add("Set-Cookie", "fixture=private-cookie; Path=/; HttpOnly")
                exchange.responseHeaders.add("Location", "/unexpected")
            }
            val body = "<html>private-page</html>".toByteArray()
            exchange.responseHeaders.add("Content-Type", "text/html")
            exchange.sendResponseHeaders(if (isContext) 302 else 200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            ShadowLog.clear()
            val client = OkHttpClient.Builder().cookieJar(jar).addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().url(chain.request().url.newBuilder()
                    .scheme("http").host("127.0.0.1").port(server.address.port).build()).build())
            }.build()
            assertEquals(SessionCheck.INVALID_RESPONSE, sessionWithClient(client).checkSession())
            assertEquals(listOf("/lublin/api/Context", "/unexpected"), paths)
            assertTrue(cookieHeaders.last().orEmpty().contains("fixture=private-cookie"))
            val restoredJar = PersistentCookieJar(RuntimeEnvironment.getApplication(), Json)
            assertTrue(restoredJar.loadForRequest("http://127.0.0.1:${server.address.port}/".toHttpUrl())
                .any { it.name == "fixture" })
            val logs = ShadowLog.getLogsForTag("UonetAuth").map { it.msg }.joinToString()
            assertTrue(logs.contains("redirects=1 page=OTHER"))
            assertFalse(logs.contains("private-"))
        } finally {
            server.stop(0)
        }
    }

    @Test fun lateHttpSetCookieCannotOverwriteNewLoginOrRestoreLoggedOutSession() = runBlocking {
        for (logout in listOf(false, true)) {
            val started = CountDownLatch(1)
            val finish = CountDownLatch(1)
            val current = CurrentAuthenticationSession(jar, jar::clear)
            current.restore(AuthenticationProvider.PROJECT_UONET)
            val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            server.createContext("/") { exchange ->
                started.countDown()
                finish.await(10, TimeUnit.SECONDS)
                exchange.responseHeaders.add("Set-Cookie", "generation-test=old; Path=/; HttpOnly")
                exchange.sendResponseHeaders(200, 2)
                exchange.responseBody.use { it.write("{}".toByteArray()) }
            }
            server.start()
            val localUrl = "http://127.0.0.1:${server.address.port}/".toHttpUrl()
            try {
                val client = NetworkModule.provideOkHttpClient(current)
                val oldRequest = async(Dispatchers.IO) {
                    client.newCall(okhttp3.Request.Builder().url(localUrl).build()).execute().use { it.body.string() }
                }
                assertTrue(started.await(5, TimeUnit.SECONDS))
                if (logout) current.invalidateAuthentication()
                else current.withAuthentication {
                    current.beginLogin()
                    current.commitAuthenticationState {
                        jar.saveFromResponse(localUrl, listOf(Cookie.Builder().name("generation-test")
                            .value("new").hostOnlyDomain(localUrl.host).build()))
                    }
                    current.activate(AutoDetectLoginResult.Success(AuthenticationProvider.PROJECT_UONET))
                }
                finish.countDown()
                oldRequest.await()
                val persisted = PersistentCookieJar(RuntimeEnvironment.getApplication(), Json)
                    .loadForRequest(localUrl).filter { it.name == "generation-test" }
                if (logout) {
                    assertTrue(persisted.isEmpty())
                    assertNull(current.provider)
                } else assertEquals("new", persisted.single().value)
            } finally {
                finish.countDown()
                server.stop(0)
            }
        }
    }

    private fun sessionWithClient(client: OkHttpClient): UonetSessionManager {
        val parser = UonetHtmlFormParser()
        return UonetSessionManager(client, jar, CurrentAuthenticationSession(jar), parser,
            WsFederationNavigator(client, parser), MessagesSessionStore(), Json)
    }

    private fun manager(
        code: Int = 200,
        body: String = "{\"uczniowie\":[]}",
        transportFailure: Boolean = false,
        redirect: String? = null,
        contentType: String? = "application/json",
        requests: MutableList<okhttp3.Request>? = null,
    ): UonetSessionManager {
        val current = CurrentAuthenticationSession(jar)
        val client = OkHttpClient.Builder().cookieJar(current).addInterceptor { chain ->
            requests?.add(chain.request())
            if (transportFailure) throw IOException("test transport failure")
            val request = if (redirect == null) chain.request() else chain.request().newBuilder().url(redirect).build()
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("test")
                .apply { if (contentType != null) header("Content-Type", contentType) }
                .body(body.toResponseBody()).build()
        }.build()
        val parser = UonetHtmlFormParser()
        return UonetSessionManager(client, jar, current, parser, WsFederationNavigator(client, parser), MessagesSessionStore(), Json)
    }
}
