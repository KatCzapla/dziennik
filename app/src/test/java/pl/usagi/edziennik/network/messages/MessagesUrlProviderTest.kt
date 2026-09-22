package pl.usagi.edziennik.network.messages

import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test

class MessagesUrlProviderTest {
    @Test
    fun providerReadsMessagesUrlFromMainAppVParam() = runTest {
        val requests = mutableListOf<Request>()
        val provider = UonetMessagesUrlProvider(
            mainAppClient = UonetMainAppMessagesUrlClient(
                okHttpClient = scriptedClient(
                    responseBody = """
                        <html>
                          <script>
                            var VParam = {
                              wiadomosciUrl: 'https://messages.test/lublin/App'
                            };
                          </script>
                        </html>
                    """.trimIndent(),
                    requests = requests,
                ),
            ),
        )

        val url = provider.getMessagesAppUrl()

        assertEquals("https://messages.test/lublin/App/odebrane", url.toString())
        assertEquals("https://projekt-uczen.edu.lublin.eu/lublin/App", requests.single().url.toString())
    }

    @Test
    fun providerReadsQuotedMessagesUrlFromMainAppVParam() = runTest {
        val provider = UonetMessagesUrlProvider(
            mainAppClient = UonetMainAppMessagesUrlClient(
                okHttpClient = scriptedClient(
                    responseBody = """
                        <script>
                          window.VParam = {
                            "wiadomosciUrl": "https:\/\/messages.test\/lublin\/App\/odebrane"
                          };
                        </script>
                    """.trimIndent(),
                ),
            ),
        )

        val url = provider.getMessagesAppUrl()

        assertEquals("https://messages.test/lublin/App/odebrane", url.toString())
    }

    @Test
    fun providerFallsBackToPythonPocMessagesUrlWhenMainAppVParamDoesNotContainMessagesUrl() = runTest {
        val provider = UonetMessagesUrlProvider(
            mainAppClient = UonetMainAppMessagesUrlClient(
                okHttpClient = scriptedClient(responseBody = "<html></html>"),
            ),
        )

        val url = provider.getMessagesAppUrl()

        assertEquals("https://projekt-wiadomosci.edu.lublin.eu/lublin/App/odebrane", url.toString())
    }

    @Test
    fun messagesUrlEndingAtEnvironmentRootStartsFromInboxAppPathLikePythonPoc() {
        val url = "https://messages.test/lublin/".toHttpUrl().toMessagesAppUrl()

        assertEquals("https://messages.test/lublin/App/odebrane", url.toString())
    }

    @Test
    fun messagesUrlEndingAtAppPathAddsInboxPath() {
        val url = "https://messages.test/lublin/App".toHttpUrl().toMessagesAppUrl()

        assertEquals("https://messages.test/lublin/App/odebrane", url.toString())
    }

    @Test
    fun messagesUrlAlreadyPointingAtInboxIsKept() {
        val url = "https://messages.test/lublin/App/odebrane".toHttpUrl().toMessagesAppUrl()

        assertEquals("https://messages.test/lublin/App/odebrane", url.toString())
    }

    private fun scriptedClient(
        responseCode: Int = 200,
        responseBody: String,
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
                    .header("Content-Type", "text/html")
                    .body(responseBody.toResponseBody("text/html".toMediaType()))
                    .build()
            }
            .build()
}
