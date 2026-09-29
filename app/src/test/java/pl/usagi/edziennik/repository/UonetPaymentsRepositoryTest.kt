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
import pl.usagi.edziennik.network.payments.UonetPaymentsDataSource
import pl.usagi.edziennik.ui.payments.operationAmountLabel

class UonetPaymentsRepositoryTest {
    @Test
    fun fetchesPaymentsWithReadOnlyGetAndStudentKey() = runTest {
        val requests = mutableListOf<Request>()
        val repository = repository(requests, 200)
        val payments = repository.getPayments(student)

        assertEquals("GET", requests.single().method)
        assertEquals("/lublin/api/Oplaty", requests.single().url.encodedPath)
        assertEquals(student.key, requests.single().url.queryParameter("key"))
        assertEquals("Nadpłata 6,00 zł", payments.bankAccounts.single().operationAmountLabel())
    }

    @Test
    fun httpFailureCanBeHandledByScreen() {
        assertThrows(UonetApiClient.Failure.Http::class.java) {
            runTest { repository(mutableListOf(), 500).getPayments(student) }
        }
    }

    private fun repository(requests: MutableList<Request>, code: Int): UonetPaymentsRepository {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            requests += chain.request()
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("Response")
                .header("Content-Type", "application/json")
                .body("""{"kontaBankowe":[{"nazwaOperacji":"Nadpłata","kwotaDoZaplaty":6.00}],"oplaty":[]}"""
                    .toResponseBody("application/json".toMediaType()))
                .build()
        }.build()
        return UonetPaymentsRepository(UonetPaymentsDataSource(UonetApiClient(client, Json { ignoreUnknownKeys = true })))
    }

    private val student = Student("student-key", 1, "Uczeń", "4A", "Szkoła")
}
