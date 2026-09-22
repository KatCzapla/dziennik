package pl.usagi.edziennik.network.messages

import javax.inject.Inject

class MessagesBootstrapParser @Inject constructor() {
    fun parse(
        html: String,
        appUrl: okhttp3.HttpUrl,
    ): MessagesSessionContext {
        val appGuid = extractVParam(html, "appGuid")
        val token = extractVParam(html, "antiForgeryToken")
        MessagesDiagnostics.debug("Messages bootstrap parsed: appGuid=true, antiForgeryToken=true")
        return MessagesSessionContext(
            appUrl = appUrl,
            apiBaseUrl = appUrl.toMessagesApiBaseUrl(),
            appGuid = appGuid,
            requestVerificationToken = token,
        )
    }

    private fun extractVParam(
        html: String,
        name: String,
    ): String {
        val pattern = Regex("""$name\s*:\s*['"]([^'"]+)['"]""")
        return pattern.find(html)?.groupValues?.get(1)
            ?: throw MessagesSessionException.MissingBootstrapData(name)
    }
}
