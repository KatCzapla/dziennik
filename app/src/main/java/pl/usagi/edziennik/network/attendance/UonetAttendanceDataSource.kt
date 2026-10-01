package pl.usagi.edziennik.network.attendance

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.HttpUrl.Companion.toHttpUrl
import pl.usagi.edziennik.dto.AttendanceResponseDto
import pl.usagi.edziennik.dto.AttendanceExcusesResponseDto
import pl.usagi.edziennik.dto.AttendanceStatisticsDto
import pl.usagi.edziennik.dto.AttendanceSubjectDto
import pl.usagi.edziennik.dto.SubmitAttendanceExcuseDto
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

    suspend fun getSubjects(studentKey: String): List<AttendanceSubjectDto> = withContext(Dispatchers.IO) {
        val url = API_URL.toHttpUrl()
            .newBuilder()
            .addPathSegment("Przedmioty")
            .addQueryParameter("key", studentKey)
            .build()

        try {
            apiClient.get(url, ListSerializer(AttendanceSubjectDto.serializer()))
        } catch (failure: UonetApiClient.Failure) {
            throw failure.toAttendanceFetchException()
        }
    }

    suspend fun getExcuses(
        studentKey: String,
        dateFrom: String,
        dateTo: String,
    ): AttendanceExcusesResponseDto = withContext(Dispatchers.IO) {
        val url = API_URL.toHttpUrl()
            .newBuilder()
            .addPathSegment("Usprawiedliwienia")
            .addQueryParameter("key", studentKey)
            .addQueryParameter("dataOd", dateFrom)
            .addQueryParameter("dataDo", dateTo)
            .build()

        try {
            apiClient.get(url, AttendanceExcusesResponseDto.serializer())
        } catch (failure: UonetApiClient.Failure) {
            throw failure.toAttendanceFetchException()
        }
    }

    suspend fun submitExcuse(request: SubmitAttendanceExcuseDto) = withContext(Dispatchers.IO) {
        val url = API_URL.toHttpUrl().newBuilder().addPathSegment("Usprawiedliwienia").build()
        try {
            apiClient.post(url, request, SubmitAttendanceExcuseDto.serializer(), includeNulls = true)
        } catch (failure: UonetApiClient.Failure) {
            throw failure.toAttendanceFetchException()
        }
    }

    suspend fun getStatistics(studentKey: String, subjectId: Int): AttendanceStatisticsDto =
        withContext(Dispatchers.IO) {
            val url = API_URL.toHttpUrl()
                .newBuilder()
                .addPathSegment("FrekwencjaStatystyki")
                .addQueryParameter("key", studentKey)
                .addQueryParameter("idPrzedmiot", subjectId.toString())
                .build()

            try {
                apiClient.get(url, AttendanceStatisticsDto.serializer())
            } catch (failure: UonetApiClient.Failure) {
                throw failure.toAttendanceFetchException()
            }
        }

    private companion object {
        const val API_URL = "https://projekt-uczen.edu.lublin.eu/lublin/api"
    }
}

private fun UonetApiClient.Failure.toAttendanceFetchException(): AttendanceFetchException =
    when (this) {
        is UonetApiClient.Failure.Network -> AttendanceFetchException.Network(networkCause)
        is UonetApiClient.Failure.Http -> AttendanceFetchException.Http(statusCode)
        is UonetApiClient.Failure.Unexpected -> AttendanceFetchException.UnexpectedResponse
    }

sealed class AttendanceFetchException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    class Network(cause: Throwable) : AttendanceFetchException("Could not fetch UONET+ attendance.", cause)

    class Http(statusCode: Int) : AttendanceFetchException("UONET+ attendance request failed with HTTP $statusCode.")

    data object UnexpectedResponse : AttendanceFetchException("UONET+ attendance response did not match the expected contract.")
}
