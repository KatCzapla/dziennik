package pl.usagi.edziennik.network.session

import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import pl.usagi.edziennik.network.html.HtmlForm
import pl.usagi.edziennik.network.html.UonetHtmlFormParser
import java.io.IOException
import java.security.MessageDigest
import org.jsoup.Jsoup
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WsFederationNavigator @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val htmlFormParser: UonetHtmlFormParser,
) {
    fun follow(
        initialResponse: Response,
        targetHost: String,
        maxSteps: Int = DEFAULT_MAX_STEPS,
        allowedHosts: Set<String>? = null,
    ): Response = followForms(initialResponse, targetHost, false, maxSteps, allowedHosts)

    fun followBeforeLogin(
        initialResponse: Response,
        allowedHosts: Set<String>,
        maxSteps: Int = DEFAULT_MAX_STEPS,
    ): Response = followForms(initialResponse, null, true, maxSteps, allowedHosts)

    private fun followForms(
        initialResponse: Response,
        targetHost: String?,
        beforeLogin: Boolean,
        maxSteps: Int,
        allowedHosts: Set<String>?,
    ): Response {
        var currentResponse = initialResponse
        val visited = mutableSetOf<String>()
        var steps = 0
        try {
            while (true) {
                currentResponse.httpDiagnostic("WS_FEDERATION_RESPONSE", inspectForm = true)
                if (!beforeLogin && currentResponse.request.url.host == targetHost) {
                    allowedHosts?.let { requireTrusted(currentResponse.request.url, it) }
                    authenticationDiagnostic("WS_FEDERATION", "COMPLETED")
                    return currentResponse
                }

                val statusCode = currentResponse.code
                val currentUrl = currentResponse.request.url.toString()
                val body = currentResponse.peekBody(Long.MAX_VALUE).string()
                val document = Jsoup.parse(body)
                if (beforeLogin && document.selectFirst("input[type=password]") != null) return currentResponse
                val automatic = document.select("script").any { autoSubmit.containsMatchIn(it.data()) } ||
                    document.select("[onload]").any { autoSubmit.containsMatchIn(it.attr("onload")) }
                if (beforeLogin) {
                    val declaredMethod = document.select("form").singleOrNull()?.let {
                        when (it.attr("method").lowercase()) { "post" -> "POST"; "", "get" -> "GET"; else -> "OTHER" }
                    } ?: "UNKNOWN"
                    authenticationDiagnostic("WS_FEDERATION_PRE_LOGIN", "forms=${document.select("form").size} " +
                        "declaredMethod=$declaredMethod autoSubmitMarker=$automatic")
                }
                val form = htmlFormParser.parseAutoPostForm(body, currentUrl, strictAuthenticationRequest = beforeLogin)
                    ?: run {
                        if (beforeLogin) return currentResponse
                        authenticationDiagnostic("WS_FEDERATION_FORM", "MISSING")
                        throw AuthenticationException.WsFederationFormNotFound(statusCode, currentUrl)
                    }
                allowedHosts?.let {
                    requireTrusted(form.actionUrl.toHttpUrlOrNull(), it)
                }
                val fingerprint = MessageDigest.getInstance("SHA-256").digest(
                    (currentUrl + "\n" + form.method + "\n" + form.actionUrl + "\n" + form.fields.toSortedMap().toString()).toByteArray(),
                ).joinToString("") { "%02x".format(it) }
                if (!visited.add(fingerprint)) throw AuthenticationException.WsFederationCycleDetected()
                if (steps >= maxSteps) throw AuthenticationException.WsFederationStepLimitExceeded(targetHost ?: "LOGIN")
                val target = when (form.actionUrl.toHttpUrlOrNull()?.host) {
                    "logowanie.edu.lublin.eu" -> "PROJECT_LOGIN"
                    "projekt-uczen.edu.lublin.eu" -> "PROJECT_UONET"
                    else -> "OTHER"
                }
                authenticationDiagnostic("WS_FEDERATION_FORM", "FOUND kind=WS_FEDERATION method=${form.method} target=$target " +
                    "hiddenFields=${form.fields.size} wa=${"wa" in form.fields} wresult=${"wresult" in form.fields} " +
                    "wctx=${"wctx" in form.fields} wtrealm=${"wtrealm" in form.fields} autoSubmitMarker=$automatic")
                currentResponse.close()
                steps++
                authenticationDiagnostic("WS_FEDERATION", "${form.method}_STEP_$steps")
                currentResponse = executeForm(form, referer = currentUrl, allowedHosts = allowedHosts)
            }
        } catch (failure: Exception) {
            currentResponse.close()
            throw failure
        }
    }

    internal fun executeForm(
        form: HtmlForm,
        referer: String,
        allowedHosts: Set<String>? = null,
    ): Response {
        allowedHosts?.let { requireTrusted(form.actionUrl.toHttpUrlOrNull(), it) }
        val body = FormBody.Builder().apply {
            form.fields.forEach { (name, value) -> add(name, value) }
        }.build()

        val request = Request.Builder().header("Referer", referer).apply {
            if (form.method == "GET") {
                val targetUrl = checkNotNull(form.actionUrl.toHttpUrlOrNull()).newBuilder().query(null)
                form.fields.forEach { (name, value) -> targetUrl.addQueryParameter(name, value) }
                url(targetUrl.build()).get()
            } else {
                url(form.actionUrl).post(body)
            }
        }.build()

        return try {
            val client = if (allowedHosts == null) okHttpClient else okHttpClient.newBuilder()
                .addNetworkInterceptor { chain ->
                    // Runs for every redirect, before any form body can be sent to that destination.
                    requireTrusted(chain.request().url, allowedHosts)
                    chain.proceed(chain.request())
                }.build()
            client.newCall(request).execute().also { response ->
                if (!response.isSuccessful && response.code !in REDIRECT_STATUS_CODES) {
                    val exception = AuthenticationException.Http(
                        statusCode = response.code,
                        url = response.request.url.toString(),
                    )
                    response.close()
                    throw exception
                }
            }
        } catch (exception: IOException) {
            throw AuthenticationException.Network(exception)
        }
    }

    private fun requireTrusted(url: HttpUrl?, allowedHosts: Set<String>) {
        if (url == null || url.scheme != "https" || url.port != 443 || url.host !in allowedHosts ||
            url.username.isNotEmpty() || url.password.isNotEmpty()) {
            authenticationDiagnostic("WS_FEDERATION_SECURITY", "UNTRUSTED_DESTINATION")
            throw AuthenticationException.UnsafeFederationDestination()
        }
    }

    private companion object {
        const val DEFAULT_MAX_STEPS = 10
        val autoSubmit = Regex("\\.(?:submit|requestSubmit)\\s*\\(")
        val REDIRECT_STATUS_CODES = 300..399
    }
}
