package pl.usagi.edziennik.repository

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import pl.usagi.edziennik.domain.ActiveStudentManager
import pl.usagi.edziennik.domain.Student
import pl.usagi.edziennik.network.UonetApiClient
import pl.usagi.edziennik.network.timetable.UonetTimetableDataSource
import java.time.LocalDate

class TimetableDaysOffRepositoryTest {
    private val from = LocalDate.of(2026, 10, 12)
    private val to = LocalDate.of(2026, 10, 16)
    private val manager = ActiveStudentManager().apply { initialize(listOf(Student("runtime", 1, "Uczeń", "1A", "Szkoła", 1220))) }
    private val requests = mutableListOf<Request>()

    @Test fun requestsDaysOffForActiveStudentAndDisplayedRange() = runTest {
        val result = repository("""[{"nazwa":"dZień Edukacji narodowej","dataOd":"2026-10-14T00:00:00+02:00","dataDo":"2026-10-14T00:00:00+02:00","wszystkieSkladowe":false,"jednostkiSkladowe":[{"id":1220,"nazwa":"Szkoła"}]}]""").getDaysOff(from, to)
        val request = requests.single()
        assertEquals("/lublin/api/DniWolne", request.url.encodedPath)
        assertEquals("runtime", request.url.queryParameter("key"))
        assertNotNull(request.url.queryParameter("dataOd"))
        assertNotNull(request.url.queryParameter("dataDo"))
        assertEquals(LocalDate.of(2026, 10, 14), result.single().from)
        assertEquals("Dzień Edukacji Narodowej", result.single().name)
    }

    @Test fun emptyResponseIsEmptyDataNotFailure() = runTest {
        assertTrue(repository("[]").getDaysOff(from, to).isEmpty())
    }

    @Test fun endpointFailureDoesNotBecomeAnEmptySuccessfulResponse() = runTest {
        assertTrue(runCatching { repository("{}", 503).getDaysOff(from, to) }.isFailure)
    }

    @Test fun changedStudentSuppliesNewKey() = runTest {
        val repository = repository("[]")
        repository.getDaysOff(from, to)
        manager.setActiveStudent(Student("other-runtime", 2, "Inny uczeń", "2A", "Szkoła", 999))
        repository.getDaysOff(from, to)
        assertEquals(listOf("runtime", "other-runtime"), requests.map { it.url.queryParameter("key") })
    }

    private fun repository(body: String, code: Int = 200): UonetTimetableRepository {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            requests += chain.request()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("test")
                .header("Content-Type", "application/json").body(body.toResponseBody()).build()
        }.build()
        return UonetTimetableRepository(manager, UonetTimetableDataSource(UonetApiClient(client, Json)))
    }
}
