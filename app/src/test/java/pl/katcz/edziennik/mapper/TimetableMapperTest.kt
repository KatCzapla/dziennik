package pl.katcz.edziennik.mapper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.katcz.edziennik.dto.TimetableChangeDto
import pl.katcz.edziennik.dto.TimetableEntryDto
import java.time.LocalDate
import java.time.LocalTime

class TimetableMapperTest {
    @Test
    fun mapsTimetableDtoToDomainEntry() {
        val dto = TimetableEntryDto(
            date = "2026-09-10T00:00:00+02:00",
            timeFrom = "2026-09-10T08:00:00+02:00",
            timeTo = "2026-09-10T08:45:00+02:00",
            teacher = "Jan Kowalski",
            subject = "Matematyka",
            room = "12",
            changes = listOf(
                TimetableChangeDto(
                    changeType = 7,
                    lessonNumber = 1,
                    timeFrom = "2026-09-10T08:00:00+02:00",
                    timeTo = "2026-09-10T08:45:00+02:00",
                    teacher = "Anna Nowak",
                ),
            ),
        )

        val entry = dto.toDomain()

        assertEquals(LocalDate.of(2026, 9, 10), entry.date)
        assertEquals(LocalTime.of(8, 0), entry.startTime)
        assertEquals(LocalTime.of(8, 45), entry.endTime)
        assertEquals("Matematyka", entry.subject)
        assertEquals("Jan Kowalski", entry.teacher)
        assertEquals("12", entry.room)
        assertTrue(entry.changes.single().isSubstitution)
    }

    @Test
    fun emptyStringsBecomeNullsLikePythonPoc() {
        val dto = TimetableEntryDto(
            date = "2026-09-10T00:00:00",
            timeFrom = "2026-09-10T08:00:00",
            timeTo = "2026-09-10T08:45:00",
            teacher = "",
            subject = "Matematyka",
            room = "",
        )

        val entry = dto.toDomain()

        assertNull(entry.teacher)
        assertNull(entry.room)
    }

    @Test
    fun emptyTimetableResponseMapsToEmptyList() {
        assertTrue(emptyList<TimetableEntryDto>().toDomainTimetable().isEmpty())
    }
}
