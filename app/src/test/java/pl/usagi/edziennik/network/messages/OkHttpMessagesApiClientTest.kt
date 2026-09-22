package pl.usagi.edziennik.network.messages

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.usagi.edziennik.domain.MessageMailboxType

class OkHttpMessagesApiClientTest {
    @Test
    fun unreadCountsRequestUsesMessagesBaseUrlAndRequiredHeaders() = runTest {
        val requests = mutableListOf<Request>()
        val client = OkHttpMessagesApiClient(
            okHttpClient = scriptedClient(
                responseBody = """[{"globalKey":"mailbox-key","liczbaWiadomosci":3}]""",
                requests = requests,
            ),
            json = json,
        )

        val counts = client.getUnreadCounts(messagesContext())

        assertEquals(1, counts.size)
        assertEquals("mailbox-key", counts.single().globalKey)
        assertEquals(3, counts.single().count)

        val request = requests.single()
        assertEquals("https://messages.test/lublin/api/LiczbyNieodczytanych", request.url.toString())
        assertEquals("application/json, text/plain, */*", request.header("Accept"))
        assertEquals("https://messages.test/lublin/App/odebrane", request.header("Referer"))
        assertEquals("app-guid", request.header("X-V-AppGuid"))
        assertEquals("verification-token", request.header("X-V-RequestVerificationToken"))
        assertNull(request.header("Cookie"))
    }

    @Test
    fun receivedMessagesRequestUsesConfirmedPaginationParameters() = runTest {
        val requests = mutableListOf<Request>()
        val client = OkHttpMessagesApiClient(
            okHttpClient = scriptedClient(
                responseBody = """
                    [{
                      "apiGlobalKey":"api-key",
                      "korespondenci":"Anna Kowalska",
                      "korespondenciSzczegoly":null,
                      "temat":"Zebranie",
                      "data":"2026-09-14T18:32:00+02:00",
                      "skrzynka":"Rodzic",
                      "hasZalaczniki":false,
                      "przeczytana":false,
                      "nieprzeczytanePrzeczytanePrzez":null,
                      "wazna":false,
                      "uzytkownikRola":1,
                      "wycofana":false,
                      "odpowiedziana":false,
                      "przekazana":false,
                      "id":123
                    }]
                """.trimIndent(),
                requests = requests,
            ),
            json = json,
        )

        val messages = client.getReceivedMessages(
            context = messagesContext(),
            lastMessageId = 42,
            pageSize = 50,
        )

        assertEquals(123, messages.single().id)
        val url = requests.single().url
        assertEquals("https://messages.test/lublin/api/Odebrane", "${url.scheme}://${url.host}${url.encodedPath}")
        assertEquals("42", url.queryParameter("idLastWiadomosc"))
        assertEquals("50", url.queryParameter("pageSize"))
    }

    @Test
    fun mailboxMessagesRequestsUseConfirmedEndpointsAndPaginationParameters() = runTest {
        val cases = listOf(
            MessageMailboxType.Received to "Odebrane",
            MessageMailboxType.Sent to "Wyslane",
            MessageMailboxType.Deleted to "Usuniete",
            MessageMailboxType.Drafts to "Kopie",
        )

        cases.forEach { (mailboxType, endpoint) ->
            val requests = mutableListOf<Request>()
            val client = OkHttpMessagesApiClient(
                okHttpClient = scriptedClient(
                    responseBody = "[]",
                    requests = requests,
                ),
                json = json,
            )

            client.getMessages(
                context = messagesContext(),
                mailboxType = mailboxType,
                lastMessageId = 42,
                pageSize = 50,
            )

            val url = requests.single().url
            assertEquals("https://messages.test/lublin/api/$endpoint", "${url.scheme}://${url.host}${url.encodedPath}")
            assertEquals("42", url.queryParameter("idLastWiadomosc"))
            assertEquals("50", url.queryParameter("pageSize"))
        }
    }

    @Test
    fun mailboxesRequestUsesMessagesApi() = runTest {
        val requests = mutableListOf<Request>()
        val client = OkHttpMessagesApiClient(
            okHttpClient = scriptedClient(
                responseBody = """[{"globalKey":"mailbox-key","nazwa":"Rodzic","typUzytkownika":1}]""",
                requests = requests,
            ),
            json = json,
        )

        val mailboxes = client.getMailboxes(messagesContext())

        assertEquals("Rodzic", mailboxes.single().name)
        assertEquals("https://messages.test/lublin/api/Skrzynki", requests.single().url.toString())
    }

