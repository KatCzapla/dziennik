package pl.usagi.edziennik.network.messages

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject

interface MessagesUrlProvider {
    suspend fun getMessagesAppUrl(): HttpUrl
}

class UonetMessagesUrlProvider @Inject constructor(
    private val mainAppClient: UonetMainAppMessagesUrlClient,
) : MessagesUrlProvider {
    override suspend fun getMessagesAppUrl(): HttpUrl = withContext(Dispatchers.IO) {
        val rawUrl = mainAppClient.getMessagesUrl()

        runCatching { rawUrl.toHttpUrl().toMessagesAppUrl() }
            .getOrElse { throw MessagesSessionException.InvalidMessagesUrl(it) }
    }
}

class UonetMainAppMessagesUrlClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {
    fun getMessagesUrl(): String {
        val request = Request.Builder()
            .url(MAIN_APP_URL)
            .header("Accept", ACCEPT_HTML)
            .get()
            .build()

        return try {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw MessagesSessionException.UrlSourceRequestFailed(
                        MessagesSessionException.Http(response.code),
                    )
                }

                val html = response.body.string()
                extractVParamValue(html, "wiadomosciUrl")
                    ?: FALLBACK_MESSAGES_APP_URL.also {
                        MessagesDiagnostics.warn(
                            "Messages URL missing in main app VParam, using Python POC fallback: ${it.toHttpUrl().safeHostAndPath()}",
                        )
                    }
            }
        } catch (exception: IOException) {
            throw MessagesSessionException.UrlSourceRequestFailed(exception)
        }
    }

    private fun extractVParamValue(
        html: String,
        name: String,
    ): String? {
        val pattern = Regex("""["']?${Regex.escape(name)}["']?\s*:\s*['"]([^'"]+)['"]""")
        return pattern.find(html)?.groupValues?.get(1)?.replace("\\/", "/")
    }

    private companion object {
        const val MAIN_APP_URL = "https://projekt-uczen.edu.lublin.eu/lublin/App"
        const val FALLBACK_MESSAGES_APP_URL = "https://projekt-wiadomosci.edu.lublin.eu/lublin/App/odebrane"
        const val ACCEPT_HTML = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
    }
}

fun HttpUrl.toMessagesAppUrl(): HttpUrl {
    val pathSegments = encodedPathSegments.filter { it.isNotBlank() }
    val appIndex = pathSegments.indexOf("App")
    if (appIndex >= 0 && pathSegments.drop(appIndex + 1).isNotEmpty()) return this
    if (appIndex >= 0) {
        return newBuilder()
            .addPathSegment("odebrane")
            .build()
    }

    return newBuilder()
        .addPathSegment("App")
        .addPathSegment("odebrane")
        .build()
}

fun HttpUrl.toMessagesApiBaseUrl(): HttpUrl {
    val appIndex = encodedPathSegments.indexOf("App")
    val environmentSegments = if (appIndex >= 0) {
        encodedPathSegments.take(appIndex)
    } else {
        encodedPathSegments
    }.filter { it.isNotBlank() }

    return newBuilder()
        .encodedPath("/")
        .query(null)
        .fragment(null)
        .apply {
            environmentSegments.forEach { addEncodedPathSegment(it) }
            addPathSegment("api")
        }
        .build()
}
