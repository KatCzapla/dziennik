package pl.usagi.edziennik.network.html

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class UonetHtmlFormParserTest {
    private val parser = UonetHtmlFormParser()

    @Test fun missingPasswordIsReportedWithoutHtmlOrCredentials() {
        assertFailure("<form><input name='login'></form>", LoginFormFailure.NO_PASSWORD_INPUT)
    }

    @Test fun passwordOutsideFormIsReported() {
        assertFailure("<input type='password' name='password'>", LoginFormFailure.PASSWORD_OUTSIDE_FORM)
    }

    @Test fun missingNamedLoginFieldIsReported() {
        assertFailure("<form><input type='password' name='password'></form>", LoginFormFailure.NO_LOGIN_FIELD)
    }

    @Test fun missingPasswordNameIsReported() {
        assertFailure("<form><input type='text' name='login'><input type='password'></form>",
            LoginFormFailure.NO_NAMED_PASSWORD_FIELD)
    }

    @Test fun validFormDoesNotReportFailureAndPreservesAuthenticationFields() {
        var failure: LoginFormFailure? = null
        val form = parser.parseLoginForm("<form><input name='login'><input type='password' name='password'></form>",
            "https://example.test/login", "test-login", "test-password", { failure = it })
        assertNull(failure)
        assertEquals("test-login", form?.fields?.get("login"))
        assertEquals("test-password", form?.fields?.get("password"))
    }

    private fun assertFailure(html: String, expected: LoginFormFailure) {
        val reasons = mutableListOf<LoginFormFailure>()
        assertNull(parser.parseLoginForm(html, "https://example.test/login?private=not-logged",
            "test-login", "test-password", reasons::add))
        assertEquals(listOf(expected), reasons)
    }

    @Test
    fun parsesLoginFormUsingPasswordInputParentForm() {
        val html = """
            <html>
              <body>
                <form action="/LoginPage.aspx?ReturnUrl=%2F">
                  <input type="hidden" name="__VIEWSTATE" value="view-state" />
                  <input type="email" name="LoginName" />
                  <input type="password" name="Password" />
                </form>
              </body>
            </html>
        """.trimIndent()

        val form = parser.parseLoginForm(
            html = html,
            currentUrl = "https://logowanie.edu.lublin.eu/LoginPage.aspx",
            username = "user@example.com",
            password = "secret",
        )

        assertNotNull(form)
        assertEquals(
            "https://logowanie.edu.lublin.eu/LoginPage.aspx?ReturnUrl=%2F",
            form?.actionUrl,
        )
        assertEquals("view-state", form?.fields?.get("__VIEWSTATE"))
        assertEquals("user@example.com", form?.fields?.get("LoginName"))
        assertEquals("secret", form?.fields?.get("Password"))
    }

    @Test
    fun preservesAllHiddenInputsFromLoginForm() {
        val html = """
            <form action="submit">
              <input type="hidden" name="__VIEWSTATE" value="a" />
              <input type="hidden" name="__EVENTVALIDATION" value="b" />
              <input type="text" name="login" />
              <input type="password" name="pass" />
            </form>
        """.trimIndent()

        val form = parser.parseLoginForm(
            html = html,
            currentUrl = "https://logowanie.edu.lublin.eu/path/LoginPage.aspx",
            username = "login-value",
            password = "pass-value",
        )

        assertEquals("a", form?.fields?.get("__VIEWSTATE"))
        assertEquals("b", form?.fields?.get("__EVENTVALIDATION"))
        assertEquals("login-value", form?.fields?.get("login"))
        assertEquals("pass-value", form?.fields?.get("pass"))
    }

    @Test
    fun resolvesRelativeFormActionAgainstCurrentUrl() {
        val resolved = parser.resolveActionUrl(
            currentUrl = "https://logowanie.edu.lublin.eu/account/LoginPage.aspx",
            action = "../signin",
        )

        assertEquals("https://logowanie.edu.lublin.eu/signin", resolved)
    }

    @Test
    fun detectsAutoPostFormWithWresult() {
        val html = """
            <form action="/lublin/">
              <input type="hidden" name="wa" value="other" />
              <input type="hidden" name="wresult" value="payload" />
            </form>
        """.trimIndent()

        val form = parser.parseAutoPostForm(
            html = html,
            currentUrl = "https://adfs.example.test/federation",
        )

        assertNotNull(form)
        assertEquals("https://adfs.example.test/lublin/", form?.actionUrl)
        assertEquals("payload", form?.fields?.get("wresult"))
    }

    @Test
    fun detectsAutoPostFormWithWsSigninAction() {
        val html = """
            <form action="reply">
              <input type="hidden" name="wa" value="wsignin1.0" />
              <input type="hidden" name="wctx" value="context" />
            </form>
        """.trimIndent()

        val form = parser.parseAutoPostForm(
            html = html,
            currentUrl = "https://logowanie.edu.lublin.eu/wsfed/start",
        )

        assertNotNull(form)
        assertEquals("https://logowanie.edu.lublin.eu/wsfed/reply", form?.actionUrl)
        assertEquals("wsignin1.0", form?.fields?.get("wa"))
        assertEquals("context", form?.fields?.get("wctx"))
    }

    @Test
    fun ignoresFormsWithoutWsFederationHiddenFields() {
        val html = """
            <form action="/ordinary">
              <input type="hidden" name="wa" value="not-signin" />
            </form>
        """.trimIndent()

        val form = parser.parseAutoPostForm(
            html = html,
            currentUrl = "https://logowanie.edu.lublin.eu/wsfed/start",
        )

        assertNull(form)
    }
}
