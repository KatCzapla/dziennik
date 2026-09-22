package pl.usagi.edziennik.network

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import javax.inject.Inject

class UonetApiClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
) {
    fun <T> get(
        url: HttpUrl,
        deserializer: DeserializationStrategy<T>,
        requireJsonContentType: Boolean = true,
        onResponse: (Response) -> Unit = {},
    ): T {
        val request = Request.Builder()
            .url(url)
            .header("Accept", ACCEPT_JSON)
            .get()
            .build()

        try {
            okHttpClient.newCall(request).execute().use { response ->
                onResponse(response)
                if (!response.isSuccessful) {
                    throw Failure.Http(response.code)
                }
                if (requireJsonContentType &&
                    !response.header("Content-Type").orEmpty().contains("application/json", ignoreCase = true)
                ) {
                    throw Failure.Unexpected()
                }

                return json.decodeFromString(deserializer, response.body.string())
            }
        } catch (exception: IOException) {
            throw Failure.Network(exception)
        } catch (exception: SerializationException) {
            throw Failure.Unexpected(exception)
        } catch (exception: IllegalArgumentException) {
            throw Failure.Unexpected(exception)
        }
    }

    sealed class Failure(cause: Throwable? = null) : Exception(cause) {
        class Network(val networkCause: IOException) : Failure(networkCause)

        class Http(val statusCode: Int) : Failure()

        class Unexpected(cause: Throwable? = null) : Failure(cause)
    }

    private companion object {
        const val ACCEPT_JSON = "application/json, text/plain, */*"
    }
}
