package pl.katcz.edziennik.network.html

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup

class UonetHtmlFormParser {
    fun parseLoginForm(
        html: String,
        currentUrl: String,
        username: String,
        password: String,
    ): HtmlForm? {
        val document = Jsoup.parse(html, currentUrl)
        val passwordInput = document.selectFirst("input[type=password]") ?: return null
        val form = passwordInput.closest("form") ?: return null

        val fields = linkedMapOf<String, String>()
        var usernameFieldName: String? = null
        var passwordFieldName: String? = null

        form.select("input[name]").forEach { input ->
            val name = input.attr("name")
            if (name.isBlank()) return@forEach

            when (input.attr("type").ifBlank { "text" }.lowercase()) {
                "hidden" -> fields[name] = input.attr("value")
                "text", "email" -> if (usernameFieldName == null) usernameFieldName = name
                "password" -> passwordFieldName = name
            }
        }

        val resolvedUsernameField = usernameFieldName ?: return null
        val resolvedPasswordField = passwordFieldName ?: return null

        fields[resolvedUsernameField] = username
        fields[resolvedPasswordField] = password

        return HtmlForm(
            actionUrl = resolveActionUrl(currentUrl, form.attr("action")),
            fields = fields,
        )
    }

    fun parseAutoPostForm(
        html: String,
        currentUrl: String,
    ): HtmlForm? {
        val document = Jsoup.parse(html, currentUrl)

        document.select("form").forEach { form ->
            val hiddenFields = linkedMapOf<String, String>()

            form.select("input[name]").forEach { input ->
                val name = input.attr("name")
                val type = input.attr("type").lowercase()

                if (name.isNotBlank() && type == "hidden") {
                    hiddenFields[name] = input.attr("value")
                }
            }

            if ("wresult" in hiddenFields || hiddenFields["wa"] == "wsignin1.0") {
                return HtmlForm(
                    actionUrl = resolveActionUrl(currentUrl, form.attr("action")),
                    fields = hiddenFields,
                )
            }
        }

        return null
    }

    fun resolveActionUrl(
        currentUrl: String,
        action: String?,
    ): String {
        val fallback = currentUrl.toHttpUrl()
        val resolved = action
            ?.takeUnless { it.isBlank() }
            ?.let { fallback.resolve(it) }

        return (resolved ?: fallback).toString()
    }
}
