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
import pl.usagi.edziennik.network.html.UonetHtmlFormParser
import pl.usagi.edziennik.network.session.WsFederationNavigator

class UonetMessagesSsoClientTest {
    @Test
    fun bootstrapKeepsCanonicalInboxAppUrlForApiReferer() = runTest {
        val requestedAppUrl = "https://messages.test/lublin/App/odebrane".toHttpUrl()
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(
                        Request.Builder()
                            .url("https://messages.test/lublin/App/after-login")
                            .build(),
                    )
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .header("Content-Type", "text/html")
                    .body(
                        """
                            <script>
                              var VParam = {
                                appGuid: 'app-guid-value',
                                antiForgeryToken: 'verification-token-value'
                              };
                            </script>
                        """.trimIndent().toResponseBody("text/html".toMediaType()),
                    )
                    .build()
            }
            .build()
        val ssoClient = UonetMessagesSsoClient(
            okHttpClient = client,
            wsFederationNavigator = WsFederationNavigator(client, UonetHtmlFormParser()),
            bootstrapParser = MessagesBootstrapParser(),
        )

        val context = ssoClient.authenticate(requestedAppUrl)

        assertEquals("https://messages.test/lublin/App/odebrane", context.appUrl.toString())
        assertEquals("https://messages.test/lublin/api", context.apiBaseUrl.toString())
    }
}