    @Test
    fun messageDetailsRequestUsesConfirmedEndpointAndApiGlobalKeyParameter() = runTest {
        val requests = mutableListOf<Request>()
        val client = OkHttpMessagesApiClient(
            okHttpClient = scriptedClient(
                responseBody = """
                    {
                      "data":"2026-09-14T18:32:00+02:00",
                      "apiGlobalKey":"api-key",
                      "nadawca":"Anna Kowalska",
                      "nadawcaTyp":1,
                      "odbiorcy":["Jan Nowak"],
                      "temat":"Zebranie",
                      "tresc":"Treść wiadomości",
                      "odczytana":false,
                      "zalaczniki":[{"url":"https://files.test/a.pdf","nazwaPliku":"a.pdf"}],
                      "nadawcaInfo":"",
                      "wycofana":false,
                      "dataWycofania":null,
                      "id":123
                    }
                """.trimIndent(),
                requests = requests,
            ),
            json = json,
        )

        val details = client.getMessageDetails(messagesContext(), apiGlobalKey = "api-key")

        assertEquals(123, details.id)
        assertEquals("Anna Kowalska", details.sender)
        assertEquals("https://messages.test/lublin/api/WiadomoscSzczegoly?apiGlobalKey=api-key", requests.single().url.toString())
        assertEquals("GET", requests.single().method)
        assertEquals("app-guid", requests.single().header("X-V-AppGuid"))
        assertEquals("verification-token", requests.single().header("X-V-RequestVerificationToken"))
        assertNull(requests.single().header("Cookie"))
    }

    @Test
    fun markMessageAsReadUsesPutWiadomoscSzczegolyWithApiGlobalKeyJsonBody() = runTest {
        val requests = mutableListOf<Request>()
        val client = OkHttpMessagesApiClient(
            okHttpClient = scriptedClient(
                responseBody = "",
                requests = requests,
            ),
            json = json,
        )

        client.markMessageAsRead(messagesContext(), apiGlobalKey = "api-key")

        val request = requests.single()
        assertEquals("https://messages.test/lublin/api/WiadomoscSzczegoly", request.url.toString())
        assertEquals("PUT", request.method)
        assertEquals("""{"apiGlobalKey":"api-key"}""", request.bodyString())
        assertEquals("application/json, text/plain, */*", request.header("Accept"))
        assertEquals("https://messages.test/lublin/App/odebrane", request.header("Referer"))
        assertEquals("app-guid", request.header("X-V-AppGuid"))
        assertEquals("verification-token", request.header("X-V-RequestVerificationToken"))
        assertNull(request.header("Cookie"))
    }

    @Test
    fun receivedMessagesRequestDoesNotBuildManualCookieHeader() = runTest {
        val requests = mutableListOf<Request>()
        val client = OkHttpMessagesApiClient(
            okHttpClient = scriptedClient(
                responseBody = """[]""",
                requests = requests,
            ),
            json = json,
        )

        client.getReceivedMessages(messagesContext(), lastMessageId = 0, pageSize = 50)

        assertNull(requests.single().header("Cookie"))
    }

    @Test
    fun httpErrorGivesControlledError() {
        val client = OkHttpMessagesApiClient(
            okHttpClient = scriptedClient(responseCode = 500),
            json = json,
        )

        assertThrows(MessagesSessionException.Http::class.java) {
            runTest { client.getUnreadCounts(messagesContext()) }
        }
    }

    @Test
    fun malformedResponseGivesControlledError() {
        val client = OkHttpMessagesApiClient(
            okHttpClient = scriptedClient(responseBody = """{"not":"a-list"}"""),
            json = json,
        )

        assertThrows(MessagesSessionException.UnexpectedResponse::class.java) {
            runTest { client.getUnreadCounts(messagesContext()) }
        }
    }

    @Test
    fun requestDoesNotContainUserCredentials() = runTest {
        val requests = mutableListOf<Request>()
        val client = OkHttpMessagesApiClient(
            okHttpClient = scriptedClient(
                responseBody = """[]""",
                requests = requests,
            ),
            json = json,
        )

        client.getUnreadCounts(messagesContext())

        val requestText = requests.single().toString() + requests.single().url
        assertTrue("login" !in requestText.lowercase())
        assertTrue("password" !in requestText.lowercase())
        assertTrue("haslo" !in requestText.lowercase())
    }

    private fun scriptedClient(
        responseCode: Int = 200,
        responseBody: String = """[]""",
        requests: MutableList<Request> = mutableListOf(),
    ): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                requests += request
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(responseCode)
                    .message("OK")
                    .header("Content-Type", "application/json")
                    .body(responseBody.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

    private fun Request.bodyString(): String {
        val buffer = Buffer()
        body?.writeTo(buffer)
        return buffer.readUtf8()
    }

    private fun messagesContext(): MessagesSessionContext {
        val appUrl = "https://messages.test/lublin/App/odebrane".toHttpUrl()
        return MessagesSessionContext(
            appUrl = appUrl,
            apiBaseUrl = appUrl.toMessagesApiBaseUrl(),
            appGuid = "app-guid",
            requestVerificationToken = "verification-token",
        )
    }

    private companion object {
        val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }
    }
}
