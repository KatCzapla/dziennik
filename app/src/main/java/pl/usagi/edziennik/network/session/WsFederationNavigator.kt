package pl.usagi.edziennik.network.session

import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import pl.usagi.edziennik.network.messages.MessagesDiagnostics
import pl.usagi.edziennik.network.messages.safeSummary
import pl.usagi.edziennik.network.html.HtmlForm
import pl.usagi.edziennik.network.html.UonetHtmlFormParser
import java.io.IOException
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
    ): Response {
        var currentResponse = initialResponse

        repeat(maxSteps) { step ->
            if (currentResponse.request.url.host == targetHost) {
                MessagesDiagnostics.debug("WS-Federation completed: ${currentResponse.safeSummary()}")
                return currentResponse
            }

            val statusCode = currentResponse.code
            val currentUrl = currentResponse.request.url.toString()
            val body = currentResponse.body.string()
            val summary = currentResponse.safeSummary()
            currentResponse.close()

            val form = htmlFormParser.parseAutoPostForm(body, currentUrl)
                ?: throw AuthenticationException.WsFederationFormNotFound(statusCode, currentUrl)

            MessagesDiagnostics.debug("WS-Federation step ${step + 1}: $summary, formFound=true")
            currentResponse = executeFormPost(form, referer = currentUrl)
        }

        currentResponse.close()
        throw AuthenticationException.WsFederationStepLimitExceeded(targetHost)
    }

    private fun executeFormPost(
        form: HtmlForm,
        referer: String,
    ): Response {
        val body = FormBody.Builder().apply {
            form.fields.forEach { (name, value) -> add(name, value) }
        }.build()

        val request = Request.Builder()
            .url(form.actionUrl)
            .header("Referer", referer)
            .post(body)
            .build()

        return try {
            okHttpClient.newCall(request).execute().also { response ->
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

    private companion object {
        const val DEFAULT_MAX_STEPS = 10
        val REDIRECT_STATUS_CODES = 300..399
    }
}
