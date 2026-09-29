package pl.usagi.edziennik.ui.schooltasks

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import pl.usagi.edziennik.MainDispatcherRule
import pl.usagi.edziennik.domain.ActiveStudentManager
import pl.usagi.edziennik.domain.SchoolTask
import pl.usagi.edziennik.domain.SchoolTaskDetails
import pl.usagi.edziennik.domain.SchoolTaskType
import pl.usagi.edziennik.domain.Student
import pl.usagi.edziennik.repository.SchoolTasksRepository
import pl.usagi.edziennik.ui.navigation.SchoolTaskNavigationTarget
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class SchoolTasksViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun homeworkAndTestNavigationLoadTargetWeekAndKeepExistingDetails() = runTest {
        for (rawType in listOf(4, 2)) {
            val date = LocalDate.of(2026, 9, 22)
            val manager = ActiveStudentManager()
            val repository = FakeSchoolTasksRepository(manager)
            repository.response = listOf(schoolTask(date, description = "Opis szczegółów").copy(
                id = 72,
                rawType = rawType,
                type = if (rawType == 4) SchoolTaskType.Homework else SchoolTaskType.Quiz,
            ))
            val viewModel = SchoolTasksViewModel(repository, manager, fixedClock(), SavedStateHandle(mapOf(
                "taskId" to "72", "taskType" to rawType.toString(), "taskDate" to date.toString(),
            )))
            manager.initialize(listOf(firstStudent))
            advanceUntilIdle()

            assertEquals(listOf(Request("first-key", LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 25))), repository.requests)
            val state = viewModel.uiState.value
            assertEquals(SchoolTaskNavigationTarget(72, rawType, date), state.navigationTarget)
            assertEquals(1, state.navigationTarget?.let(state.dayGroups::targetItemIndex))
            assertEquals("Opis szczegółów", state.weekTasks.single().details?.description)
            viewModel.navigationTargetShown()
            assertEquals(null, viewModel.uiState.value.navigationTarget)
        }
    }

    @Test
    fun initialLoadRequestsCurrentWeekInsideSchoolYear() = runTest {
        val activeStudentManager = ActiveStudentManager()
        val repository = FakeSchoolTasksRepository(activeStudentManager)
        val viewModel = SchoolTasksViewModel(repository, activeStudentManager, fixedClock())

        activeStudentManager.initialize(listOf(firstStudent))
        advanceUntilIdle()

        assertEquals(listOf(Request("first-key", LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 11))), repository.requests)
        assertEquals(false, viewModel.uiState.value.isLoading)
        assertEquals(null, viewModel.uiState.value.navigationTarget)
    }

    @Test
    fun repositoryResponseIsFilteredToDisplayedSchoolWeek() = runTest {
        val activeStudentManager = ActiveStudentManager()
        val repository = FakeSchoolTasksRepository(activeStudentManager).apply {
            response = listOf(
                schoolTask(date = LocalDate.of(2026, 9, 8)),
                schoolTask(date = LocalDate.of(2026, 9, 14)),
            )
        }
        val viewModel = SchoolTasksViewModel(repository, activeStudentManager, fixedClock())

        activeStudentManager.initialize(listOf(firstStudent))
        advanceUntilIdle()

        assertEquals(listOf(LocalDate.of(2026, 9, 8)), viewModel.uiState.value.weekTasks.map { it.date })
        assertEquals(listOf(LocalDate.of(2026, 9, 8)), viewModel.uiState.value.dayGroups.map { it.date })
    }

    @Test
    fun activeStudentChangeReloadsUsingNewStudent() = runTest {
        val activeStudentManager = ActiveStudentManager()
        val repository = FakeSchoolTasksRepository(activeStudentManager)
        val viewModel = SchoolTasksViewModel(repository, activeStudentManager, fixedClock())

        activeStudentManager.initialize(listOf(firstStudent, secondStudent))
        activeStudentManager.setActiveStudent(firstStudent)
        advanceUntilIdle()
        activeStudentManager.setActiveStudent(secondStudent)
        advanceUntilIdle()

        assertEquals(listOf("first-key", "second-key"), repository.requests.map { it.studentKey })
        assertEquals(secondStudent, viewModel.uiState.value.activeStudent)
    }

    @Test
    fun previousAndNextWeekKeepCorrespondingSchoolDay() = runTest {
        val activeStudentManager = ActiveStudentManager()
        val repository = FakeSchoolTasksRepository(activeStudentManager)
        val viewModel = SchoolTasksViewModel(repository, activeStudentManager, fixedClock())

        activeStudentManager.initialize(listOf(firstStudent))
        advanceUntilIdle()
        val selectedDate = viewModel.uiState.value.selectedDate
        viewModel.previousWeek()
        advanceUntilIdle()
        assertEquals(selectedDate.minusWeeks(1), viewModel.uiState.value.selectedDate)

        viewModel.nextWeek()
        advanceUntilIdle()
        assertEquals(selectedDate, viewModel.uiState.value.selectedDate)
    }

    @Test
    fun changingWeekDoesNotKeepOldDetails() = runTest {
        val activeStudentManager = ActiveStudentManager()
        val repository = FakeSchoolTasksRepository(activeStudentManager).apply {
            responseByRange[LocalDate.of(2026, 9, 7) to LocalDate.of(2026, 9, 11)] = listOf(
                schoolTask(
                    date = LocalDate.of(2026, 9, 9),
                    description = "Stary tydzień",
                ),
            )
            responseByRange[LocalDate.of(2026, 9, 14) to LocalDate.of(2026, 9, 18)] = listOf(
                schoolTask(
                    date = LocalDate.of(2026, 9, 16),
                    description = "Nowy tydzień",
                ),
            )
        }
        val viewModel = SchoolTasksViewModel(repository, activeStudentManager, fixedClock())

        activeStudentManager.initialize(listOf(firstStudent))
        advanceUntilIdle()
        viewModel.nextWeek()
        advanceUntilIdle()

        assertEquals(listOf("Nowy tydzień"), viewModel.uiState.value.weekTasks.map { it.details?.description })
    }

    @Test
    fun activeStudentChangeDoesNotReusePreviousStudentDetails() = runTest {
        val activeStudentManager = ActiveStudentManager()
        val repository = FakeSchoolTasksRepository(activeStudentManager).apply {
            responseByStudent["first-key"] = listOf(
                schoolTask(
                    date = LocalDate.of(2026, 9, 9),
                    description = "Pierwszy uczeń",
                ),
            )
            responseByStudent["second-key"] = listOf(
                schoolTask(
                    date = LocalDate.of(2026, 9, 9),
                    description = "Drugi uczeń",
                ),
            )
        }
        val viewModel = SchoolTasksViewModel(repository, activeStudentManager, fixedClock())

        activeStudentManager.initialize(listOf(firstStudent, secondStudent))
        activeStudentManager.setActiveStudent(firstStudent)
        advanceUntilIdle()
        activeStudentManager.setActiveStudent(secondStudent)
        advanceUntilIdle()

        assertEquals(listOf("Drugi uczeń"), viewModel.uiState.value.weekTasks.map { it.details?.description })
    }

    @Test
    fun requestRangeIsClampedAtSchoolYearStart() = runTest {
        val activeStudentManager = ActiveStudentManager()
        val repository = FakeSchoolTasksRepository(activeStudentManager)
        val viewModel = SchoolTasksViewModel(repository, activeStudentManager, fixedClock(LocalDate.of(2026, 9, 1)))

        activeStudentManager.initialize(listOf(firstStudent))
        advanceUntilIdle()
        viewModel.previousWeek()
        advanceUntilIdle()

        assertEquals(false, viewModel.uiState.value.canGoPreviousWeek)
        assertEquals(listOf(Request("first-key", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 4))), repository.requests)
    }

    @Test
    fun requestRangeIsClampedAtSchoolYearEnd() = runTest {
        val activeStudentManager = ActiveStudentManager()
        val repository = FakeSchoolTasksRepository(activeStudentManager)
        val viewModel = SchoolTasksViewModel(repository, activeStudentManager, fixedClock(LocalDate.of(2027, 6, 30)))

        activeStudentManager.initialize(listOf(firstStudent))
        advanceUntilIdle()
        viewModel.nextWeek()
        advanceUntilIdle()

        assertEquals(false, viewModel.uiState.value.canGoNextWeek)
        assertEquals(listOf(Request("first-key", LocalDate.of(2027, 6, 28), LocalDate.of(2027, 6, 30))), repository.requests)
    }

    @Test
    fun emptyRepositoryResponseIsEmptyStateNotError() = runTest {
        val activeStudentManager = ActiveStudentManager()
        val repository = FakeSchoolTasksRepository(activeStudentManager)
        val viewModel = SchoolTasksViewModel(repository, activeStudentManager, fixedClock())

        activeStudentManager.initialize(listOf(firstStudent))
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isEmpty)
        assertEquals(null, viewModel.uiState.value.errorMessage)
    }

    private class FakeSchoolTasksRepository(
        private val activeStudentManager: ActiveStudentManager,
    ) : SchoolTasksRepository {
        val requests = mutableListOf<Request>()
        var response = emptyList<SchoolTask>()
        val responseByRange = mutableMapOf<Pair<LocalDate, LocalDate>, List<SchoolTask>>()
        val responseByStudent = mutableMapOf<String, List<SchoolTask>>()

        override suspend fun getSchoolTasks(from: LocalDate, to: LocalDate): List<SchoolTask> {
            val studentKey = activeStudentManager.activeStudent.value?.key
            requests += Request(studentKey, from, to)
            return responseByStudent[studentKey]
                ?: responseByRange[from to to]
                ?: response
        }
    }

    private data class Request(
        val studentKey: String?,
        val from: LocalDate,
        val to: LocalDate,
    )

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

        fun schoolTask(
            date: LocalDate,
            description: String? = null,
        ): SchoolTask =
            SchoolTask(
                type = SchoolTaskType.Quiz,
                rawType = 2,
                date = date,
                subject = "Matematyka",
                hasAttachment = false,
                id = date.dayOfMonth,
                details = description?.let {
                    SchoolTaskDetails(
                        description = it,
                        teacher = null,
                        responseDeadline = null,
                    )
                },
            )

        fun fixedClock(date: LocalDate = LocalDate.of(2026, 9, 9)): Clock {
            val zoneId = ZoneId.of("Europe/Warsaw")
            return Clock.fixed(date.atStartOfDay(zoneId).toInstant(), zoneId)
        }
    }
}
