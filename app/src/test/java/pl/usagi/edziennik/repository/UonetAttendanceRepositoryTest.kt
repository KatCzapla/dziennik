package pl.usagi.edziennik.repository

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pl.usagi.edziennik.domain.ActiveStudentManager
import pl.usagi.edziennik.domain.AttendanceExcuseStatus
import pl.usagi.edziennik.domain.AttendanceStatus
import pl.usagi.edziennik.domain.Student
import pl.usagi.edziennik.network.UonetApiClient
import pl.usagi.edziennik.network.attendance.UonetAttendanceDataSource
import java.time.LocalDate

class UonetAttendanceRepositoryTest {
    @Test
    fun fetchesExcusesForSameWeekAndAttachesMatchingLesson() = runTest {
        val requests = mutableListOf<Request>()
        val repository = repository(requests = requests)

        val entries = repository.getAttendance(weekStart, weekEnd)

        assertEquals(AttendanceStatus.Absent, entries.single().status)
        assertEquals(AttendanceExcuseStatus.Pending, entries.single().excuseStatus)
        assertEquals(listOf("Frekwencja", "Usprawiedliwienia"), requests.map {
            it.url.encodedPath.substringAfterLast('/')
        })
        assertEquals(requests[0].url.queryParameter("dataOd"), requests[1].url.queryParameter("dataOd"))
        assertEquals(requests[0].url.queryParameter("dataDo"), requests[1].url.queryParameter("dataDo"))
        assertEquals(requests[0].url.queryParameter("key"), requests[1].url.queryParameter("key"))
    }

    @Test
    fun failedExcusesRequestKeepsBaseAttendance() = runTest {
        val repository = repository(excusesHttpStatus = 503)

        val entries = repository.getAttendance(weekStart, weekEnd)

        assertEquals(1, entries.size)
        assertEquals(AttendanceStatus.Absent, entries.single().status)
        assertNull(entries.single().excuseStatus)
    }

    @Test
    fun failedExcusesAreReportedAsPartialToSynchronization() = runTest {
        val result = repository(excusesHttpStatus = 503).getAttendanceWithCompleteness(weekStart, weekEnd)

        assertEquals(false, result.excusesComplete)
        assertEquals(1, result.entries.size)
        assertNull(result.entries.single().excuseStatus)
    }

    @Test
    fun unmappableEntryDoesNotClaimCompleteAttendance() = runTest {
        val response = """{"oddzialy":[{"idPoraLekcji":1568,"data":"2026-09-15T00:00:00+02:00"}]}"""
        val result = repository(attendanceResponse = response).getAttendanceWithCompleteness(weekStart, weekEnd)

        assertEquals(false, result.entriesComplete)
        assertEquals(emptyList<Any>(), result.entries)
    }

    @Test
    fun unmappableExcuseDoesNotClaimCompleteExcuses() = runTest {
        val response = """{"usprawiedliwienia":[{"status":0,"dzien":"2026-09-15T00:00:00+02:00"}]}"""
        val result = repository(excusesResponse = response).getAttendanceWithCompleteness(weekStart, weekEnd)

        assertEquals(false, result.excusesComplete)
        assertEquals(1, result.entries.size)
        assertNull(result.entries.single().excuseStatus)
    }

    private fun repository(
        requests: MutableList<Request> = mutableListOf(),
        excusesHttpStatus: Int = 200,
        attendanceResponse: String = attendanceBody,
        excusesResponse: String = excusesBody,
    ): UonetAttendanceRepository {
        val studentManager = ActiveStudentManager()
        studentManager.initialize(listOf(
            Student("test-student-key", 1, "Test Student", "4A", "Test School"),
        ))
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                requests += request
                val isExcuses = request.url.encodedPath.endsWith("/Usprawiedliwienia")
                val body = if (isExcuses) excusesResponse else attendanceResponse
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(if (isExcuses) excusesHttpStatus else 200)
                    .message("scripted")
                    .header("Content-Type", "application/json")
                    .body(body.toResponseBody())
                    .build()
            }
            .build()
        val dataSource = UonetAttendanceDataSource(
            UonetApiClient(client, Json { ignoreUnknownKeys = true }),
        )
        return UonetAttendanceRepository(studentManager, dataSource)
    }

    private companion object {
        val weekStart = LocalDate.of(2026, 9, 14)
        val weekEnd = LocalDate.of(2026, 9, 18)
        const val attendanceBody = """
            {"oddzialy":[{
              "idPoraLekcji":1568,
              "numerLekcji":8,
              "kategoriaFrekwencji":2,
              "data":"2026-09-15T00:00:00+02:00",
              "godzinaOd":"2026-09-15T14:05:00+02:00",
              "godzinaDo":"2026-09-15T14:50:00+02:00"
            }]}
        """
        const val excusesBody = """
            {"usprawiedliwienia":[{
              "status":0,
              "dzien":"2026-09-15T00:00:00+02:00",
              "idPoraLekcji":1568,
              "numerLekcji":8
            }]}
        """
    }
}
