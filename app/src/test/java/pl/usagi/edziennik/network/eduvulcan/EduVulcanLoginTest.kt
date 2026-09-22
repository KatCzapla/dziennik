package pl.usagi.edziennik.network.eduvulcan

import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Test

class EduVulcanLoginTest {
    @Test
    fun acceptedCredentialsFollowThePocRequestSequence() = runTest {
        val requests = mutableListOf<Request>()
        val client = scriptedClient(requests, loginCode = 302, loginBody = "")

        assertEquals(EduVulcanLoginResult.Accepted, EduVulcanLogin(client).checkCredentials("alice", "secret"))
        assertEquals(3, requests.size)
        assertEquals("GET", requests[0].method)
        assertEquals("https://eduvulcan.pl/logowanie", requests[0].url.toString())
        assertEquals("https://eduvulcan.pl/Account/QueryUserInfo", requests[1].url.toString())
        assertEquals("alias=alice&__RequestVerificationToken=csrf-value", requests[1].formBody())
        assertEquals("https://eduvulcan.pl/logowanie", requests[1].header("Referer"))
        assertEquals("https://eduvulcan.pl/logowanie", requests[2].url.toString())
        assertEquals("https://eduvulcan.pl/logowanie", requests[2].header("Referer"))
        assertEquals(
            "UserName=alice&Password=secret&captcha-response=25%3B16&__RequestVerificationToken=csrf-value",
            requests[2].formBody(),
        )
    }

    @Test
    fun explicitLoginRejectionMeansInvalidCredentials() = runTest {
        val client = scriptedClient(
            mutableListOf(),
            loginCode = 200,
            loginBody = "<p>Zła nazwa użytkownika lub hasło</p>",
        )

        assertEquals(
            EduVulcanLoginResult.InvalidCredentials,
            EduVulcanLogin(client).checkCredentials("alice", "bad-password"),
        )
    }

    @Test
    fun unexpectedLoginResponseIsTechnicalError() = runTest {
        val client = scriptedClient(mutableListOf(), loginCode = 200, loginBody = "<html>Other error</html>")

        assertEquals(
            EduVulcanLoginResult.TechnicalError,
            EduVulcanLogin(client).checkCredentials("alice", "secret"),
        )
    }

    @Test
    fun unsuccessfulQueryUserInfoStopsBeforePasswordPost() = runTest {
        val requests = mutableListOf<Request>()
        val client = scriptedClient(requests, queryBody = """{"success":false}""")

        assertEquals(
            EduVulcanLoginResult.TechnicalError,
            EduVulcanLogin(client).checkCredentials("alice", "secret"),
        )
        assertEquals(2, requests.size)
    }

    @Test
    fun malformedLoginPageIsTechnicalError() = runTest {
        val requests = mutableListOf<Request>()
        val client = scriptedClient(requests, loginPage = "<html></html>")

        assertEquals(
            EduVulcanLoginResult.TechnicalError,
            EduVulcanLogin(client).checkCredentials("alice", "secret"),
        )
        assertEquals(1, requests.size)
    }

    private fun scriptedClient(
        requests: MutableList<Request>,
        loginCode: Int = 302,
        loginBody: String = "",
        queryBody: String = """{"success":true,"data":{"ShowCaptcha":true}}""",
        loginPage: String = """
            <input name="__RequestVerificationToken" value="csrf-value">
            <div class="captcha-wrapper" data-challenge="test" data-difficulty="268435456" data-rounds="2"></div>
        """.trimIndent(),
    ): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(Interceptor { chain ->
            val request = chain.request()
            requests += request
            val index = requests.size
            val (code, body, contentType) = when (index) {
                1 -> Triple(200, loginPage, "text/html")
                2 -> Triple(200, queryBody, "application/json")
                else -> Triple(loginCode, loginBody, "text/html")
            }
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("scripted")
                .header("Content-Type", contentType)
                .body(body.toResponseBody(contentType.toMediaType()))
                .build()
        })
        .build()

    private fun Request.formBody(): String {
        val buffer = Buffer()
        requireNotNull(body).writeTo(buffer)
        return buffer.readUtf8()
    }
}
