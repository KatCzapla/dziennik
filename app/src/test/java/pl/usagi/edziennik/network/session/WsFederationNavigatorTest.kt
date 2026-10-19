package pl.usagi.edziennik.network.session

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.usagi.edziennik.network.html.UonetHtmlFormParser

class WsFederationNavigatorTest {
    @Test
    fun followsMoreThanOneFederationStepAndStopsAtTargetHost() {
        val client = scriptedClient(
            "https://login.test/start" to htmlResponse(
                """
                    <form action="https://federation.test/step">
                      <input type="hidden" name="wa" value="wsignin1.0" />
                      <input type="hidden" name="wctx" value="context" />
                    </form>
                """.trimIndent(),
            ),
            "https://federation.test/step" to htmlResponse(
                """
                    <form action="https://messages.test/lublin/App/odebrane">
                      <input type="hidden" name="wresult" value="token" />
                    </form>
                """.trimIndent(),
            ),
            "https://messages.test/lublin/App/odebrane" to htmlResponse("<html>done</html>"),
        )
        val navigator = WsFederationNavigator(client, UonetHtmlFormParser())
        val initialResponse = client.get("https://login.test/start")

        navigator.follow(initialResponse, "messages.test").use { finalResponse ->
            assertEquals("messages.test", finalResponse.request.url.host)
            assertEquals("https://messages.test/lublin/App/odebrane", finalResponse.request.url.toString())
        }
    }

    @Test
    fun preservesHiddenInputsWhenPostingAutoPostForm() {
        val postedBodies = mutableListOf<String>()
        val client = scriptedClient(
            "https://login.test/start" to htmlResponse(
                """
                    <form action="https://messages.test/lublin/App/odebrane">
                      <input type="hidden" name="wa" value="wsignin1.0" />
                      <input type="hidden" name="wctx" value="ctx-value" />
                      <input type="hidden" name="wresult" value="result-value" />
                    </form>
                """.trimIndent(),
            ),
            "https://messages.test/lublin/App/odebrane" to htmlResponse("<html>done</html>"),
            postedBodies = postedBodies,
        )
        val navigator = WsFederationNavigator(client, UonetHtmlFormParser())

        navigator.follow(client.get("https://login.test/start"), "messages.test").close()

        assertTrue(postedBodies.any { it.contains("wa=wsignin1.0") })
        assertTrue(postedBodies.any { it.contains("wctx=ctx-value") })
        assertTrue(postedBodies.any { it.contains("wresult=result-value") })
    }

    @Test
    fun resolvesRelativeFormActionAgainstCurrentUrl() {
        val client = scriptedClient(
            "https://federation.test/sso/start" to htmlResponse(
                """
                    <form action="../reply">
                      <input type="hidden" name="wa" value="wsignin1.0" />
                    </form>
                """.trimIndent(),
            ),
            "https://federation.test/reply" to htmlResponse(
                """
                    <form action="https://messages.test/lublin/App/odebrane">
                      <input type="hidden" name="wresult" value="token" />
                    </form>
                """.trimIndent(),
            ),
            "https://messages.test/lublin/App/odebrane" to htmlResponse("<html>done</html>"),
        )
        val navigator = WsFederationNavigator(client, UonetHtmlFormParser())

        navigator.follow(client.get("https://federation.test/sso/start"), "messages.test").use { finalResponse ->
            assertEquals("messages.test", finalResponse.request.url.host)
        }
    }

    @Test
    fun checksTargetAsHostnameNotQuerySubstring() {
        val client = scriptedClient(
            "https://login.test/start?next=https://messages.test/lublin/App/odebrane" to htmlResponse("<html></html>"),
        )
        val navigator = WsFederationNavigator(client, UonetHtmlFormParser())

        assertThrows(AuthenticationException.WsFederationFormNotFound::class.java) {
            navigator.follow(
                client.get("https://login.test/start?next=https://messages.test/lublin/App/odebrane"),
                "messages.test",
            )
        }
    }

    @Test
    fun missingAutoPostFormGivesControlledError() {
        val client = scriptedClient(
            "https://login.test/start" to htmlResponse("<form action='/ordinary'></form>"),
        )
        val navigator = WsFederationNavigator(client, UonetHtmlFormParser())

        assertThrows(AuthenticationException.WsFederationFormNotFound::class.java) {
            navigator.follow(client.get("https://login.test/start"), "messages.test")
        }
    }

    @Test
    fun cyclicFormGivesControlledError() {
        val client = scriptedClient(
            "https://login.test/start" to htmlResponse(
                """
                    <form action="https://login.test/start">
                      <input type="hidden" name="wa" value="wsignin1.0" />
                    </form>
                """.trimIndent(),
            ),
        )
        val navigator = WsFederationNavigator(client, UonetHtmlFormParser())

        assertThrows(AuthenticationException.WsFederationCycleDetected::class.java) {
            navigator.follow(client.get("https://login.test/start"), "messages.test", maxSteps = 2)
        }
    }

    private fun OkHttpClient.get(url: String): Response =
        newCall(Request.Builder().url(url).get().build()).execute()

    private fun scriptedClient(
        vararg responses: Pair<String, ScriptedResponse>,
        postedBodies: MutableList<String> = mutableListOf(),
    ): OkHttpClient {
        val responseMap = responses.toMap()
        return OkHttpClient.Builder()
            .addInterceptor(ScriptedInterceptor(responseMap, postedBodies))
            .build()
    }

    private class ScriptedInterceptor(
        private val responses: Map<String, ScriptedResponse>,
        private val postedBodies: MutableList<String>,
    ) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            request.body?.let { body ->
                val buffer = Buffer()
                body.writeTo(buffer)
                postedBodies += buffer.readUtf8()
            }
            val response = responses.getValue(request.url.toString())
            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(response.code)
                .message("OK")
                .header("Content-Type", response.contentType)
                .body(response.body.toResponseBody(response.contentType.toMediaType()))
                .build()
        }
    }

    private data class ScriptedResponse(
        val body: String,
        val code: Int = 200,
        val contentType: String = "text/html",
    )

    private fun htmlResponse(body: String): ScriptedResponse = ScriptedResponse(body)
}
