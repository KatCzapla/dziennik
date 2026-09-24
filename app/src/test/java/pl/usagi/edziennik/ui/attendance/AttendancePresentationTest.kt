package pl.usagi.edziennik.ui.attendance

import org.junit.Assert.assertEquals
import org.junit.Test
import pl.usagi.edziennik.domain.AttendanceStatus
import java.time.LocalDate

class AttendancePresentationTest {
    @Test
    fun confirmedStatusesHaveReadableLabels() {
        val expected = mapOf(
            AttendanceStatus.Present to "Obecność",
            AttendanceStatus.Absent to "Nieobecność",
            AttendanceStatus.ExcusedAbsence to "Nieobecność usprawiedliwiona",
            AttendanceStatus.Late to "Spóźnienie",
            AttendanceStatus.ExcusedLate to "Spóźnienie usprawiedliwione",
            AttendanceStatus.SchoolRelatedAbsence to "Nieobecność z przyczyn szkolnych",
            AttendanceStatus.Released to "Zwolnienie",
        )

        expected.forEach { (status, label) ->
            assertEquals(label, status.label())
        }
    }

    @Test
    fun unknownStatusShowsRawValue() {
        assertEquals("Status: 999", AttendanceStatus.Unknown(999).label())
    }

    @Test
    fun initialDateKeepsMonday() {
        val monday = LocalDate.of(2026, 9, 7)

        assertEquals(monday, initialAttendanceDate(monday))
    }

    @Test
    fun initialDateKeepsWednesday() {
        val wednesday = LocalDate.of(2026, 9, 9)

        assertEquals(wednesday, initialAttendanceDate(wednesday))
    }

    @Test
    fun initialDateKeepsFriday() {
        val friday = LocalDate.of(2026, 9, 11)

        assertEquals(friday, initialAttendanceDate(friday))
    }

    @Test
    fun initialDateMovesSaturdayToPreviousFriday() {
        val saturday = LocalDate.of(2026, 9, 12)

        assertEquals(LocalDate.of(2026, 9, 11), initialAttendanceDate(saturday))
    }

    @Test
    fun initialDateMovesSundayToPreviousFriday() {
        val sunday = LocalDate.of(2026, 9, 13)

        assertEquals(LocalDate.of(2026, 9, 11), initialAttendanceDate(sunday))
    }

    @Test
    fun fridayNextDayIsNextMonday() {
        val friday = LocalDate.of(2026, 9, 11)

        assertEquals(LocalDate.of(2026, 9, 14), attendanceDateAtOffset(friday, 1))
    }

    @Test
    fun mondayPreviousDayIsPreviousFriday() {
        val monday = LocalDate.of(2026, 9, 14)

        assertEquals(LocalDate.of(2026, 9, 11), attendanceDateAtOffset(monday, -1))
    }

    @Test
    fun offsetSkipsWeekend() {
        val friday = LocalDate.of(2026, 9, 11)
        val monday = LocalDate.of(2026, 9, 14)

        assertEquals(1, attendanceDateOffset(friday, monday))
        assertEquals(-1, attendanceDateOffset(monday, friday))
    }
}
