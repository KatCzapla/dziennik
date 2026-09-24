package pl.usagi.edziennik.network.attendance

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import pl.usagi.edziennik.network.UonetApiClient

class UonetAttendanceDataSourceTest {
    @Test
    fun requestsSubjectsAndStatisticsWithSelectedSubjectId() = runTest {
        val requests = mutableListOf<Request>()
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                requests += request
                val body = when (request.url.encodedPath.substringAfterLast('/')) {
                    "Przedmioty" -> """[{"nazwa":"Wszystkie","id":-1},{"nazwa":"Język polski","id":7867}]"""
                    "FrekwencjaStatystyki" -> """{"podsumowanie":100.0,"statystyki":[]}"""
                    else -> error("Unexpected endpoint")
                }
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .header("Content-Type", "application/json")
                    .body(body.toResponseBody())
                    .build()
            }
            .build()
        val dataSource = UonetAttendanceDataSource(UonetApiClient(client, Json))

        val subjects = dataSource.getSubjects("test-student-key")
        val statistics = dataSource.getStatistics("test-student-key", 7867)

        assertEquals(listOf(-1, 7867), subjects.map { it.id })
        assertEquals(100.0, statistics.summary)
        assertEquals(listOf("Przedmioty", "FrekwencjaStatystyki"), requests.map {
            it.url.encodedPath.substringAfterLast('/')
        })
        assertEquals(listOf("test-student-key", "test-student-key"), requests.map {
            it.url.queryParameter("key")
        })
        assertEquals("7867", requests.last().url.queryParameter("idPrzedmiot"))
    }
}
