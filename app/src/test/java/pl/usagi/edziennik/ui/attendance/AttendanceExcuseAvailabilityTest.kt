package pl.usagi.edziennik.ui.attendance

import kotlinx.serialization.json.Json
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.usagi.edziennik.domain.AttendanceEntry
import pl.usagi.edziennik.domain.AttendanceExcuseStatus
import pl.usagi.edziennik.domain.AttendanceStatus
import pl.usagi.edziennik.dto.AttendanceExcusesResponseDto
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
        today = LocalDate.of(2026, 9, 18),
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

    @Test fun headerUsesDisplayedWeekNotSelectedDay() {
        val friday = LocalDate.of(2026, 9, 18)
        val data = state().copy(selectedDate = friday)

        assertTrue(data.selectedDayEntries.isEmpty())
        assertTrue(ExcuseOption.Lesson(entry) in data.availableExcuseOptions)
        assertTrue(data.showHeaderExcuseButton)
    }

    @Test fun visibleDaysSkipDaysWithoutAvailableOptions() {
        val present = entry.copy(date = entry.date.plusDays(1), status = AttendanceStatus.Present)
        val data = state().copy(weekEntries = listOf(entry, present))

        assertEquals(listOf(entry.date), visibleExcuseDays(data).map { it.first })
        assertEquals(listOf(ExcuseOption.WholeDay(entry.date), ExcuseOption.Lesson(entry)),
            visibleExcuseDays(data).single().second)
    }

    @Test fun visibleDaysAreEmptyWhenWeekHasNoOptions() {
        val data = state(entry.copy(status = AttendanceStatus.Present))

        assertTrue(visibleExcuseDays(data).isEmpty())
        assertFalse(data.canSubmitSelectedExcuses)
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

        assertTrue(ExcuseOption.WholeDay(future) in data.availableExcuseOptions)
        assertTrue(data.showHeaderExcuseButton)
        assertFalse(data.copy(excusesActive = false).showHeaderExcuseButton)
        assertFalse(ExcuseOption.WholeDay(future) in
            data.copy(submittedExcuseKeys = setOf(future to null)).availableExcuseOptions)
    }

    @Test fun futureWeekdaysCanOfferWholeDay() {
        val monday = LocalDate.of(2026, 9, 21)
        (0L..4L).forEach { offset ->
            val date = monday.plusDays(offset)
            val data = state().copy(today = entry.date, selectedDate = date, weekEntries = emptyList())

            assertTrue(ExcuseOption.WholeDay(date) in data.availableExcuseOptions)
            assertTrue(data.showHeaderExcuseButton)
        }
    }

    @Test fun saturdayDoesNotOfferWholeDayOrHeaderWithoutLesson() {
        val saturday = LocalDate.of(2026, 9, 19)
        val data = state().copy(selectedDate = saturday, weekEntries = emptyList())

        assertTrue(data.availableExcuseOptions.isEmpty())
        assertFalse(data.showHeaderExcuseButton)
    }

    @Test fun sundayDoesNotOfferWholeDayOrHeaderWithoutLesson() {
        val sunday = LocalDate.of(2026, 9, 20)
        val data = state().copy(selectedDate = sunday, weekEntries = emptyList())

        assertTrue(data.availableExcuseOptions.isEmpty())
        assertFalse(data.showHeaderExcuseButton)
    }

    @Test fun eligibleWeekendLessonRemainsAvailable() {
        val saturdayEntry = entry.copy(date = LocalDate.of(2026, 9, 19))
        val data = state(saturdayEntry)

        assertEquals(listOf(ExcuseOption.Lesson(saturdayEntry)), data.availableExcuseOptions)
        assertTrue(data.showHeaderExcuseButton)
    }

    @Test fun parentReportingFlagDoesNotAffectExistingExcuses() {
        val response = Json { ignoreUnknownKeys = true }.decodeFromString<AttendanceExcusesResponseDto>(
            """{"usprawiedliwieniaAktywne":true,"rodzicMozeZglaszac":false,"usprawiedliwienia":[]}"""
        )
        val monday = LocalDate.of(2026, 9, 21)
        val data = state().copy(
            today = entry.date,
            selectedDate = monday,
            weekEntries = emptyList(),
            excusesActive = response.active,
        )

        assertTrue(ExcuseOption.WholeDay(monday) in data.availableExcuseOptions)
    }

    @Test fun pastDayWithoutEntriesHasNoWholeDayOption() {
        val data = state().copy(selectedDate = entry.date.minusDays(1), weekEntries = emptyList())
        assertFalse(data.showHeaderExcuseButton)
    }
}
