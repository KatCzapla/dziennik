package pl.usagi.edziennik.network.timetable

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.HttpUrl.Companion.toHttpUrl
import pl.usagi.edziennik.dto.TimetableEntryDto
import pl.usagi.edziennik.dto.SchoolDayOffDto
import pl.usagi.edziennik.network.UonetApiClient
import javax.inject.Inject

class UonetTimetableDataSource @Inject constructor(
    private val apiClient: UonetApiClient,
) {
    suspend fun getDaysOff(studentKey: String, dateFrom: String, dateTo: String): List<SchoolDayOffDto> =
        withContext(Dispatchers.IO) {
            val url = API_URL.toHttpUrl().newBuilder().addPathSegment("DniWolne")
                .addQueryParameter("key", studentKey)
                .addQueryParameter("dataOd", dateFrom)
                .addQueryParameter("dataDo", dateTo).build()
            apiClient.get(url, ListSerializer(SchoolDayOffDto.serializer()))
        }

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

        try {
            apiClient.get(url, ListSerializer(TimetableEntryDto.serializer()))
        } catch (failure: UonetApiClient.Failure) {
            when (failure) {
                is UonetApiClient.Failure.Network -> throw TimetableFetchException.Network(failure.networkCause)
                is UonetApiClient.Failure.Http -> throw TimetableFetchException.Http(failure.statusCode)
                is UonetApiClient.Failure.Unexpected -> throw TimetableFetchException.UnexpectedResponse
            }
        }
    }

    private companion object {
        const val API_URL = "https://projekt-uczen.edu.lublin.eu/lublin/api"
    }
}

sealed class TimetableFetchException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    class Network(cause: Throwable) : TimetableFetchException("Could not fetch UONET+ timetable.", cause)

    class Http(val statusCode: Int) : TimetableFetchException("UONET+ timetable request failed with HTTP $statusCode.")

    data object UnexpectedResponse : TimetableFetchException("UONET+ timetable response did not match the expected contract.")
}
