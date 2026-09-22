package pl.usagi.edziennik.network.grades

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl
import pl.usagi.edziennik.dto.ClassificationPeriodDto
import pl.usagi.edziennik.dto.GradesResponseDto
import pl.usagi.edziennik.network.UonetApiClient
import javax.inject.Inject

class UonetGradesDataSource @Inject constructor(
    private val apiClient: UonetApiClient,
) {
    suspend fun getClassificationPeriods(
        studentKey: String,
        diaryId: Int,
    ): List<ClassificationPeriodDto> = withContext(Dispatchers.IO) {
        val url = API_URL.toHttpUrl()
            .newBuilder()
            .addPathSegment("OkresyKlasyfikacyjne")
            .addQueryParameter("key", studentKey)
            .addQueryParameter("idDziennik", diaryId.toString())
            .build()

        executeJsonRequest(url, ListSerializer(ClassificationPeriodDto.serializer()))
    }

    suspend fun getGrades(
        studentKey: String,
        periodId: Int,
    ): GradesResponseDto = withContext(Dispatchers.IO) {
        val url = API_URL.toHttpUrl()
            .newBuilder()
            .addPathSegment("Oceny")
            .addQueryParameter("key", studentKey)
            .addQueryParameter("idOkresKlasyfikacyjny", periodId.toString())
            .build()

        executeJsonRequest(url, GradesResponseDto.serializer())
    }

    private fun <T> executeJsonRequest(
        url: HttpUrl,
        deserializer: DeserializationStrategy<T>,
    ): T {
        val endpoint = url.encodedPath.substringAfterLast("/")
        try {
            Log.d(TAG, "Grades: loading $endpoint")
            return apiClient.get(url, deserializer, requireJsonContentType = false) { response ->
                Log.d(TAG, "Grades: $endpoint HTTP ${response.code}")
                if (response.isSuccessful) {
                    val contentType = response.header("Content-Type").orEmpty()
                    if (contentType.isNotBlank() && !contentType.contains("json", ignoreCase = true)) {
                        Log.w(TAG, "Grades: $endpoint returned non-json content type: $contentType")
                    }
                }
            }
        } catch (failure: UonetApiClient.Failure) {
            when (failure) {
                is UonetApiClient.Failure.Network -> {
                    val cause = failure.networkCause
                    Log.w(TAG, "Grades: $endpoint network failed: ${cause::class.simpleName}: ${cause.message}")
                    throw GradesFetchException.Network(cause)
                }
                is UonetApiClient.Failure.Http -> throw GradesFetchException.Http(failure.statusCode)
                is UonetApiClient.Failure.Unexpected -> {
                    val cause = failure.cause
                    val reason = if (cause is SerializationException) "serialization failed" else "contract failed"
                    Log.w(TAG, "Grades: $endpoint $reason: ${cause?.let { it::class.simpleName }}: ${cause?.message}")
                    throw GradesFetchException.UnexpectedResponse(cause)
                }
            }
        } catch (exception: IllegalStateException) {
            Log.w(TAG, "Grades: $endpoint contract failed: ${exception::class.simpleName}: ${exception.message}")
            throw GradesFetchException.UnexpectedResponse(exception)
        }
    }

    private companion object {
        const val TAG = "UonetGrades"
        const val API_URL = "https://projekt-uczen.edu.lublin.eu/lublin/api"
    }
}

sealed class GradesFetchException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    class Network(cause: Throwable) : GradesFetchException("Could not fetch UONET+ grades.", cause)

    class Http(statusCode: Int) : GradesFetchException("UONET+ grades request failed with HTTP $statusCode.")

    class UnexpectedResponse(cause: Throwable? = null) :
        GradesFetchException("UONET+ grades response did not match the expected contract.", cause)
}
