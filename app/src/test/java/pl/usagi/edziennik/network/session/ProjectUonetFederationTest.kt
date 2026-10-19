package pl.usagi.edziennik.network.session

import android.app.Application
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.Cookie
import okhttp3.FormBody
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import pl.usagi.edziennik.network.NetworkModule
import pl.usagi.edziennik.network.cookie.PersistentCookieJar
import pl.usagi.edziennik.network.html.UonetHtmlFormParser
import pl.usagi.edziennik.network.messages.MessagesSessionStore

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class ProjectUonetFederationTest {
    private val requests = mutableListOf<Request>()
    private val cookies = mutableListOf<List<String>>()
    private val jar = PersistentCookieJar(RuntimeEnvironment.getApplication(), Json)
    private val current = CurrentAuthenticationSession(jar)

    @Test fun federationBeforePasswordContinuesThroughPasswordAndPostLoginFederation() = runBlocking {
        assertTrue(manager().login("fixture-user", "fixture-password").isSuccess)
        assertEquals(listOf("/lublin/", "/Federation", "/Account/Login", "/lublin/", "/lublin/api/Context"), requests.map { it.url.encodedPath })
        assertEquals(listOf("GET", "POST", "POST", "POST", "GET"), requests.map { it.method })
        val fields = fields(requests[1])
        assertEquals(mapOf("wa" to "wsignin1.0", "wresult" to "fixture-token", "wctx" to "fixture-context", "wtrealm" to "fixture-realm"), fields)
        assertEquals("fixture-user", fields(requests[2])["LoginName"])
        assertEquals("fixture-password", fields(requests[2])["Password"])
        assertEquals("fixture-result", fields(requests[3])["wresult"])
    }

    @Test fun standardPasswordFormStillWorks() = runBlocking {
        assertTrue(manager(start = fixture("password")).login("fixture-user", "fixture-password").isSuccess)
        assertEquals(4, requests.size)
        assertEquals("/Account/Login", requests[1].url.encodedPath)
    }

    @Test fun postPasswordFederationStillPreservesHiddenFields() = runBlocking {
        assertTrue(manager(start = fixture("password")).login("fixture-user", "fixture-password").isSuccess)
        assertEquals(mapOf("wa" to "wsignin1.0", "wresult" to "fixture-result"), fields(requests[2]))
    }

    @Test fun missingOrUnknownFormNeverCausesAnAutomaticPost() = runBlocking {
        for (html in listOf("<html></html>", "<form method='post' action='/unknown'><input type='hidden' name='unknown' value='fixture-value'></form>")) {
            requests.clear()
            assertTrue(manager(start = html).login("fixture-user", "fixture-password").exceptionOrNull() is AuthenticationException.LoginFormNotFound)
            assertEquals(listOf("GET"), requests.map { it.method })
        }
    }

    @Test fun unsafeActionsNeverReceiveFederationFields() = runBlocking {
        for (action in listOf(
            "https://attacker.invalid/", "https://logowanie.edu.lublin.eu.attacker.invalid/",
            "http://logowanie.edu.lublin.eu/Federation", "https://logowanie.edu.lublin.eu:444/Federation",
            "https://name:password@logowanie.edu.lublin.eu/Federation", "javascript:submit()",
        )) {
            requests.clear()
            val html = fixture("before").replace("https://logowanie.edu.lublin.eu/Federation", action)
            assertTrue(manager(start = html).login("fixture-user", "fixture-password").isFailure)
            assertEquals(listOf("GET"), requests.map { it.method })
        }
    }

    @Test fun untrustedPostPasswordActionIsAlsoBlocked() = runBlocking {
        val html = fixture("after").replace("https://projekt-uczen.edu.lublin.eu/lublin/", "https://attacker.invalid/")
        assertTrue(manager(after = html).login("fixture-user", "fixture-password").exceptionOrNull() is AuthenticationException.UnsafeFederationDestination)
        assertEquals(3, requests.size)
        assertFalse(requests.any { it.url.host == "attacker.invalid" })
    }

    @Test fun unsafePasswordFormActionIsNotSent() = runBlocking {
        val html = fixture("password").replace("https://logowanie.edu.lublin.eu/Account/Login", "https://attacker.invalid/")
        assertTrue(manager(start = html).login("fixture-user", "fixture-password").exceptionOrNull() is AuthenticationException.UnsafeFederationDestination)
        assertEquals(1, requests.size)
    }

    @Test fun confirmedCufsHostIsAcceptedAsIntermediateFederationStepAfterPassword() = runBlocking {
        val cufsForm = fixture("after").replace("https://projekt-uczen.edu.lublin.eu/lublin/", "https://cufs.edu.lublin.eu/CufsFederation")
        assertTrue(manager(start = fixture("password"), after = cufsForm).login("fixture-user", "fixture-password").isSuccess)
        assertEquals(listOf(
            "projekt-uczen.edu.lublin.eu", "logowanie.edu.lublin.eu", "cufs.edu.lublin.eu",
            "projekt-uczen.edu.lublin.eu", "projekt-uczen.edu.lublin.eu",
        ), requests.map { it.url.host })
        assertEquals("POST", requests[2].method)
        assertEquals(mapOf("wa" to "wsignin1.0", "wresult" to "fixture-result"), fields(requests[2]))
        assertEquals("/lublin/api/Context", requests.last().url.encodedPath)
    }

    @Test fun cufsAllowanceDoesNotTrustUnknownHostsOrLookalikeDomains() = runBlocking {
        for (host in listOf("unknown.edu.lublin.eu", "cufs.edu.lublin.eu.attacker.invalid", "other.cufs.edu.lublin.eu")) {
            requests.clear()
            val html = fixture("after").replace("https://projekt-uczen.edu.lublin.eu/lublin/", "https://$host/CufsFederation")
            assertTrue(manager(start = fixture("password"), after = html).login("fixture-user", "fixture-password")
                .exceptionOrNull() is AuthenticationException.UnsafeFederationDestination)
            assertEquals(2, requests.size)
            assertFalse(requests.any { it.url.host == host })
        }
    }

    @Test fun confirmedCufsHostStillRequiresHttpsDefaultPortAndNoUrlCredentials() = runBlocking {
        for (action in listOf(
            "http://cufs.edu.lublin.eu/CufsFederation", "https://cufs.edu.lublin.eu:444/CufsFederation",
            "https://name:password@cufs.edu.lublin.eu/CufsFederation",
        )) {
            requests.clear()
            val html = fixture("after").replace("https://projekt-uczen.edu.lublin.eu/lublin/", action)
            assertTrue(manager(start = fixture("password"), after = html).login("fixture-user", "fixture-password")
                .exceptionOrNull() is AuthenticationException.UnsafeFederationDestination)
            assertEquals(2, requests.size)
        }
    }

    @Test fun cyclicPrePasswordFederationStopsBeforeSecondIdenticalPost() = runBlocking {
        val html = fixture("before").replace("https://logowanie.edu.lublin.eu/Federation", "https://projekt-uczen.edu.lublin.eu/lublin/")
        assertTrue(manager(start = html, repeatStart = true).login("fixture-user", "fixture-password").exceptionOrNull() is AuthenticationException.WsFederationCycleDetected)
        assertEquals(listOf("GET", "POST"), requests.map { it.method })
    }

    @Test fun changingFederationFormsCannotExceedStepLimit() = runBlocking {
        val html = fixture("before").replace("https://logowanie.edu.lublin.eu/Federation", "https://projekt-uczen.edu.lublin.eu/lublin/")
        assertTrue(manager(start = html, repeatStart = true, changingToken = true).login("fixture-user", "fixture-password").exceptionOrNull() is AuthenticationException.WsFederationStepLimitExceeded)
        assertEquals(10, requests.count { it.method == "POST" })
    }

    @Test fun prePasswordFormMustBeAnUnambiguousHiddenFederationEnvelope() = runBlocking {
        for (html in listOf(
            fixture("before").replace("method=\"post\"", "method=\"get\""),
            fixture("before").replace("method=\"post\"", ""),
            fixture("before").replace("</form>", "<input name='editable'></form>"),
            fixture("before") + fixture("before"),
        )) {
            requests.clear()
            assertTrue(manager(start = html).login("fixture-user", "fixture-password").isFailure)
            assertEquals(1, requests.size)
        }
    }

    @Test fun recognizedSignInRequestRespectsGetMethodWithoutSendingResultToken() = runBlocking {
        val html = fixture("before").replace("method=\"post\"", "method=\"get\"")
            .replace("<input type=\"hidden\" name=\"wresult\" value=\"fixture-token\">", "")
        assertTrue(manager(start = html).login("fixture-user", "fixture-password").isSuccess)
        val request = requests[1]
        assertEquals("GET", request.method)
        assertEquals("wsignin1.0", request.url.queryParameter("wa"))
        assertEquals("fixture-realm", request.url.queryParameter("wtrealm"))
        assertNull(request.url.queryParameter("wresult"))
    }

    @Test fun sessionCookiesAreSharedAndPersistedAcrossEveryStage() = runBlocking {
        assertTrue(manager().login("fixture-user", "fixture-password").isSuccess)
        assertEquals(emptyList<String>(), cookies.first())
        assertTrue(cookies.drop(1).all { it == listOf("fixture-cookie") })
        val restored = PersistentCookieJar(RuntimeEnvironment.getApplication(), Json)
        assertEquals("fixture-cookie", restored.loadForRequest(requests.last().url).single().name)
    }

    @Test fun federationDiagnosticsExposeOnlySafeProtocolMetadata() = runBlocking {
        ShadowLog.clear()
        assertTrue(manager().login("fixture-user", "fixture-password").isSuccess)
        val logs = ShadowLog.getLogsForTag("UonetAuth").joinToString { it.msg }
        assertTrue(logs.contains("kind=WS_FEDERATION method=POST target=PROJECT_LOGIN"))
        assertTrue(logs.contains("wa=true wresult=true wctx=true wtrealm=true autoSubmitMarker=true"))
        assertFalse(logs.contains("fixture-"))
        assertFalse(logs.contains("https://"))
    }

    private fun manager(
        start: String = fixture("before"),
        after: String = fixture("after"),
        repeatStart: Boolean = false,
        changingToken: Boolean = false,
    ): UonetSessionManager {
        // This terminal application interceptor replaces transport; no test request reaches a network.
        val client = NetworkModule.provideOkHttpClient(current).newBuilder().addInterceptor { chain ->
            val request = chain.request()
            requests += request
            cookies += current.loadForRequest(request.url).map { it.name }
            current.saveFromResponse(request.url, listOf(Cookie.Builder().name("fixture-cookie").value("fixture-value")
                .domain("edu.lublin.eu").path("/").build()))
            val context = request.url.encodedPath == "/lublin/api/Context"
            val html = when (request.url.encodedPath to request.method) {
                "/lublin/" to "GET" -> start
                "/Federation" to "POST" -> fixture("password")
                "/Federation" to "GET" -> fixture("password")
                "/Account/Login" to "POST" -> after
                "/CufsFederation" to "POST" -> fixture("after")
                "/lublin/" to "POST" -> if (repeatStart) start else "<html>done</html>"
                "/lublin/api/Context" to "GET" -> "{\"uczniowie\":[]}"
                else -> error("Unscripted test request")
            }.let { if (changingToken) it.replace("fixture-token", "fixture-${requests.size}") else it }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("test")
                .header("Content-Type", if (context) "application/json" else "text/html")
                .body(html.toResponseBody()).build()
        }.build()
        val parser = UonetHtmlFormParser()
        return UonetSessionManager(client, jar, current, parser, WsFederationNavigator(client, parser), MessagesSessionStore(), Json)
    }

    private fun fields(request: Request): Map<String, String> = (request.body as FormBody).let { body ->
        (0 until body.size).associate { body.name(it) to body.value(it) }
    }

    private fun fixture(name: String): String =
        checkNotNull(javaClass.getResource("/html/project-auth-$name.html")).readText()
}
