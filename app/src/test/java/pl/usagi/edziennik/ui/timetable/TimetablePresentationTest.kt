package pl.usagi.edziennik.ui.timetable

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.usagi.edziennik.domain.TimetableChange
import pl.usagi.edziennik.domain.TimetableChangeKind
import pl.usagi.edziennik.domain.TimetableEntry
import pl.usagi.edziennik.domain.time.WeekRange
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

class TimetablePresentationTest {
    @Test
    fun schoolWeekLabelUsesMondayToFridayRange() {
        val week = WeekRange.fromDate(LocalDate.of(2026, 9, 10))

        assertEquals("7–11 września", week.schoolWeekLabel())
    }

    @Test
    fun schoolWeekLabelHandlesMonthBoundary() {
        val week = WeekRange.fromDate(LocalDate.of(2026, 10, 1))

        assertEquals("28 września – 2 października", week.schoolWeekLabel())
    }

    @Test
    fun schoolWeekLabelHandlesYearBoundary() {
        val week = WeekRange.fromDate(LocalDate.of(2026, 12, 30))

        assertEquals("28 grudnia 2026 – 1 stycznia 2027", week.schoolWeekLabel())
    }

    @Test
    fun schoolDaysExcludeSaturdayAndSunday() {
        val days = WeekRange.fromDate(LocalDate.of(2026, 9, 10)).schoolDays()

        assertEquals(
            listOf(
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY,
            ),
            days.map { it.dayOfWeek },
        )
    }

    @Test
    fun initialDateKeepsMonday() {
        val monday = LocalDate.of(2026, 9, 7)

        assertEquals(monday, initialTimetableDate(monday))
    }

    @Test
    fun initialDateKeepsFriday() {
        val friday = LocalDate.of(2026, 9, 11)

        assertEquals(friday, initialTimetableDate(friday))
    }

    @Test
    fun initialDateMovesSaturdayToNextMonday() {
        val saturday = LocalDate.of(2026, 9, 12)

        assertEquals(LocalDate.of(2026, 9, 14), initialTimetableDate(saturday))
    }

    @Test
    fun initialDateMovesSundayToNextMonday() {
        val sunday = LocalDate.of(2026, 9, 13)

        assertEquals(LocalDate.of(2026, 9, 14), initialTimetableDate(sunday))
    }

    @Test
    fun previousSchoolDateFromMondayIsPreviousFriday() {
        val monday = LocalDate.of(2026, 9, 14)

        assertEquals(LocalDate.of(2026, 9, 11), previousSchoolDate(monday))
    }

    @Test
    fun nextSchoolDateFromFridayIsNextMonday() {
        val friday = LocalDate.of(2026, 9, 11)

        assertEquals(LocalDate.of(2026, 9, 14), nextSchoolDate(friday))
    }

    @Test
    fun schoolDateOffsetSkipsWeekend() {
        val friday = LocalDate.of(2026, 9, 11)
        val monday = LocalDate.of(2026, 9, 14)

        assertEquals(1, schoolDateOffset(friday, monday))
        assertEquals(monday, schoolDateAtOffset(friday, 1))
    }

    @Test
    fun entryWithoutApiLessonNumberDoesNotFallbackToListIndexOrStartTime() {
        val entry = timetableEntry(
            startTime = LocalTime.of(8, 20),
            lessonNumber = null,
        )

        assertNull(entry.displayLessonNumber())
    }

    @Test
    fun displayLessonNumberUsesValueConfirmedByChangeDto() {
        val entry = timetableEntry(
            lessonNumber = null,
            changes = listOf(timetableChange(lessonNumber = 2)),
        )

        assertEquals(2, entry.displayLessonNumber())
    }

    @Test
    fun teacherAbsenceNoteShowsConfirmedSupportingTeacherTextAndEffect() {
        val note = timetableChange(
            kind = TimetableChangeKind.FirstSupportingTeacherAbsence,
            teacherType = 1,
            absenceInformation = "brak",
        ).readableNote()

        assertEquals("Nieobecny nauczyciel wspomagający\nSkutek nieobecności: brak", note)
    }

    @Test
    fun teacherAbsenceNoteOmitsBlankEffect() {
        val note = timetableChange(
            kind = TimetableChangeKind.FirstSupportingTeacherAbsence,
            teacherType = 1,
            absenceInformation = "",
        ).readableNote()

        assertEquals("Nieobecny nauczyciel wspomagający", note)
    }

    @Test
    fun cancelledEntryHasPriorityRegardlessOfChangeOrder() {
        val entry = timetableEntry(
            changes = listOf(
                timetableChange(
                    kind = TimetableChangeKind.FirstSupportingTeacherAbsence,
                    teacherType = 1,
                    absenceInformation = "brak",
                ),
                timetableChange(
                    kind = TimetableChangeKind.Cancelled,
                    changeType = 4,
                    absenceInformation = "wyjazd integracyjny",
                ),
            ),
        )

        assertTrue(entry.isCancelled)
        assertEquals("wyjazd integracyjny", entry.cancellationReason)
    }

    private fun timetableEntry(
        startTime: LocalTime = LocalTime.of(8, 0),
        lessonNumber: Int? = null,
        changes: List<TimetableChange> = emptyList(),
    ) = TimetableEntry(
        date = LocalDate.of(2026, 9, 10),
        lessonNumber = lessonNumber,
        startTime = startTime,
        endTime = startTime.plusMinutes(45),
        subject = "Matematyka",
        teacher = "Jan Kowalski",
        room = "14",
        changes = changes,
    )

    private fun timetableChange(
        lessonNumber: Int? = null,
        changeType: Int = 7,
        kind: TimetableChangeKind = TimetableChangeKind.Substitution,
        teacherType: Int? = null,
        absenceInformation: String? = null,
    ) = TimetableChange(
        changeType = changeType,
        kind = kind,
        teacherType = teacherType,
        day = null,
        lessonNumber = lessonNumber,
        startTime = null,
        endTime = null,
        group = null,
        subject = null,
        teacher = "Anna Nowak",
        room = null,
        absenceInformation = absenceInformation,
    )
}
