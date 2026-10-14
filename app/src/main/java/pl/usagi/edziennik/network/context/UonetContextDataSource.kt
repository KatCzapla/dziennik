package pl.usagi.edziennik.network.context

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import pl.usagi.edziennik.dto.ContextDto
import pl.usagi.edziennik.network.UonetApiClient
import javax.inject.Inject

class UonetContextDataSource @Inject constructor(
    private val apiClient: UonetApiClient,
) {
    suspend fun getContext(): ContextDto = withContext(Dispatchers.IO) {
        try {
            apiClient.get(CONTEXT_URL.toHttpUrl(), ContextDto.serializer())
        } catch (failure: UonetApiClient.Failure) {
            when (failure) {
                is UonetApiClient.Failure.Network -> throw ContextFetchException.Network(failure.networkCause)
                is UonetApiClient.Failure.Http -> throw ContextFetchException.Http(failure.statusCode)
                is UonetApiClient.Failure.Unexpected -> throw ContextFetchException.UnexpectedResponse
            }
        }
    }

    private companion object {
        const val CONTEXT_URL = "https://projekt-uczen.edu.lublin.eu/lublin/api/Context"
    }
}

sealed class ContextFetchException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    class Network(cause: Throwable) : ContextFetchException("Could not fetch UONET+ context.", cause)

    class Http(val statusCode: Int) : ContextFetchException("UONET+ context request failed with HTTP $statusCode.")

    data object UnexpectedResponse : ContextFetchException("UONET+ context response did not match the expected contract.")
}
