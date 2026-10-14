package pl.usagi.edziennik.repository

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
        assertEquals(503, (result.excusesFailure as pl.usagi.edziennik.network.attendance.AttendanceFetchException.Http).statusCode)
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

    @Test
    fun submitSingleLessonUsesActiveStudentKeyAndDate() = runTest {
        val requests = mutableListOf<Request>()
        val repository = repository(requests = requests)

        repository.submitExcuse(LocalDate.of(2026, 9, 15), 1568, "Proszę usprawiedliwić")

        val request = requests.single()
        val body = Json.parseToJsonElement(request.body!!.let { body ->
            okio.Buffer().also { body.writeTo(it) }.readUtf8()
        }).jsonObject
        assertEquals("POST", request.method)
        assertEquals("Usprawiedliwienia", request.url.encodedPath.substringAfterLast('/'))
        assertNull(request.url.queryParameter("key"))
        assertEquals("test-student-key", body.getValue("key").jsonPrimitive.content)
        assertEquals("Proszę usprawiedliwić", body.getValue("tresc").jsonPrimitive.content)
        val lesson = body.getValue("usprawiedliwienia").jsonArray.single().jsonObject
        assertEquals("1568", lesson.getValue("idPoraLekcji").jsonPrimitive.content)
        assertEquals("2026-09-15T00:00:00+02:00", lesson.getValue("dzien").jsonPrimitive.content)
    }

    @Test
    fun submitWholeDaySendsExplicitNullLessonTimeId() = runTest {
        val requests = mutableListOf<Request>()
        repository(requests = requests).submitExcuse(LocalDate.of(2026, 9, 16), null, "")

        val body = Json.parseToJsonElement(
            okio.Buffer().also { requests.single().body!!.writeTo(it) }.readUtf8(),
        ).jsonObject
        val lesson = body.getValue("usprawiedliwienia").jsonArray.single().jsonObject
        assertTrue(lesson.containsKey("idPoraLekcji"))
        assertEquals("null", lesson.getValue("idPoraLekcji").toString())
        assertEquals("2026-09-16T00:00:00+02:00", lesson.getValue("dzien").jsonPrimitive.content)
    }

    @Test
    fun submitMultipleLessonsUsesOnePost() = runTest {
        val requests = mutableListOf<Request>()
        repository(requests = requests).submitExcuses(
            listOf(
                AttendanceExcuseRequest(LocalDate.of(2026, 9, 15), 1568),
                AttendanceExcuseRequest(LocalDate.of(2026, 9, 16), 1600),
            ),
            "Wspólny powód",
        )

        assertEquals(1, requests.size)
        val body = Json.parseToJsonElement(
            okio.Buffer().also { requests.single().body!!.writeTo(it) }.readUtf8(),
        ).jsonObject
        assertEquals("Wspólny powód", body.getValue("tresc").jsonPrimitive.content)
        assertEquals(
            listOf("1568", "1600"),
            body.getValue("usprawiedliwienia").jsonArray.map {
                it.jsonObject.getValue("idPoraLekcji").jsonPrimitive.content
            },
        )
        assertEquals(
            listOf("2026-09-15T00:00:00+02:00", "2026-09-16T00:00:00+02:00"),
            body.getValue("usprawiedliwienia").jsonArray.map {
                it.jsonObject.getValue("dzien").jsonPrimitive.content
            },
        )
    }

    @Test
    fun submitAllowsEmptyAndExactly256Characters() = runTest {
        val requests = mutableListOf<Request>()
        val repository = repository(requests = requests)

        repository.submitExcuse(weekStart, 12, "")
        repository.submitExcuse(weekStart, 12, "a".repeat(256))

        val bodies = requests.map { request ->
            Json.parseToJsonElement(okio.Buffer().also { request.body!!.writeTo(it) }.readUtf8())
                .jsonObject.getValue("tresc").jsonPrimitive.content
        }
        assertEquals(listOf("", "a".repeat(256)), bodies)
    }

    @Test
    fun getExposesActiveFlagWithoutInterpretingBlockade() = runTest {
        val result = repository(excusesResponse = """{"usprawiedliwieniaAktywne":false,"usprawiedliwienia":[]}""")
            .getAttendanceWithCompleteness(weekStart, weekEnd)

        assertEquals(false, result.excusesActive)
        assertEquals(true, result.excusesComplete)
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
            UonetApiClient(client, Json { ignoreUnknownKeys = true; explicitNulls = false }),
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
            {"usprawiedliwieniaAktywne":true,"usprawiedliwienia":[{
              "status":0,
              "dzien":"2026-09-15T00:00:00+02:00",
              "idPoraLekcji":1568,
              "numerLekcji":8
            }]}
        """
    }
}
