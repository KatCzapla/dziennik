package pl.usagi.edziennik.network.dashboard

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.HttpUrl.Companion.toHttpUrl
import pl.usagi.edziennik.dto.DashboardInformationDto
import pl.usagi.edziennik.network.UonetApiClient
import javax.inject.Inject

class UonetDashboardDataSource @Inject constructor(
    private val apiClient: UonetApiClient,
) {
    suspend fun getInformation(studentKey: String): List<DashboardInformationDto> = withContext(Dispatchers.IO) {
        val url = "https://projekt-uczen.edu.lublin.eu/lublin/api".toHttpUrl().newBuilder()
            .addPathSegment("InformacjeTablica")
            .addQueryParameter("key", studentKey)
            .build()
        apiClient.get(url, ListSerializer(DashboardInformationDto.serializer()))
    }
}
