package pl.usagi.edziennik.network.eduvulcan

import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class EduVulcanAuthenticationTest {
    @Test
    fun authenticatedClientUsesPersistentCookieJar() = runTest {
        val persistentCookies = RecordingCookieJar()
        val client = scriptedClient(mutableListOf())

        val result = EduVulcanLogin(client, persistentCookies = persistentCookies)
            .authenticate("alice", "secret") as EduVulcanLoginResult.Authenticated

        assertSame(persistentCookies, result.client.cookieJar)
    }

    @Test
    fun authenticatedCookiesAreCopiedToPersistentJar() {
        val persistentCookies = RecordingCookieJar()
        val cookie = Cookie.Builder()
            .name("session")
            .value("opaque")
            .hostOnlyDomain("uczen.edu.lublin.eu")
            .build()

        copyAuthenticatedCookies(listOf(cookie), persistentCookies)

        assertEquals("session", persistentCookies.cookies.single().name)
        assertEquals("uczen.edu.lublin.eu", persistentCookies.cookies.single().domain)
    }

    @Test
    fun oneJournalCompletesFederationAndValidatesContext() = runTest {
        val requests = mutableListOf<Request>()
        val client = scriptedClient(requests)

        val result = EduVulcanLogin(client).authenticate("alice", "secret")

        assertTrue(result is EduVulcanLoginResult.Authenticated)
        assertNotSame(client.cookieJar, (result as EduVulcanLoginResult.Authenticated).client.cookieJar)
        assertEquals(
            listOf("/logowanie", "/Account/QueryUserInfo", "/logowanie",
                "/dostep-do-dziennika/", "/dziennik", "/wsfed/relay", "/lublin/", "/lublin/api/Context"),
            requests.map { it.url.encodedPath },
        )
        assertEquals("opaque", requests[4].url.queryParameter("key"))
        assertEquals("wa=wsignin1.0&wctx=context", requests[5].formBody())
        assertEquals("wresult=federation-token", requests[6].formBody())
        assertEquals("application/json, text/plain, */*", requests[7].header("Accept"))
    }

    @Test
    fun noJournalIsTechnicalError() = runTest {
        val requests = mutableListOf<Request>()
        val client = scriptedClient(requests, journalsHtml = "<a href='/other'>Other</a>")

        assertEquals(EduVulcanLoginResult.TechnicalError, EduVulcanLogin(client).authenticate("alice", "secret"))
        assertEquals(4, requests.size)
    }

    @Test
    fun multipleJournalsAreTechnicalError() = runTest {
        val requests = mutableListOf<Request>()
        val client = scriptedClient(
            requests,
            journalsHtml = """
                <a href="/dziennik?key=first">First</a>
                <a href="/dziennik?key=second">Second</a>
            """.trimIndent(),
        )

        assertEquals(EduVulcanLoginResult.TechnicalError, EduVulcanLogin(client).authenticate("alice", "secret"))
        assertEquals(4, requests.size)
    }

    @Test
    fun missingFederationFormIsTechnicalError() = runTest {
        val requests = mutableListOf<Request>()
        val client = scriptedClient(requests, journalHtml = "<html>No federation form</html>")

        assertEquals(EduVulcanLoginResult.TechnicalError, EduVulcanLogin(client).authenticate("alice", "secret"))
        assertEquals(5, requests.size)
    }

    @Test
    fun contextWithoutStudentsIsTechnicalError() = runTest {
        val requests = mutableListOf<Request>()
        val client = scriptedClient(requests, contextBody = """{"other":[]}""")

        assertEquals(EduVulcanLoginResult.TechnicalError, EduVulcanLogin(client).authenticate("alice", "secret"))
        assertEquals("/lublin/api/Context", requests.last().url.encodedPath)
    }

    private fun scriptedClient(
        requests: MutableList<Request>,
        journalsHtml: String = "<a href='/dziennik?key=opaque'>Journal</a>",
        journalHtml: String = """
            <form action="/wsfed/relay">
                <input type="hidden" name="wa" value="wsignin1.0">
                <input type="hidden" name="wctx" value="context">
            </form>
        """.trimIndent(),
        contextBody: String = """{"uczniowie":[]}""",
    ): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(Interceptor { chain ->
            val request = chain.request()
            requests += request
            val response = when (request.url.encodedPath to request.method) {
                "/logowanie" to "GET" -> scriptedResponse(200, LOGIN_PAGE)
                "/Account/QueryUserInfo" to "POST" -> scriptedResponse(200, """{"success":true}""", "application/json")
                "/logowanie" to "POST" -> scriptedResponse(302, "")
                "/dostep-do-dziennika/" to "GET" -> scriptedResponse(200, journalsHtml)
                "/dziennik" to "GET" -> scriptedResponse(200, journalHtml)
                "/wsfed/relay" to "POST" -> scriptedResponse(
                    200,
                    """
                        <form action="https://uczen.edu.lublin.eu/lublin/">
                            <input type="hidden" name="wresult" value="federation-token">
                        </form>
                    """.trimIndent(),
                )
                "/lublin/" to "POST" -> scriptedResponse(200, "<html>Done</html>")
                "/lublin/api/Context" to "GET" -> scriptedResponse(200, contextBody, "application/json")
                else -> error("Unexpected test request")
            }
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(response.code)
                .message("scripted")
                .header("Content-Type", response.contentType)
                .body(response.body.toResponseBody(response.contentType.toMediaType()))
                .build()
        })
        .build()

    private class RecordingCookieJar : CookieJar {
        val cookies = mutableListOf<Cookie>()

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            this.cookies += cookies
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> = cookies.filter { it.matches(url) }
    }

    private fun scriptedResponse(code: Int, body: String, contentType: String = "text/html") =
        ScriptedResponse(code, body, contentType)

    private data class ScriptedResponse(val code: Int, val body: String, val contentType: String)

    private fun Request.formBody(): String {
        val buffer = Buffer()
        requireNotNull(body).writeTo(buffer)
        return buffer.readUtf8()
    }

    private companion object {
        val LOGIN_PAGE = """
            <input name="__RequestVerificationToken" value="csrf-value">
            <div class="captcha-wrapper" data-challenge="test" data-difficulty="268435456" data-rounds="2"></div>
        """.trimIndent()
    }
}
