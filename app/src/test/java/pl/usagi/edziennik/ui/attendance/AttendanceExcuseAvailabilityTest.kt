package pl.usagi.edziennik.ui.attendance

import org.junit.Assert.assertFalse
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
}
