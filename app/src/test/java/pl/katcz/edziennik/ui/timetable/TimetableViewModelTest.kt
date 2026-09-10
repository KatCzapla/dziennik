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
    }
}
