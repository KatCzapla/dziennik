package pl.katcz.edziennik.network.timetable

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import pl.katcz.edziennik.dto.TimetableEntryDto
import java.io.IOException
import javax.inject.Inject

class UonetTimetableDataSource @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
) {
    suspend fun getTimetable(
        studentKey: String,
        dateFrom: String,
        dateTo: String,
        dataScope: Int = 2,
    ): List<TimetableEntryDto> = withContext(Dispatchers.IO) {
        val url = API_URL.toHttpUrl()
            .newBuilder()
            .addPathSegment("PlanZajec")
            .addQueryParameter("key", studentKey)
            .addQueryParameter("dataOd", dateFrom)
            .addQueryParameter("dataDo", dateTo)
            .addQueryParameter("zakresDanych", dataScope.toString())
            .build()

        val request = Request.Builder()
            .url(url)
            .header("Accept", ACCEPT_JSON)
            .get()
            .build()

        try {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw TimetableFetchException.Http(response.code)
                }
                if (!response.header("Content-Type").orEmpty().contains("application/json", ignoreCase = true)) {
                    throw TimetableFetchException.UnexpectedResponse
                }

                json.decodeFromString<List<TimetableEntryDto>>(response.body.string())
            }
        } catch (exception: IOException) {
            throw TimetableFetchException.Network(exception)
        } catch (exception: SerializationException) {
            throw TimetableFetchException.UnexpectedResponse
        } catch (exception: IllegalArgumentException) {
            throw TimetableFetchException.UnexpectedResponse
        }
    }

    private companion object {
        const val API_URL = "https://projekt-uczen.edu.lublin.eu/lublin/api"
        const val ACCEPT_JSON = "application/json, text/plain, */*"
    }
}

sealed class TimetableFetchException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    class Network(cause: Throwable) : TimetableFetchException("Could not fetch UONET+ timetable.", cause)

    class Http(statusCode: Int) : TimetableFetchException("UONET+ timetable request failed with HTTP $statusCode.")

    data object UnexpectedResponse : TimetableFetchException("UONET+ timetable response did not match the expected contract.")
}
