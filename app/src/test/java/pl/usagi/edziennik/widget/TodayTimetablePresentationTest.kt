package pl.usagi.edziennik.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.usagi.edziennik.domain.changes.SubstitutionSnapshot
import pl.usagi.edziennik.domain.changes.TimetableSlot
import pl.usagi.edziennik.domain.changes.TimetableSnapshot
import pl.usagi.edziennik.persistence.changes.LocalTimetableResult
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class TodayTimetablePresentationTest {
    private val today = LocalDate.of(2026, 10, 7)
    private val zone = ZoneId.of("Europe/Warsaw")

    @Test fun noBaselineShowsNeutralMessage() {
        val presentation = presentTodayTimetable(LocalTimetableResult.NoBaseline, today, zone)
        assertEquals("7 października 2026", presentation.dateLabel)
        assertEquals("Plan nie został jeszcze zsynchronizowany.", presentation.message)
        assertTrue(presentation.lessons.isEmpty())
        assertNull(presentation.lastCompleteLabel)
    }

    @Test fun emptyAvailableDayIsNotNoBaseline() {
        val presentation = presentTodayTimetable(LocalTimetableResult.Available(emptyList(), null), today, zone)
        assertEquals("Dzisiaj nie ma lekcji.", presentation.message)
        assertTrue(presentation.lessons.isEmpty())
    }

    @Test fun lessonsAreOrderedChronologicallyAndOtherDaysAreExcluded() {
        val entries = listOf(lesson(today, 11), lesson(today.plusDays(1), 8), lesson(today, 8))
        val presentation = presentTodayTimetable(LocalTimetableResult.Available(entries, null), today, zone)
        assertEquals(listOf("08:00", "11:00"), presentation.lessons.map { it.startTime })
        assertNull(presentation.message)
    }

    @Test fun roomIsOptional() {
        val entries = listOf(lesson(today, 8, room = "106"), lesson(today, 9, room = null))
        val presentation = presentTodayTimetable(LocalTimetableResult.Available(entries, null), today, zone)
        assertEquals("106", presentation.lessons[0].room)
        assertNull(presentation.lessons[1].room)
    }

    @Test fun cancellationIsMarkedFromSnapshot() {
        val entry = lesson(today, 8).copy(cancelled = true)
        val row = presentTodayTimetable(LocalTimetableResult.Available(listOf(entry), null), today, zone)
            .lessons.single()
        assertTrue(row.cancelled)
        assertFalse(row.substituted)
    }

    @Test fun substitutionIsMarkedFromRecognizedSnapshot() {
        val entry = lesson(today, 8).copy(substitutions = listOf(SubstitutionSnapshot("Zastępca", null, null)))
        val row = presentTodayTimetable(LocalTimetableResult.Available(listOf(entry), null), today, zone)
            .lessons.single()
        assertTrue(row.substituted)
        assertFalse(row.cancelled)
    }

    @Test fun lastCompleteIsShownInLocalPolishTime() {
        val result = LocalTimetableResult.Available(emptyList(), Instant.parse("2026-10-07T08:05:00Z"))
        assertEquals("7 paź, 10:05", presentTodayTimetable(result, today, zone).lastCompleteLabel)
    }

    @Test fun unknownLastCompleteIsNotInvented() {
        val result = LocalTimetableResult.Available(listOf(lesson(today, 8)), null)
        assertNull(presentTodayTimetable(result, today, zone).lastCompleteLabel)
    }

    private fun lesson(date: LocalDate, hour: Int, room: String? = "106") = TimetableSnapshot(
        slot = TimetableSlot(date, LocalTime.of(hour, 0), LocalTime.of(hour, 45)),
        subject = "Matematyka",
        teacher = "Nauczyciel",
        supportingTeacher1 = null,
        supportingTeacher2 = null,
        room = room,
        cancelled = false,
        substitutions = emptyList(),
        firstSupportingTeacherAbsent = false,
    )
}
