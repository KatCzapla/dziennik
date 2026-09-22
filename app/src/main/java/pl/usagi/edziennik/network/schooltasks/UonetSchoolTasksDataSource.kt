package pl.usagi.edziennik.network.schooltasks

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl
import pl.usagi.edziennik.dto.HomeworkDetailsDto
import pl.usagi.edziennik.dto.SchoolTaskDto
import pl.usagi.edziennik.dto.TestDetailsDto
import pl.usagi.edziennik.network.UonetApiClient
import javax.inject.Inject

interface SchoolTasksRemoteDataSource {
    suspend fun getSchoolTasks(
        studentKey: String,
        dateFrom: String,
        dateTo: String,
    ): List<SchoolTaskDto>

    suspend fun getHomeworkDetails(
        studentKey: String,
        homeworkId: Int,
    ): HomeworkDetailsDto

    suspend fun getTestDetails(
        studentKey: String,
        testId: Int,
    ): TestDetailsDto
}

class UonetSchoolTasksDataSource @Inject constructor(
    private val apiClient: UonetApiClient,
) : SchoolTasksRemoteDataSource {
    override suspend fun getSchoolTasks(
        studentKey: String,
        dateFrom: String,
        dateTo: String,
    ): List<SchoolTaskDto> = withContext(Dispatchers.IO) {
        val url = API_URL.toHttpUrl()
            .newBuilder()
            .addPathSegment("SprawdzianyZadaniaDomowe")
            .addQueryParameter("key", studentKey)
            .addQueryParameter("dataOd", dateFrom)
            .addQueryParameter("dataDo", dateTo)
            .build()

        executeJsonRequest(url, ListSerializer(SchoolTaskDto.serializer()))
    }

    override suspend fun getHomeworkDetails(
        studentKey: String,
        homeworkId: Int,
    ): HomeworkDetailsDto = withContext(Dispatchers.IO) {
        val url = API_URL.toHttpUrl()
            .newBuilder()
            .addPathSegment("ZadanieDomoweSzczegoly")
            .addQueryParameter("key", studentKey)
            .addQueryParameter("id", homeworkId.toString())
            .build()

        executeJsonRequest(url, HomeworkDetailsDto.serializer())
    }

    override suspend fun getTestDetails(
        studentKey: String,
        testId: Int,
    ): TestDetailsDto = withContext(Dispatchers.IO) {
        val url = API_URL.toHttpUrl()
            .newBuilder()
            .addPathSegment("SprawdzianSzczegoly")
            .addQueryParameter("key", studentKey)
            .addQueryParameter("id", testId.toString())
            .build()

        executeJsonRequest(url, TestDetailsDto.serializer())
    }

    private fun <T> executeJsonRequest(
        url: HttpUrl,
        deserializer: DeserializationStrategy<T>,
    ): T {
        try {
            return apiClient.get(url, deserializer)
        } catch (failure: UonetApiClient.Failure) {
            when (failure) {
                is UonetApiClient.Failure.Network -> throw SchoolTasksFetchException.Network(failure.networkCause)
                is UonetApiClient.Failure.Http -> throw SchoolTasksFetchException.Http(failure.statusCode)
                is UonetApiClient.Failure.Unexpected -> throw SchoolTasksFetchException.UnexpectedResponse
            }
        }
    }

    private companion object {
        const val API_URL = "https://projekt-uczen.edu.lublin.eu/lublin/api"
    }
}

sealed class SchoolTasksFetchException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    class Network(cause: Throwable) : SchoolTasksFetchException("Could not fetch UONET+ school tasks.", cause)

    class Http(statusCode: Int) : SchoolTasksFetchException("UONET+ school tasks request failed with HTTP $statusCode.")

    data object UnexpectedResponse : SchoolTasksFetchException("UONET+ school tasks response did not match the expected contract.")
}
