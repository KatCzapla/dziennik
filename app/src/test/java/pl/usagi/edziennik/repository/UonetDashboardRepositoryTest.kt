package pl.usagi.edziennik.repository

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import pl.usagi.edziennik.domain.Student
import pl.usagi.edziennik.network.UonetApiClient
import pl.usagi.edziennik.network.dashboard.UonetDashboardDataSource
import java.time.LocalDate

class UonetDashboardRepositoryTest {
    @Test
    fun getsAndMapsAllInformationUsingStudentKeyAndExistingApiClient() = runTest {
        val requests = mutableListOf<Request>()
        val repository = repository("""
            [
                {"temat":"Pierwsza","tresc":"Treść 1","data":"2026-09-07T00:00:00+02:00"},
                {"temat":"Druga","tresc":"Treść 2","data":"2026-09-08T00:00:00+02:00"}
            ]
        """.trimIndent(), requests)

        val information = repository.getInformation(student)

        assertEquals(listOf("Pierwsza", "Druga"), information.map { it.subject })
        assertEquals(listOf("Treść 1", "Treść 2"), information.map { it.content })
        assertEquals(listOf(LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 8)), information.map { it.date })
        assertEquals("GET", requests.single().method)
        assertEquals("/lublin/api/InformacjeTablica", requests.single().url.encodedPath)
        assertEquals(student.key, requests.single().url.queryParameter("key"))
    }

    @Test
    fun emptyResponseRemainsEmpty() = runTest {
        assertEquals(emptyList<Any>(), repository("[]").getInformation(student))
    }

    @Test
    fun endpointErrorIsPassedToDashboardForIndependentHandling() {
        assertThrows(UonetApiClient.Failure.Http::class.java) {
            runTest { repository("[]", code = 500).getInformation(student) }
        }
    }

    private fun repository(
        body: String,
        requests: MutableList<Request> = mutableListOf(),
        code: Int = 200,
    ): UonetDashboardRepository {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            requests += chain.request()
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("Response")
                .header("Content-Type", "application/json")
                .body(body.toResponseBody("application/json".toMediaType()))
                .build()
        }.build()
        return UonetDashboardRepository(UonetDashboardDataSource(UonetApiClient(client, Json)))
    }

    private val student = Student("student-key", 1, "Uczeń", "4A", "Szkoła")
}
