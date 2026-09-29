package pl.usagi.edziennik.network.payments

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import pl.usagi.edziennik.dto.PaymentsDto
import pl.usagi.edziennik.network.UonetApiClient
import javax.inject.Inject

class UonetPaymentsDataSource @Inject constructor(private val apiClient: UonetApiClient) {
    suspend fun getPayments(studentKey: String): PaymentsDto = withContext(Dispatchers.IO) {
        val url = "https://projekt-uczen.edu.lublin.eu/lublin/api".toHttpUrl().newBuilder()
            .addPathSegment("Oplaty")
            .addQueryParameter("key", studentKey)
            .build()
        apiClient.get(url, PaymentsDto.serializer())
    }
}
