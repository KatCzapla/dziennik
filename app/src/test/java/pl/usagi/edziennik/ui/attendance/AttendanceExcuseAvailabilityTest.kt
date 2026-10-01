package pl.usagi.edziennik.ui.attendance

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.usagi.edziennik.domain.AttendanceEntry
import pl.usagi.edziennik.domain.AttendanceExcuseStatus
import pl.usagi.edziennik.domain.AttendanceStatus
import java.time.LocalDate
import java.time.LocalTime

class AttendanceExcuseAvailabilityTest {
    private val entry = AttendanceEntry(
        lessonTimeId = 1568,
        classLessonId = 10,
        lessonNumber = 2,
        status = AttendanceStatus.Absent,
        date = LocalDate.of(2026, 9, 15),
        startTime = LocalTime.of(8, 0),
        endTime = LocalTime.of(8, 45),
        subject = "Matematyka",
        teacher = null,
    )

    private fun state(item: AttendanceEntry = entry) = AttendanceUiState(
        selectedDate = item.date,
        today = item.date,
        weekEntries = listOf(item), excusesActive = true, excusesComplete = true,
    )

    @Test fun absentCanBeExcused() = assertTrue(state().canSubmitExcuse(entry))

    @Test fun lateCanBeExcused() {
        val late = entry.copy(status = AttendanceStatus.Late)
        assertTrue(state(late).canSubmitExcuse(late))
    }

    @Test fun presentCannotBeExcused() {
        val present = entry.copy(status = AttendanceStatus.Present)
        assertFalse(state(present).canSubmitExcuse(present))
    }

    @Test fun alreadySubmittedOrApprovedCannotBeExcused() {
        listOf(AttendanceExcuseStatus.Pending, AttendanceExcuseStatus.Approved).forEach { status ->
            val excused = entry.copy(excuseStatus = status)
            assertFalse(state(excused).canSubmitExcuse(excused))
        }
    }

    @Test fun inactiveOrIncompleteExcusesDisableAction() {
        assertFalse(state().copy(excusesActive = false).canSubmitExcuse(entry))
        assertFalse(state().copy(excusesActive = null).canSubmitExcuse(entry))
        assertFalse(state().copy(excusesComplete = false).canSubmitExcuse(entry))
    }

    @Test fun missingLessonIdAndLocallySubmittedLessonDisableAction() {
        val withoutId = entry.copy(lessonTimeId = null)
        assertFalse(state(withoutId).canSubmitExcuse(withoutId))
        assertFalse(state().copy(submittedExcuseKeys = setOf(entry.date to 1568)).canSubmitExcuse(entry))
    }

    @Test fun headerAppearsOnlyInJournalWhenOptionsExist() {
        assertTrue(state().showHeaderExcuseButton)
        assertFalse(state().copy(view = AttendanceView.STATISTICS).showHeaderExcuseButton)
        assertFalse(state(entry.copy(status = AttendanceStatus.Present)).showHeaderExcuseButton)
        assertFalse(state().copy(excusesActive = false).showHeaderExcuseButton)
    }

    @Test fun optionsContainOnlyEligibleLessons() {
        val late = entry.copy(lessonTimeId = 2, status = AttendanceStatus.Late, subject = "Polski")
        val present = entry.copy(lessonTimeId = 3, status = AttendanceStatus.Present)
        val pending = entry.copy(lessonTimeId = 4, excuseStatus = AttendanceExcuseStatus.Pending)
        val released = entry.copy(lessonTimeId = 5, status = AttendanceStatus.Released)
        val schoolRelated = entry.copy(lessonTimeId = 6, status = AttendanceStatus.SchoolRelatedAbsence)
        val data = state().copy(weekEntries = listOf(entry, late, present, pending, released, schoolRelated))

        assertEquals(listOf(entry, late), data.availableExcuseOptions.filterIsInstance<ExcuseOption.Lesson>()
            .map { it.entry })
        assertFalse(data.availableExcuseOptions.any { it is ExcuseOption.WholeDay })
    }

    @Test fun excusedAbsenceAndExcusedLateAreNotOptions() {
        listOf(AttendanceStatus.ExcusedAbsence, AttendanceStatus.ExcusedLate).forEach { status ->
            val item = entry.copy(status = status)
            assertFalse(state(item).showHeaderExcuseButton)
        }
    }

    @Test fun wholeDayIsAvailableForFutureDayWithoutAttendanceEntries() {
        val future = entry.date.plusDays(1)
        val data = state().copy(today = entry.date, selectedDate = future, weekEntries = emptyList())

        assertEquals(listOf(ExcuseOption.WholeDay(future)), data.availableExcuseOptions)
        assertTrue(data.showHeaderExcuseButton)
        assertFalse(data.copy(excusesActive = false).showHeaderExcuseButton)
        assertFalse(data.copy(submittedExcuseKeys = setOf(future to null)).showHeaderExcuseButton)
    }

    @Test fun pastDayWithoutEntriesHasNoWholeDayOption() {
        val data = state().copy(selectedDate = entry.date.minusDays(1), weekEntries = emptyList())
        assertFalse(data.showHeaderExcuseButton)
    }
}
