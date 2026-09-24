package pl.usagi.edziennik.mapper

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.usagi.edziennik.domain.AttendanceStatus
import pl.usagi.edziennik.dto.AttendanceEntryDto
import pl.usagi.edziennik.dto.AttendanceStatisticsDto
import pl.usagi.edziennik.dto.AttendanceSubjectDto
import java.time.LocalDate
import java.time.LocalTime

class AttendanceMapperTest {
    @Test
    fun mapsSubjectsIncludingAllSubjectsId() {
        val payload = """[{"nazwa":"Wszystkie","id":-1},{"nazwa":"Język polski","id":7867}]"""
        val subjects = Json.decodeFromString(ListSerializer(AttendanceSubjectDto.serializer()), payload)
            .toDomainAttendanceSubjects()

        assertEquals(listOf(-1, 7867), subjects.map { it.id })
        assertEquals(listOf("Wszystkie", "Język polski"), subjects.map { it.name })
    }

    @Test
    fun mapsBackendSummaryCategoriesMonthsAndPeriods() {
        val payload = """
            {
              "podsumowanie": 100.0,
              "statystyki": [
                {
                  "kategoriaFrekwencji": 1,
                  "miesiace": [{"miesiac": 9, "wartosc": 70}, {"miesiac": 10, "wartosc": 30}],
                  "okresy": [70, 30, 0],
                  "razem": 100
                },
                {
                  "kategoriaFrekwencji": 7,
                  "miesiace": [],
                  "okresy": [2],
                  "razem": 2
                }
              ]
            }
        """.trimIndent()

        val result = Json.decodeFromString<AttendanceStatisticsDto>(payload).toDomain()

        assertEquals(100.0, result.summary)
        assertEquals(2, result.categories.size)
        assertEquals(AttendanceStatus.Present, result.categories[0].status)
        assertEquals(100, result.categories[0].total)
        assertEquals(listOf(9, 10), result.categories[0].months.map { it.month })
        assertEquals(listOf(70, 30), result.categories[0].months.map { it.value })
        assertEquals(listOf(70, 30, 0), result.categories[0].periods)
        assertEquals(AttendanceStatus.Released, result.categories[1].status)
        assertEquals(listOf(2), result.categories[1].periods)
    }

    @Test
    fun missingAndUnknownCategoriesRemainSafe() {
        val empty = Json.decodeFromString<AttendanceStatisticsDto>(
            """{"podsumowanie":75.0,"statystyki":[]}""",
        ).toDomain()
        val unknown = Json.decodeFromString<AttendanceStatisticsDto>(
            """{"podsumowanie":null,"statystyki":[{"kategoriaFrekwencji":999,"razem":2}]}""",
        ).toDomain()

        assertTrue(empty.categories.isEmpty())
        assertEquals(AttendanceStatus.Unknown(999), unknown.categories.single().status)
        assertEquals(2, unknown.categories.single().total)
        assertTrue(unknown.categories.single().months.isEmpty())
        assertTrue(unknown.categories.single().periods.isEmpty())
    }

    @Test
    fun mapsAttendanceDtoToDomainEntry() {
        val dto = attendanceDto()

        val entry = dto.toDomainOrNull()

        requireNotNull(entry)
        assertEquals(10, entry.lessonTimeId)
        assertEquals(20, entry.classLessonId)
        assertEquals(2, entry.lessonNumber)
        assertEquals(LocalDate.of(2026, 9, 10), entry.date)
        assertEquals(LocalTime.of(8, 20), entry.startTime)
        assertEquals(LocalTime.of(9, 5), entry.endTime)
        assertEquals("Matematyka", entry.subject)
        assertEquals("Jan Kowalski", entry.teacher)
    }

    @Test
    fun statusOneMapsToPresent() {
        val entry = attendanceDto(attendanceCategory = 1).toDomainOrNull()

        assertEquals(AttendanceStatus.Present, entry?.status)
    }

    @Test
    fun confirmedCategoriesMapToTheirStatuses() {
        val expected = mapOf(
            1 to AttendanceStatus.Present,
            2 to AttendanceStatus.Absent,
            3 to AttendanceStatus.ExcusedAbsence,
            4 to AttendanceStatus.Late,
            5 to AttendanceStatus.ExcusedLate,
            6 to AttendanceStatus.SchoolRelatedAbsence,
            7 to AttendanceStatus.Released,
        )

        expected.forEach { (category, status) ->
            assertEquals(status, attendanceDto(attendanceCategory = category).toDomainOrNull()?.status)
        }
    }

    @Test
    fun unknownStatusKeepsRawValue() {
        val entry = attendanceDto(attendanceCategory = 9).toDomainOrNull()

        assertEquals(AttendanceStatus.Unknown(rawValue = 9), entry?.status)
    }

    @Test
    fun nullableOptionalFieldsDoNotCrash() {
        val entry = attendanceDto(
            lessonTimeId = null,
            classLessonId = null,
            lessonNumber = null,
            subject = null,
            teacher = null,
        ).toDomainOrNull()

        requireNotNull(entry)
        assertNull(entry.lessonTimeId)
        assertNull(entry.classLessonId)
        assertNull(entry.lessonNumber)
        assertNull(entry.subject)
        assertNull(entry.teacher)
    }

    @Test
    fun missingRequiredDateSkipsEntry() {
        val entries = listOf(attendanceDto(date = null)).toDomainAttendance()

        assertTrue(entries.isEmpty())
    }

    private fun attendanceDto(
        lessonTimeId: Int? = 10,
        classLessonId: Int? = 20,
        lessonNumber: Int? = 2,
        attendanceCategory: Int? = 1,
        date: String? = "2026-09-10T00:00:00+02:00",
        timeFrom: String? = "2026-09-10T08:20:00+02:00",
        timeTo: String? = "2026-09-10T09:05:00+02:00",
        subject: String? = "Matematyka",
        teacher: String? = "Jan Kowalski",
    ) = AttendanceEntryDto(
        lessonTimeId = lessonTimeId,
        classLessonId = classLessonId,
        lessonNumber = lessonNumber,
        attendanceCategory = attendanceCategory,
        date = date,
        timeFrom = timeFrom,
        timeTo = timeTo,
        subject = subject,
        teacher = teacher,
    )
}
