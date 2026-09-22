package pl.usagi.edziennik.network.attendance

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import pl.usagi.edziennik.dto.AttendanceResponseDto
import pl.usagi.edziennik.network.UonetApiClient
import javax.inject.Inject

class UonetAttendanceDataSource @Inject constructor(
    private val apiClient: UonetApiClient,
) {
    suspend fun getAttendance(
        studentKey: String,
        dateFrom: String,
        dateTo: String,
    ): AttendanceResponseDto = withContext(Dispatchers.IO) {
        val url = API_URL.toHttpUrl()
            .newBuilder()
            .addPathSegment("Frekwencja")
            .addQueryParameter("key", studentKey)
            .addQueryParameter("dataOd", dateFrom)
            .addQueryParameter("dataDo", dateTo)
            .build()

        try {
            apiClient.get(url, AttendanceResponseDto.serializer())
        } catch (failure: UonetApiClient.Failure) {
            when (failure) {
                is UonetApiClient.Failure.Network -> throw AttendanceFetchException.Network(failure.networkCause)
                is UonetApiClient.Failure.Http -> throw AttendanceFetchException.Http(failure.statusCode)
                is UonetApiClient.Failure.Unexpected -> throw AttendanceFetchException.UnexpectedResponse
            }
        }
    }

    private companion object {
        const val API_URL = "https://projekt-uczen.edu.lublin.eu/lublin/api"
    }
}

sealed class AttendanceFetchException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    class Network(cause: Throwable) : AttendanceFetchException("Could not fetch UONET+ attendance.", cause)

    class Http(statusCode: Int) : AttendanceFetchException("UONET+ attendance request failed with HTTP $statusCode.")

    data object UnexpectedResponse : AttendanceFetchException("UONET+ attendance response did not match the expected contract.")
}
