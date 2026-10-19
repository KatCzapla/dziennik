package pl.usagi.edziennik.network.session

import org.junit.Assert.*
import org.junit.Test

class HtmlDocumentDiagnosticTest {
    @Test fun aspNetErrorRequiresBothTitleAndHeading() {
        assertEquals(HtmlDocumentKind.ERROR_PAGE, inspectHtmlDocument(fixture("aspnet-error")).kind)
        assertEquals(HtmlDocumentKind.UNKNOWN,
            inspectHtmlDocument("<h1>Server Error in '/' Application.</h1>").kind)
    }

    @Test fun recognizesIisDetailedErrorStructure() {
        val result = inspectHtmlDocument(fixture("iis-error"))
        assertEquals(HtmlDocumentKind.ERROR_PAGE, result.kind)
        assertTrue(result.iisError)
    }

    @Test fun errorWordsOrClassesAloneRemainUnknown() {
        assertEquals(HtmlDocumentKind.UNKNOWN, inspectHtmlDocument("<title>Error</title><h1>Error</h1><div class='error-page'>500</div>").kind)
    }

    @Test fun recognizesLoginAndProjectElementsWithoutReportingValues() {
        val result = inspectHtmlDocument(fixture("login"))
        assertEquals(HtmlDocumentKind.LOGIN_PAGE, result.kind)
        assertTrue(result.projectLoginForm)
        assertTrue(result.projectReferences)
        assertTrue(result.passwordInForm)
        assertTrue(result.namedPassword)
    }

    @Test fun missingPasswordDoesNotProveLoginPage() {
        val html = fixture("login").replace("type=\"password\"", "type=\"hidden\"")
        val result = inspectHtmlDocument(html)
        assertEquals(HtmlDocumentKind.UNKNOWN, result.kind)
        assertFalse(result.password)
        assertTrue(result.projectReferences)
    }

    @Test fun passwordOutsideFormDoesNotProveLoginPage() {
        val result = inspectHtmlDocument("<input type='password'><form><input name='login'></form>")
        assertEquals(HtmlDocumentKind.UNKNOWN, result.kind)
        assertTrue(result.password)
        assertFalse(result.passwordInForm)
    }

    @Test fun recognizesFederationIntermediatePage() {
        val result = inspectHtmlDocument(fixture("federation"))
        assertEquals(HtmlDocumentKind.INTERMEDIATE_PAGE, result.kind)
        assertTrue(result.federation)
    }

    @Test fun recognizesExplicitMetaRedirect() {
        assertEquals(HtmlDocumentKind.INTERMEDIATE_PAGE, inspectHtmlDocument(fixture("refresh")).kind)
        assertEquals(HtmlDocumentKind.UNKNOWN, inspectHtmlDocument("<meta http-equiv='refresh' content='30'>").kind)
    }

    @Test fun scriptsAndProjectReferencesAloneDoNotProveIntermediatePage() {
        val result = inspectHtmlDocument(fixture("unknown"))
        assertEquals(HtmlDocumentKind.UNKNOWN, result.kind)
        assertTrue(result.projectReferences)
        assertEquals(1, result.scripts)
    }

    @Test fun conflictingRecognizedStructuresRemainUnknown() {
        val html = fixture("login").replace("</body>", "<form><input type='hidden' name='wresult' value='private-token'></form></body>")
        assertEquals(HtmlDocumentKind.UNKNOWN, inspectHtmlDocument(html).kind)
    }

    @Test fun analysesElementsBeyondOldPrefixForAllRecognizedKinds() {
        for (name in listOf("aspnet-error", "login", "federation")) {
            val html = fixture(name)
            val expanded = html.replace("<!-- PADDING -->", "<!--" + "x".repeat(90_000) + "-->")
            assertEquals(inspectHtmlDocument(html).kind, inspectHtmlDocument(expanded).kind)
        }
    }

    @Test fun safeSummaryContainsNoDocumentDataOrAttributeValues() {
        for (name in listOf("aspnet-error", "iis-error", "login", "federation", "refresh", "unknown")) {
            val summary = inspectHtmlDocument(fixture(name)).safeSummary()
            assertFalse(summary.contains("private-"))
            assertFalse(summary.contains("http://"))
            assertFalse(summary.contains("https://"))
            assertFalse(summary.contains("LoginName"))
            assertFalse(summary.contains("__VIEWSTATE"))
            assertFalse(summary.contains("<"))
            assertTrue(summary.startsWith("sample=FULL document="))
        }
    }

    @Test fun emptyAndUnrecognizedDocumentsRemainUnknown() {
        for (html in listOf("", "not html", "<html><body></body></html>")) {
            assertEquals(HtmlDocumentKind.UNKNOWN, inspectHtmlDocument(html).kind)
        }
    }

    @Test fun genericPasswordFormWithoutRecognizedLoginFieldRemainsUnknown() {
        val result = inspectHtmlDocument("<form><input name='private-name'><input type='password' name='private-password'></form>")
        assertEquals(HtmlDocumentKind.UNKNOWN, result.kind)
        assertTrue(result.login)
        assertTrue(result.password)
    }

    @Test fun errorMarkersInsideScriptAreNotDocumentStructure() {
        assertEquals(HtmlDocumentKind.UNKNOWN, inspectHtmlDocument("<script>const example = `<form><input name='LoginName'><input type='password' name='Password'></form>`;</script>").kind)
    }

    @Test fun recognizesAspNetRuntimeErrorVariant() {
        val html = fixture("aspnet-error").replace("<title>Server Error in '/lublin' Application.</title>", "<title>Runtime Error</title>")
        assertEquals(HtmlDocumentKind.ERROR_PAGE, inspectHtmlDocument(html).kind)
    }

    private fun fixture(name: String): String =
        checkNotNull(javaClass.getResource("/html/context-$name.html")).readText()
}
