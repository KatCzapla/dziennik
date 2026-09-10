package pl.katcz.edziennik.network.context

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import pl.katcz.edziennik.dto.ContextDto
import java.io.IOException
import javax.inject.Inject

class UonetContextDataSource @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
) {
    suspend fun getContext(): ContextDto = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(CONTEXT_URL)
            .header("Accept", ACCEPT_JSON)
            .get()
            .build()

        try {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw ContextFetchException.Http(response.code)
                }
                if (!response.header("Content-Type").orEmpty().contains("application/json", ignoreCase = true)) {
                    throw ContextFetchException.UnexpectedResponse
                }

                json.decodeFromString<ContextDto>(response.body.string())
            }
        } catch (exception: IOException) {
            throw ContextFetchException.Network(exception)
        } catch (exception: SerializationException) {
            throw ContextFetchException.UnexpectedResponse
        } catch (exception: IllegalArgumentException) {
            throw ContextFetchException.UnexpectedResponse
        }
    }

    private companion object {
        const val CONTEXT_URL = "https://projekt-uczen.edu.lublin.eu/lublin/api/Context"
        const val ACCEPT_JSON = "application/json, text/plain, */*"
    }
}

sealed class ContextFetchException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    class Network(cause: Throwable) : ContextFetchException("Could not fetch UONET+ context.", cause)

    class Http(statusCode: Int) : ContextFetchException("UONET+ context request failed with HTTP $statusCode.")

    data object UnexpectedResponse : ContextFetchException("UONET+ context response did not match the expected contract.")
}
