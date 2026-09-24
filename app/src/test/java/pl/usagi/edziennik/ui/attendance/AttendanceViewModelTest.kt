package pl.usagi.edziennik.ui.attendance

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import pl.usagi.edziennik.MainDispatcherRule
import pl.usagi.edziennik.domain.ActiveStudentManager
import pl.usagi.edziennik.domain.AttendanceEntry
import pl.usagi.edziennik.domain.AttendanceStatus
import pl.usagi.edziennik.domain.AttendanceStatistics
import pl.usagi.edziennik.domain.AttendanceSubject
import pl.usagi.edziennik.domain.Student
import pl.usagi.edziennik.domain.time.WeekRange
import pl.usagi.edziennik.repository.AttendanceRepository
import java.io.IOException
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class AttendanceViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun initialLoadOnSchoolDaySelectsTodayAndFetchesWeek() = runTest {
        val today = LocalDate.of(2026, 9, 9)
        val activeStudentManager = ActiveStudentManager()
        val repository = FakeAttendanceRepository()
        val viewModel = AttendanceViewModel(repository, activeStudentManager, fixedClock(today))

        activeStudentManager.initialize(listOf(firstStudent))
        advanceUntilIdle()

        assertEquals(today, viewModel.uiState.value.selectedDate)
        assertEquals(
            listOf(WeekRange.fromDate(today).start to WeekRange.fromDate(today).start.plusDays(4)),
            repository.requests,
        )
    }

    @Test
    fun initialLoadOnSaturdaySelectsPreviousFriday() = runTest {
        val activeStudentManager = ActiveStudentManager()
        val viewModel = AttendanceViewModel(FakeAttendanceRepository(), activeStudentManager, fixedClock(LocalDate.of(2026, 9, 12)))

        activeStudentManager.initialize(listOf(firstStudent))
        advanceUntilIdle()

        assertEquals(LocalDate.of(2026, 9, 11), viewModel.uiState.value.selectedDate)
    }

    @Test
    fun initialLoadOnSundaySelectsPreviousFriday() = runTest {
        val activeStudentManager = ActiveStudentManager()
        val viewModel = AttendanceViewModel(FakeAttendanceRepository(), activeStudentManager, fixedClock(LocalDate.of(2026, 9, 13)))

        activeStudentManager.initialize(listOf(firstStudent))
        advanceUntilIdle()

        assertEquals(LocalDate.of(2026, 9, 11), viewModel.uiState.value.selectedDate)
    }

    @Test
    fun dayChangeInsideLoadedWeekDoesNotReloadAttendance() = runTest {
        val today = LocalDate.of(2026, 9, 9)
        val activeStudentManager = ActiveStudentManager()
        val repository = FakeAttendanceRepository()
        val viewModel = AttendanceViewModel(repository, activeStudentManager, fixedClock(today))

        activeStudentManager.initialize(listOf(firstStudent))
        advanceUntilIdle()
        viewModel.selectDate(today.plusDays(1))
        advanceUntilIdle()

        assertEquals(1, repository.requests.size)
    }

    @Test
    fun fridayNextDayCannotMoveIntoFutureWeek() = runTest {
        val friday = LocalDate.of(2026, 9, 11)
        val activeStudentManager = ActiveStudentManager()
        val repository = FakeAttendanceRepository()
        val viewModel = AttendanceViewModel(repository, activeStudentManager, fixedClock(friday))

        activeStudentManager.initialize(listOf(firstStudent))
        advanceUntilIdle()
        viewModel.nextDay()
        advanceUntilIdle()

        assertEquals(friday, viewModel.uiState.value.selectedDate)
        assertEquals(1, repository.requests.size)
    }

    @Test
    fun previousWeekKeepsSameSchoolDayAndReloadsAttendance() = runTest {
        val today = LocalDate.of(2026, 9, 9)
        val activeStudentManager = ActiveStudentManager()
        val repository = FakeAttendanceRepository()
        val viewModel = AttendanceViewModel(repository, activeStudentManager, fixedClock(today))

        activeStudentManager.initialize(listOf(firstStudent))
        advanceUntilIdle()
        viewModel.previousWeek()
        advanceUntilIdle()

        assertEquals(today.minusWeeks(1), viewModel.uiState.value.selectedDate)
        assertEquals(2, repository.requests.size)
    }

    @Test
    fun nextWeekIsDisabledForCurrentWeek() = runTest {
        val today = LocalDate.of(2026, 9, 9)
        val activeStudentManager = ActiveStudentManager()
        val repository = FakeAttendanceRepository()
        val viewModel = AttendanceViewModel(repository, activeStudentManager, fixedClock(today))

        activeStudentManager.initialize(listOf(firstStudent))
        advanceUntilIdle()
        viewModel.nextWeek()
        advanceUntilIdle()

        assertEquals(today, viewModel.uiState.value.selectedDate)
        assertEquals(1, repository.requests.size)
    }

    @Test
    fun nextWeekFromPastWeekKeepsSameSchoolDayAndReloadsAttendance() = runTest {
        val today = LocalDate.of(2026, 9, 23)
        val activeStudentManager = ActiveStudentManager()
        val repository = FakeAttendanceRepository()
        val viewModel = AttendanceViewModel(repository, activeStudentManager, fixedClock(today))

        activeStudentManager.initialize(listOf(firstStudent))
        advanceUntilIdle()
        viewModel.selectDate(LocalDate.of(2026, 9, 9))
        advanceUntilIdle()
        viewModel.nextWeek()
        advanceUntilIdle()

        assertEquals(LocalDate.of(2026, 9, 16), viewModel.uiState.value.selectedDate)
        assertEquals(3, repository.requests.size)
    }

    @Test
    fun todayReturnsToEffectiveAttendanceDate() = runTest {
        val today = LocalDate.of(2026, 9, 12)
        val activeStudentManager = ActiveStudentManager()
        val repository = FakeAttendanceRepository()
        val viewModel = AttendanceViewModel(repository, activeStudentManager, fixedClock(today))

        activeStudentManager.initialize(listOf(firstStudent))
        advanceUntilIdle()
        viewModel.selectDate(LocalDate.of(2026, 9, 4))
        advanceUntilIdle()
        viewModel.today()
        advanceUntilIdle()

        assertEquals(LocalDate.of(2026, 9, 11), viewModel.uiState.value.selectedDate)
    }

    @Test
    fun activeStudentChangeReloadsCurrentWeek() = runTest {
        val activeStudentManager = ActiveStudentManager()
        val repository = FakeAttendanceRepository()
        AttendanceViewModel(repository, activeStudentManager, fixedClock(LocalDate.of(2026, 9, 9)))

        activeStudentManager.initialize(listOf(firstStudent, secondStudent))
        activeStudentManager.setActiveStudent(firstStudent)
        advanceUntilIdle()
        activeStudentManager.setActiveStudent(secondStudent)
        advanceUntilIdle()

        assertEquals(2, repository.requests.size)
    }

    @Test
    fun activeStudentChangeKeepsSelectedDate() = runTest {
        val selectedDate = LocalDate.of(2026, 9, 10)
        val activeStudentManager = ActiveStudentManager()
        val repository = FakeAttendanceRepository()
        val viewModel = AttendanceViewModel(repository, activeStudentManager, fixedClock(LocalDate.of(2026, 9, 9)))

        activeStudentManager.initialize(listOf(firstStudent, secondStudent))
        activeStudentManager.setActiveStudent(firstStudent)
        advanceUntilIdle()
        viewModel.selectDate(selectedDate)
        advanceUntilIdle()
        activeStudentManager.setActiveStudent(secondStudent)
        advanceUntilIdle()

        assertEquals(selectedDate, viewModel.uiState.value.selectedDate)
    }

    @Test
    fun previousWeekIsDisabledBeforeSchoolYearStartAndRequestIsClamped() = runTest {
        val activeStudentManager = ActiveStudentManager()
        val repository = FakeAttendanceRepository()
        val viewModel = AttendanceViewModel(repository, activeStudentManager, fixedClock(LocalDate.of(2026, 9, 1)))

        activeStudentManager.initialize(listOf(firstStudent))
        advanceUntilIdle()
        viewModel.previousWeek()
        advanceUntilIdle()

        assertEquals(LocalDate.of(2026, 9, 1), viewModel.uiState.value.selectedDate)
        assertEquals(false, viewModel.uiState.value.canGoPreviousWeek)
        assertEquals(listOf(LocalDate.of(2026, 9, 1) to LocalDate.of(2026, 9, 4)), repository.requests)
    }

    @Test
    fun nextWeekIsDisabledAfterSchoolYearEndAndRequestIsClamped() = runTest {
        val activeStudentManager = ActiveStudentManager()
        val repository = FakeAttendanceRepository()
        val viewModel = AttendanceViewModel(repository, activeStudentManager, fixedClock(LocalDate.of(2027, 6, 30)))

        activeStudentManager.initialize(listOf(firstStudent))
        advanceUntilIdle()
        viewModel.nextDay()
        viewModel.nextWeek()
        advanceUntilIdle()

        assertEquals(LocalDate.of(2027, 6, 30), viewModel.uiState.value.selectedDate)
        assertEquals(false, viewModel.uiState.value.canGoNextWeek)
        assertEquals(listOf(LocalDate.of(2027, 6, 28) to LocalDate.of(2027, 6, 30)), repository.requests)
    }

    @Test
    fun cannotSelectDatesOutsideSchoolYearOrFutureWeek() = runTest {
        val activeStudentManager = ActiveStudentManager()
        val repository = FakeAttendanceRepository()
        val viewModel = AttendanceViewModel(repository, activeStudentManager, fixedClock(LocalDate.of(2026, 9, 9)))

        activeStudentManager.initialize(listOf(firstStudent))
        advanceUntilIdle()
        val selectedDate = viewModel.uiState.value.selectedDate
        viewModel.selectDate(LocalDate.of(2026, 8, 31))
        viewModel.selectDate(LocalDate.of(2027, 7, 1))
        viewModel.selectDate(LocalDate.of(2026, 9, 14))
        advanceUntilIdle()

        assertEquals(selectedDate, viewModel.uiState.value.selectedDate)
        assertEquals(1, repository.requests.size)
    }

    @Test
    fun repositoryErrorShowsErrorState() = runTest {
        val activeStudentManager = ActiveStudentManager()
        val repository = FakeAttendanceRepository(error = IOException())
        val viewModel = AttendanceViewModel(repository, activeStudentManager, fixedClock(LocalDate.of(2026, 9, 9)))

        activeStudentManager.initialize(listOf(firstStudent))
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals("Nie udało się pobrać frekwencji.", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun retryRepeatsRequest() = runTest {
        val activeStudentManager = ActiveStudentManager()
        val repository = FakeAttendanceRepository()
        val viewModel = AttendanceViewModel(repository, activeStudentManager, fixedClock(LocalDate.of(2026, 9, 9)))

        activeStudentManager.initialize(listOf(firstStudent))
        advanceUntilIdle()
        viewModel.retry()
        advanceUntilIdle()

        assertEquals(2, repository.requests.size)
    }

    @Test
    fun emptyRepositoryResponseIsEmptyStateNotError() = runTest {
        val activeStudentManager = ActiveStudentManager()
        val repository = FakeAttendanceRepository(entries = emptyList())
        val viewModel = AttendanceViewModel(repository, activeStudentManager, fixedClock(LocalDate.of(2026, 9, 9)))

        activeStudentManager.initialize(listOf(firstStudent))
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isEmpty)
        assertEquals(null, viewModel.uiState.value.errorMessage)
    }

    @Test
    fun statisticsStartWithAllSubjectsAndFetchSelectedSubjectId() = runTest {
        val activeStudentManager = ActiveStudentManager()
        val repository = FakeAttendanceRepository()
        val viewModel = AttendanceViewModel(repository, activeStudentManager, fixedClock(LocalDate.of(2026, 9, 9)))

        activeStudentManager.initialize(listOf(firstStudent))
        advanceUntilIdle()
        viewModel.selectView(AttendanceView.STATISTICS)
        advanceUntilIdle()

        assertEquals(listOf(-1), repository.statisticsRequests)
        assertEquals(-1, viewModel.uiState.value.selectedSubjectId)
        assertEquals("Wszystkie", viewModel.uiState.value.subjects.first().name)
        assertEquals(72.5, viewModel.uiState.value.statistics?.summary)

        viewModel.selectSubject(7867)
        advanceUntilIdle()

        assertEquals(listOf(-1, 7867), repository.statisticsRequests)
        assertEquals(7867, viewModel.uiState.value.selectedSubjectId)
    }

    @Test
    fun switchingStudentResetsStatisticsToAllSubjects() = runTest {
        val activeStudentManager = ActiveStudentManager()
        val repository = FakeAttendanceRepository()
        val viewModel = AttendanceViewModel(repository, activeStudentManager, fixedClock(LocalDate.of(2026, 9, 9)))

        activeStudentManager.initialize(listOf(firstStudent, secondStudent))
        activeStudentManager.setActiveStudent(firstStudent)
        advanceUntilIdle()
        viewModel.selectView(AttendanceView.STATISTICS)
        advanceUntilIdle()
        viewModel.selectSubject(7867)
        advanceUntilIdle()
        activeStudentManager.setActiveStudent(secondStudent)
        advanceUntilIdle()

        assertEquals(-1, viewModel.uiState.value.selectedSubjectId)
        assertEquals(listOf(-1, 7867, -1), repository.statisticsRequests)
    }

    private class FakeAttendanceRepository(
        private val entries: List<AttendanceEntry> = listOf(attendanceEntry()),
        private val error: Throwable? = null,
    ) : AttendanceRepository {
        val requests = mutableListOf<Pair<LocalDate, LocalDate>>()
        val statisticsRequests = mutableListOf<Int>()

        override suspend fun getAttendance(from: LocalDate, to: LocalDate): List<AttendanceEntry> {
            requests += from to to
            error?.let { throw it }
            return entries
        }

        override suspend fun getSubjects(): List<AttendanceSubject> = listOf(
            AttendanceSubject(-1, "Wszystkie"),
            AttendanceSubject(7867, "Język polski"),
        )

        override suspend fun getStatistics(subjectId: Int): AttendanceStatistics {
            statisticsRequests += subjectId
            return AttendanceStatistics(summary = 72.5, categories = emptyList())
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

        fun fixedClock(date: LocalDate): Clock {
            val zoneId = ZoneId.of("Europe/Warsaw")
            return Clock.fixed(date.atStartOfDay(zoneId).toInstant(), zoneId)
        }

        fun attendanceEntry() = AttendanceEntry(
            lessonTimeId = 10,
            classLessonId = 20,
            lessonNumber = 2,
            status = AttendanceStatus.Present,
            date = LocalDate.of(2026, 9, 10),
            startTime = LocalTime.of(8, 20),
            endTime = LocalTime.of(9, 5),
            subject = "Matematyka",
            teacher = "Jan Kowalski",
        )
    }
}
