package pl.usagi.edziennik.network.messages

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import pl.usagi.edziennik.network.messages.MessagesDiagnostics.debug
import pl.usagi.edziennik.network.messages.MessagesDiagnostics.warn
import pl.usagi.edziennik.network.session.AuthenticationException
import pl.usagi.edziennik.network.session.WsFederationNavigator
import java.io.IOException
import javax.inject.Inject

interface MessagesSsoClient {
    suspend fun authenticate(appUrl: HttpUrl): MessagesSessionContext
}

class UonetMessagesSsoClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val wsFederationNavigator: WsFederationNavigator,
    private val bootstrapParser: MessagesBootstrapParser,
) : MessagesSsoClient {
    override suspend fun authenticate(appUrl: HttpUrl): MessagesSessionContext = withContext(Dispatchers.IO) {
        debug("Messages SSO starting: ${appUrl.safeHostAndPath()}")
        val initialResponse = executeGet(appUrl)
        debug("Messages SSO initial response: ${initialResponse.safeSummary()}")
        val finalResponse = if (initialResponse.request.url.host == appUrl.host) {
            initialResponse
        } else {
            runCatching { wsFederationNavigator.follow(initialResponse, appUrl.host) }
                .getOrElse { error ->
                    throw when (error) {
                        is AuthenticationException -> MessagesSessionException.WsFederation(error)
                        else -> error
                    }
                }
        }

        finalResponse.use { response ->
            debug("Messages SSO final response: ${response.safeSummary()}")
            if (response.request.url.host != appUrl.host) {
                warn("Messages SSO failed: finalHost=${response.request.url.host}")
                throw MessagesSessionException.WsFederation(
                    AuthenticationException.MainApplicationAuthenticationFailed(response.request.url.host),
                )
            }
            bootstrapParser.parse(
                html = response.body.string(),
                appUrl = appUrl,
            )
        }
    }

    private fun executeGet(url: HttpUrl) =
        try {
            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            okHttpClient.newCall(request).execute().also { response ->
                if (!response.isSuccessful && response.code !in REDIRECT_STATUS_CODES) {
                    val exception = MessagesSessionException.Http(response.code)
                    response.close()
                    throw exception
                }
            }
        } catch (exception: IOException) {
            throw MessagesSessionException.Network(exception)
        }

    private companion object {
        val REDIRECT_STATUS_CODES = 300..399
    }
}
