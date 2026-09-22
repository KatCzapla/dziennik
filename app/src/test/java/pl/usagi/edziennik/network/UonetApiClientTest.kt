package pl.usagi.edziennik.network

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.usagi.edziennik.dto.ContextDto
import java.io.IOException

class UonetApiClientTest {
    @Test
    fun getSendsAcceptHeaderAndDecodesJson() {
        val requests = mutableListOf<Request>()
        val client = UonetApiClient(
            scriptedClient(body = """{"uczniowie":[]}""", requests = requests),
            Json,
        )

        val context = client.get(URL, ContextDto.serializer())

        assertTrue(context.students.isEmpty())
        assertEquals("GET", requests.single().method)
        assertEquals("application/json, text/plain, */*", requests.single().header("Accept"))
        assertEquals(URL, requests.single().url)
    }

    @Test
    fun httpErrorPreservesStatus() {
        val client = UonetApiClient(scriptedClient(responseCode = 503), Json)

        val failure = assertThrows(UonetApiClient.Failure.Http::class.java) {
            client.get(URL, ContextDto.serializer())
        }

        assertEquals(503, failure.statusCode)
    }

    @Test
    fun networkErrorPreservesCause() {
        val cause = IOException("offline")
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor { throw cause }
            .build()
        val client = UonetApiClient(okHttpClient, Json)

        val failure = assertThrows(UonetApiClient.Failure.Network::class.java) {
            client.get(URL, ContextDto.serializer())
        }

        assertSame(cause, failure.networkCause)
    }

    @Test
    fun strictContentTypeRejectsNonJsonResponse() {
        val client = UonetApiClient(scriptedClient(contentType = "text/html"), Json)

        val failure = assertThrows(UonetApiClient.Failure.Unexpected::class.java) {
            client.get(URL, ContextDto.serializer())
        }

        assertEquals(null, failure.cause)
    }

    @Test
    fun lenientContentTypeStillDecodesJsonAndNotifiesObserver() {
        val client = UonetApiClient(
            scriptedClient(contentType = "text/plain", body = """{"uczniowie":[]}"""),
            Json,
        )
        var observedContentType: String? = null

        val context = client.get(
            URL,
            ContextDto.serializer(),
            requireJsonContentType = false,
        ) { response ->
            observedContentType = response.header("Content-Type")
        }

        assertTrue(context.students.isEmpty())
        assertEquals("text/plain", observedContentType)
    }

    @Test
    fun malformedJsonPreservesDeserializationCause() {
        val client = UonetApiClient(scriptedClient(body = "not-json"), Json)

        val failure = assertThrows(UonetApiClient.Failure.Unexpected::class.java) {
            client.get(URL, ContextDto.serializer())
        }

        assertTrue(failure.cause is SerializationException)
    }

    private fun scriptedClient(
        responseCode: Int = 200,
        contentType: String = "application/json",
        body: String = "{}",
        requests: MutableList<Request> = mutableListOf(),
    ): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request()
            requests += request
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(responseCode)
                .message("scripted")
                .header("Content-Type", contentType)
                .body(body.toResponseBody())
                .build()
        }
        .build()

    private companion object {
        val URL = "https://projekt-uczen.edu.lublin.eu/lublin/api/Context".toHttpUrl()
    }
}
