package pl.usagi.edziennik.network.messages

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MessagesBootstrapParserTest {
    private val parser = MessagesBootstrapParser()

    @Test
    fun parsesAppGuidAndRequestVerificationTokenFromVParam() {
        val context = parser.parse(
            html = """
                <script>
                  var VParam = {
                    appGuid: 'app-guid-value',
                    antiForgeryToken: "verification-token-value"
                  };
                </script>
            """.trimIndent(),
            appUrl = "https://messages.test/lublin/App/odebrane".toHttpUrl(),
        )

        assertEquals("app-guid-value", context.appGuid)
        assertEquals("verification-token-value", context.requestVerificationToken)
        assertEquals("https://messages.test/lublin/api", context.apiBaseUrl.toString())
    }

    @Test
    fun missingBootstrapDataGivesControlledError() {
        assertThrows(MessagesSessionException.MissingBootstrapData::class.java) {
            parser.parse(
                html = "<html></html>",
                appUrl = "https://messages.test/lublin/App/odebrane".toHttpUrl(),
            )
        }
    }
}
