package pl.usagi.edziennik.mapper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.usagi.edziennik.domain.AttendanceStatus
import pl.usagi.edziennik.dto.AttendanceEntryDto
import java.time.LocalDate
import java.time.LocalTime

class AttendanceMapperTest {
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
