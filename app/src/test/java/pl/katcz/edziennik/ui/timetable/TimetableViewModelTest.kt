package pl.katcz.edziennik.ui.timetable

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import pl.katcz.edziennik.MainDispatcherRule
import pl.katcz.edziennik.domain.ActiveStudentManager
import pl.katcz.edziennik.domain.Student
import pl.katcz.edziennik.domain.TimetableEntry
import pl.katcz.edziennik.repository.TimetableRepository
import java.time.LocalDate
import java.time.LocalTime

@OptIn(ExperimentalCoroutinesApi::class)
class TimetableViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun activeStudentChangeReloadsTimetable() = runTest {
        val activeStudentManager = ActiveStudentManager()
        val repository = FakeTimetableRepository()
        val viewModel = TimetableViewModel(repository, activeStudentManager)

        activeStudentManager.initialize(listOf(firstStudent, secondStudent))
        activeStudentManager.setActiveStudent(firstStudent)
        advanceUntilIdle()
        activeStudentManager.setActiveStudent(secondStudent)
        advanceUntilIdle()

        assertEquals(2, repository.requests.size)
    }

    @Test
    fun emptyRepositoryResponseIsEmptyStateNotError() = runTest {
        val activeStudentManager = ActiveStudentManager()
        val repository = FakeTimetableRepository()
        val viewModel = TimetableViewModel(repository, activeStudentManager)

        activeStudentManager.initialize(listOf(firstStudent))
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isEmpty)
        assertEquals(null, viewModel.uiState.value.errorMessage)
    }

    @Test
    fun changingDayInsideLoadedWeekDoesNotReloadTimetable() = runTest {
        val activeStudentManager = ActiveStudentManager()
        val repository = FakeTimetableRepository()
        val viewModel = TimetableViewModel(repository, activeStudentManager)

        activeStudentManager.initialize(listOf(firstStudent))
        advanceUntilIdle()
        val currentWeek = viewModel.uiState.value.displayedWeek

        viewModel.selectDate(currentWeek.start.plusDays(2))
        advanceUntilIdle()

        assertEquals(1, repository.requests.size)
    }

    @Test
    fun changingToNextWeekReloadsTimetable() = runTest {
        val activeStudentManager = ActiveStudentManager()
        val repository = FakeTimetableRepository()
        val viewModel = TimetableViewModel(repository, activeStudentManager)

        activeStudentManager.initialize(listOf(firstStudent))
        advanceUntilIdle()
        viewModel.selectDate(viewModel.uiState.value.displayedWeek.next().start)
        advanceUntilIdle()

        assertEquals(2, repository.requests.size)
    }

    @Test
    fun previousWeekKeepsCorrespondingSchoolDayAndReloadsTimetable() = runTest {
        val activeStudentManager = ActiveStudentManager()
        val repository = FakeTimetableRepository()
        val viewModel = TimetableViewModel(repository, activeStudentManager)

        activeStudentManager.initialize(listOf(firstStudent))
        advanceUntilIdle()
        val selectedDate = viewModel.uiState.value.selectedDate
        viewModel.previousWeek()
        advanceUntilIdle()

        assertEquals(selectedDate.minusWeeks(1), viewModel.uiState.value.selectedDate)
        assertEquals(2, repository.requests.size)
    }

    @Test
    fun nextWeekKeepsCorrespondingSchoolDayAndReloadsTimetable() = runTest {
        val activeStudentManager = ActiveStudentManager()
        val repository = FakeTimetableRepository()
        val viewModel = TimetableViewModel(repository, activeStudentManager)

        activeStudentManager.initialize(listOf(firstStudent))
        advanceUntilIdle()
        val selectedDate = viewModel.uiState.value.selectedDate
        viewModel.nextWeek()
        advanceUntilIdle()

        assertEquals(selectedDate.plusWeeks(1), viewModel.uiState.value.selectedDate)
        assertEquals(2, repository.requests.size)
    }

    @Test
    fun activeStudentChangeReloadsDisplayedWeekWithoutChangingSelectedDate() = runTest {
        val activeStudentManager = ActiveStudentManager()
        val repository = FakeTimetableRepository()
        val viewModel = TimetableViewModel(repository, activeStudentManager)

        activeStudentManager.initialize(listOf(firstStudent, secondStudent))
        activeStudentManager.setActiveStudent(firstStudent)
        advanceUntilIdle()
        val selectedDate = viewModel.uiState.value.displayedWeek.start.plusDays(2)
        viewModel.selectDate(selectedDate)
        advanceUntilIdle()
        activeStudentManager.setActiveStudent(secondStudent)
        advanceUntilIdle()

        assertEquals(selectedDate, viewModel.uiState.value.selectedDate)
        assertEquals(2, repository.requests.size)
    }

    @Test
    fun uiStateFiltersEntriesForSelectedDate() {
        val selectedDate = LocalDate.of(2026, 9, 9)
        val state = TimetableUiState(
            selectedDate = selectedDate,
            entries = listOf(
                timetableEntry(date = selectedDate.minusDays(1)),
                timetableEntry(date = selectedDate),
            ),
        )

        assertEquals(listOf(timetableEntry(date = selectedDate)), state.selectedDayEntries)
    }

    private class FakeTimetableRepository : TimetableRepository {
        val requests = mutableListOf<Pair<LocalDate, LocalDate>>()

        override suspend fun getTimetable(from: LocalDate, to: LocalDate): List<TimetableEntry> {
            requests += from to to
            return emptyList()
        }
    }

    private companion object {
        val firstStudent = Student(
            key = "first-key",
            diaryId = 1,
            name = "Jan Kowalski",
            className = "4A",
            schoolName = "Szkola",
        )

        val secondStudent = Student(
            key = "second-key",
            diaryId = 2,
            name = "Anna Nowak",
            className = "2B",
            schoolName = "Szkola",
        )

        fun timetableEntry(date: LocalDate) = TimetableEntry(
            date = date,
            lessonNumber = null,
            startTime = LocalTime.of(8, 0),
            endTime = LocalTime.of(8, 45),
            subject = "Matematyka",
            teacher = "Jan Kowalski",
            room = "14",
            changes = emptyList(),
        )
    }
}
