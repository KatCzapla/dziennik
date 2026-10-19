package pl.usagi.edziennik.network.session

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup

internal enum class HtmlDocumentKind { ERROR_PAGE, LOGIN_PAGE, INTERMEDIATE_PAGE, UNKNOWN }

// Only allowlisted flags and counts leave the parser; no document content is retained.
internal data class HtmlDocumentDiagnostic(
    val kind: HtmlDocumentKind,
    val doctype: Boolean,
    val title: Boolean,
    val bodyElements: Int,
    val forms: Int,
    val inputs: Int,
    val scripts: Int,
    val password: Boolean,
    val passwordInForm: Boolean,
    val login: Boolean,
    val namedPassword: Boolean,
    val federation: Boolean,
    val metaRefresh: Boolean,
    val redirectRefresh: Boolean,
    val aspNetError: Boolean,
    val iisError: Boolean,
    val projectLoginForm: Boolean,
    val projectReferences: Boolean,
) {
    fun safeSummary(): String =
        "sample=FULL document=$kind doctype=$doctype title=$title bodyElements=$bodyElements " +
            "forms=$forms inputs=$inputs scripts=$scripts form=${forms > 0} " +
            "password=$password passwordInForm=$passwordInForm login=$login namedPassword=$namedPassword " +
            "federation=$federation script=${scripts > 0} metaRefresh=$metaRefresh redirectRefresh=$redirectRefresh " +
            "aspNetError=$aspNetError iisError=$iisError projectLoginForm=$projectLoginForm " +
            "projectReferences=$projectReferences"
}

internal fun inspectHtmlDocument(html: String): HtmlDocumentDiagnostic {
    val document = Jsoup.parse(html)
    val forms = document.select("form")
    val passwordInputs = document.select("input[type=password]")
    val passwordForms = passwordInputs.mapNotNull { it.closest("form") }.distinct()
    fun hasLogin(form: org.jsoup.nodes.Element): Boolean = form.select("input[name]").any {
        it.attr("name").isNotBlank() && it.attr("type").ifBlank { "text" }.lowercase() in setOf("text", "email")
    }
    fun hasPassword(form: org.jsoup.nodes.Element): Boolean = form.select("input[type=password][name]")
        .any { it.attr("name").isNotBlank() }
    val loginForm = passwordForms.any { form ->
        hasPassword(form) && form.select("input[name]").any {
            it.attr("type").ifBlank { "text" }.lowercase() in setOf("text", "email") &&
                (it.attr("name").lowercase() in setOf("loginname", "username", "login", "email") ||
                    it.attr("autocomplete").equals("username", true))
        } && form.select("input[autocomplete=new-password]").isEmpty()
    }
    val federation = forms.any { form ->
        form.select("input[type=hidden][name]").any {
            it.attr("name") == "wresult" || (it.attr("name") == "wa" && it.attr("value") == "wsignin1.0")
        }
    }
    val refresh = document.select("meta[http-equiv]").filter { it.attr("http-equiv").equals("refresh", true) }
    val redirectRefresh = refresh.any { refreshRedirect.matches(it.attr("content")) }
    val headings = document.select("h1").map { it.text() }
    val aspNetError = headings.any { aspNetHeading.matches(it) } &&
        (aspNetHeading.matches(document.title()) ||
            (document.title().equals("Runtime Error", true) &&
                document.select("h2").any { it.text().equals("Runtime Error", true) }))
    val iisError = document.select("#content .content-container h3").any { iisHeading.matches(it.text()) } &&
        (iisTitle.matches(document.title()) ||
            document.select("#header h1").any { it.text().equals("Internet Information Services", true) })
    val base = "https://diagnostic.invalid/".toHttpUrl()
    val projectLoginForm = passwordForms.any { form ->
        val action = base.resolve(form.attr("action"))
        action?.encodedPath?.endsWith("/LoginPage.aspx", true) == true &&
            form.select("input[name=LoginName]").isNotEmpty() && hasPassword(form)
    }
    val projectReferences = document.select("a[href], form[action], script[src], link[href]").any {
        val reference = it.attr(when (it.tagName()) { "form" -> "action"; "script" -> "src"; else -> "href" })
        base.resolve(reference)?.host in setOf("projekt-uczen.edu.lublin.eu", "logowanie.edu.lublin.eu")
    }
    val candidates = buildList {
        if (aspNetError || iisError) add(HtmlDocumentKind.ERROR_PAGE)
        if (loginForm) add(HtmlDocumentKind.LOGIN_PAGE)
        if (federation || redirectRefresh) add(HtmlDocumentKind.INTERMEDIATE_PAGE)
    }
    return HtmlDocumentDiagnostic(
        kind = candidates.singleOrNull() ?: HtmlDocumentKind.UNKNOWN,
        doctype = document.documentType() != null,
        title = document.selectFirst("title") != null,
        bodyElements = document.body().getAllElements().size - 1,
        forms = forms.size,
        inputs = document.select("input").size,
        scripts = document.select("script").size,
        password = passwordInputs.isNotEmpty(),
        passwordInForm = passwordForms.isNotEmpty(),
        login = passwordForms.any(::hasLogin),
        namedPassword = passwordForms.any(::hasPassword),
        federation = federation,
        metaRefresh = refresh.isNotEmpty(),
        redirectRefresh = redirectRefresh,
        aspNetError = aspNetError,
        iisError = iisError,
        projectLoginForm = projectLoginForm,
        projectReferences = projectReferences,
    )
}

private val aspNetHeading = Regex("Server Error in '.+' Application\\.", RegexOption.IGNORE_CASE)
private val iisHeading = Regex("HTTP Error [45]\\d{2}(?:\\.\\d+)?(?:\\s.*)?", RegexOption.IGNORE_CASE)
private val iisTitle = Regex("IIS \\d+(?:\\.\\d+)? Detailed Error - [45]\\d{2}(?:\\.\\d+)?(?:\\s.*)?", RegexOption.IGNORE_CASE)
private val refreshRedirect = Regex("\\s*\\d+(?:\\.\\d+)?\\s*;\\s*url\\s*=\\s*\\S.*", RegexOption.IGNORE_CASE)
