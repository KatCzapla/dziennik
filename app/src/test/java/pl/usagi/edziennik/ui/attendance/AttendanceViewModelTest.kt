package pl.usagi.edziennik.ui.attendance

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.Job
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import pl.usagi.edziennik.MainDispatcherRule
import pl.usagi.edziennik.domain.ActiveStudentManager
import pl.usagi.edziennik.domain.AttendanceEntry
import pl.usagi.edziennik.domain.AttendanceStatus
import pl.usagi.edziennik.domain.AttendanceExcuseStatus
import pl.usagi.edziennik.domain.AttendanceStatistics
import pl.usagi.edziennik.domain.AttendanceSubject
import pl.usagi.edziennik.domain.Student
import pl.usagi.edziennik.domain.time.WeekRange
import pl.usagi.edziennik.repository.AttendanceRepository
import pl.usagi.edziennik.repository.AttendanceFetchResult
import java.io.IOException
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@OptIn(ExperimentalCoroutinesApi::class)
class AttendanceViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun submittingBlocksSecondRequestAndRefreshesServerStatus() = runTest {
        val repository = ExcuseRepository()
        val manager = ActiveStudentManager()
        val viewModel = AttendanceViewModel(repository, manager, fixedClock(LocalDate.of(2026, 9, 15)))
        manager.initialize(listOf(firstStudent))
        advanceUntilIdle()
        val entry = viewModel.uiState.value.weekEntries.single()

        viewModel.openExcuseForSelectedDay()
        viewModel.selectExcuseOption(ExcuseOption.Lesson(entry))
        viewModel.changeExcuseContent("Proszę usprawiedliwić")
        viewModel.submitExcuse()
        runCurrent()
        viewModel.submitExcuse()
        assertTrue(viewModel.uiState.value.isSubmittingExcuse)
        assertEquals(1, repository.submitCalls)

        repository.entry = repository.entry.copy(excuseStatus = AttendanceExcuseStatus.Pending)
        repository.post.complete(Unit)
        advanceUntilIdle()

        assertEquals(2, repository.fetchCalls)
        assertEquals(AttendanceExcuseStatus.Pending, viewModel.uiState.value.weekEntries.single().excuseStatus)
        assertEquals(null, viewModel.uiState.value.excuseFormDate)
        assertFalse(viewModel.uiState.value.canSubmitExcuse(viewModel.uiState.value.weekEntries.single()))
    }

    @Test
    fun failedPostKeepsDraftAndShowsError() = runTest {
        val repository = ExcuseRepository()
        val manager = ActiveStudentManager()
        val viewModel = AttendanceViewModel(repository, manager, fixedClock(LocalDate.of(2026, 9, 15)))
        manager.initialize(listOf(firstStudent))
        advanceUntilIdle()
        viewModel.openExcuseForSelectedDay()
        viewModel.selectExcuseOption(ExcuseOption.Lesson(viewModel.uiState.value.weekEntries.single()))
        viewModel.changeExcuseContent("Powód")
        viewModel.submitExcuse()
        runCurrent()
        repository.post.completeExceptionally(IOException("offline"))
        advanceUntilIdle()

        assertEquals(1, repository.fetchCalls)
        assertEquals("Powód", viewModel.uiState.value.excuseContent)
        assertTrue(viewModel.uiState.value.selectedExcuseOption is ExcuseOption.Lesson)
        assertTrue(viewModel.uiState.value.excuseErrorMessage!!.contains("Nie udało się"))
        assertFalse(viewModel.uiState.value.isSubmittingExcuse)
    }

    @Test
    fun contentStopsAt256CharactersAndAllowsEmpty() = runTest {
        val repository = ExcuseRepository()
        val manager = ActiveStudentManager()
        val viewModel = AttendanceViewModel(repository, manager, fixedClock(LocalDate.of(2026, 9, 15)))
        manager.initialize(listOf(firstStudent))
        advanceUntilIdle()
        viewModel.openExcuseForSelectedDay()
        assertEquals("", viewModel.uiState.value.excuseContent)
        viewModel.changeExcuseContent("a".repeat(256))
        assertEquals(256, viewModel.uiState.value.excuseContent.length)
        viewModel.changeExcuseContent("a".repeat(257))
        assertEquals(256, viewModel.uiState.value.excuseContent.length)
    }

    @Test
    fun futureDayWithoutEntriesSubmitsWholeDayAndRefreshes() = runTest {
        val repository = WholeDayRepository()
        val manager = ActiveStudentManager()
        val viewModel = AttendanceViewModel(repository, manager, fixedClock(LocalDate.of(2026, 9, 15)))
        manager.initialize(listOf(firstStudent))
        advanceUntilIdle()
        viewModel.selectDate(LocalDate.of(2026, 9, 16))
        viewModel.openExcuseForSelectedDay()
        val option = ExcuseOption.WholeDay(LocalDate.of(2026, 9, 16))
        viewModel.selectExcuseOption(option)
        viewModel.submitExcuse()
        advanceUntilIdle()

        assertEquals(option.date to null, repository.submitted)
        assertEquals(2, repository.fetchCalls)
        assertFalse(viewModel.uiState.value.showHeaderExcuseButton)
    }

    @Test
    fun formKeepsOnlyOneSelectedOption() = runTest {
        val repository = ExcuseRepository()
        val manager = ActiveStudentManager()
        val viewModel = AttendanceViewModel(repository, manager, fixedClock(LocalDate.of(2026, 9, 15)))
        manager.initialize(listOf(firstStudent))
        advanceUntilIdle()

        viewModel.openExcuseForSelectedDay()
        val lesson = ExcuseOption.Lesson(viewModel.uiState.value.weekEntries.single())
        val wholeDay = ExcuseOption.WholeDay(LocalDate.of(2026, 9, 15))
        viewModel.selectExcuseOption(lesson)
        viewModel.selectExcuseOption(wholeDay)

        assertEquals(wholeDay, viewModel.uiState.value.selectedExcuseOption)
    }

    private class WholeDayRepository : AttendanceRepository {
        var fetchCalls = 0
        var submitted: Pair<LocalDate, Int?>? = null

        override suspend fun getAttendance(from: LocalDate, to: LocalDate): List<AttendanceEntry> = emptyList()
        override suspend fun getAttendanceWithCompleteness(from: LocalDate, to: LocalDate): AttendanceFetchResult {
            fetchCalls++
            return AttendanceFetchResult(emptyList(), excusesComplete = true, excusesActive = true)
        }
        override suspend fun submitExcuse(date: LocalDate, lessonTimeId: Int?, content: String) {
            submitted = date to lessonTimeId
        }
        override suspend fun getSubjects(): List<AttendanceSubject> = emptyList()
        override suspend fun getStatistics(subjectId: Int): AttendanceStatistics = AttendanceStatistics(null, emptyList())
    }

    private class ExcuseRepository : AttendanceRepository {
        var entry = attendanceEntry().copy(date = LocalDate.of(2026, 9, 15), status = AttendanceStatus.Absent)
        var fetchCalls = 0
        var submitCalls = 0
        val post = CompletableDeferred<Unit>()

        override suspend fun getAttendance(from: LocalDate, to: LocalDate): List<AttendanceEntry> = listOf(entry)

        override suspend fun getAttendanceWithCompleteness(from: LocalDate, to: LocalDate): AttendanceFetchResult {
            fetchCalls++
            return AttendanceFetchResult(listOf(entry), excusesComplete = true, excusesActive = true)
        }

        override suspend fun submitExcuse(date: LocalDate, lessonTimeId: Int?, content: String) {
            submitCalls++
            assertEquals(entry.date, date)
            assertEquals(entry.lessonTimeId, lessonTimeId)
            post.await()
        }

        override suspend fun getSubjects(): List<AttendanceSubject> = emptyList()
        override suspend fun getStatistics(subjectId: Int): AttendanceStatistics = AttendanceStatistics(null, emptyList())
    }

    @Test
    fun lateWeekResponseCannotReplaceNewerWeek() = runTest {
        val manager = ActiveStudentManager()
        val repository = DelayedAttendanceRepository()
        val viewModel = AttendanceViewModel(repository, manager, fixedClock(LocalDate.of(2026, 9, 16)))
        manager.initialize(listOf(firstStudent))
        runCurrent()

        viewModel.previousWeek()
        runCurrent()
        assertEquals("New", viewModel.uiState.value.weekEntries.single().subject)
        assertTrue(repository.firstJob!!.isCancelled)

        repository.finishFirst(attendanceEntry().copy(subject = "Old"))
        advanceUntilIdle()
        assertEquals("New", viewModel.uiState.value.weekEntries.single().subject)
    }

    @Test
    fun lateStudentResponseCannotReplaceNewerStudent() = runTest {
        val manager = ActiveStudentManager()
        val repository = DelayedAttendanceRepository()
        val viewModel = AttendanceViewModel(repository, manager, fixedClock(LocalDate.of(2026, 9, 9)))
        manager.initialize(listOf(firstStudent, secondStudent))
        manager.setActiveStudent(firstStudent)
        runCurrent()

        manager.setActiveStudent(secondStudent)
        runCurrent()
        repository.finishFirst(attendanceEntry().copy(subject = "Old"))
        advanceUntilIdle()

        assertEquals(secondStudent, viewModel.uiState.value.activeStudent)
        assertEquals("New", viewModel.uiState.value.weekEntries.single().subject)
    }

    @Test
    fun lateStatisticsCannotReplaceNewerSubject() = runTest {
        val manager = ActiveStudentManager()
        val repository = DelayedAttendanceRepository(delayStatistics = true)
        val viewModel = AttendanceViewModel(repository, manager, fixedClock(LocalDate.of(2026, 9, 9)))
        manager.initialize(listOf(firstStudent))
        runCurrent()
        viewModel.selectView(AttendanceView.STATISTICS)
        runCurrent()

        viewModel.selectSubject(7867)
        runCurrent()
        assertEquals(2.0, viewModel.uiState.value.statistics?.summary)
        assertTrue(repository.firstStatisticsJob!!.isCancelled)

        repository.finishFirstStatistics()
        advanceUntilIdle()
        assertEquals(7867, viewModel.uiState.value.selectedSubjectId)
        assertEquals(2.0, viewModel.uiState.value.statistics?.summary)
    }

    private class DelayedAttendanceRepository(
        private val delayStatistics: Boolean = false,
    ) : AttendanceRepository {
        private var entryCount = 0
        private lateinit var first: Continuation<List<AttendanceEntry>>
        var firstJob: Job? = null
        private lateinit var firstStatistics: Continuation<AttendanceStatistics>
        var firstStatisticsJob: Job? = null

        override suspend fun getAttendance(from: LocalDate, to: LocalDate): List<AttendanceEntry> =
            if (!delayStatistics && ++entryCount == 1) suspendCoroutine { continuation ->
                first = continuation
                firstJob = continuation.context[Job]
            } else listOf(attendanceEntry().copy(subject = "New"))

        override suspend fun getSubjects(): List<AttendanceSubject> = listOf(
            AttendanceSubject(-1, "Wszystkie"), AttendanceSubject(7867, "Polski"),
        )

        override suspend fun getStatistics(subjectId: Int): AttendanceStatistics =
            if (subjectId == -1) suspendCoroutine { continuation ->
                firstStatistics = continuation
                firstStatisticsJob = continuation.context[Job]
            } else AttendanceStatistics(2.0, emptyList())

        fun finishFirst(entry: AttendanceEntry) = first.resume(listOf(entry))
        fun finishFirstStatistics() = firstStatistics.resume(AttendanceStatistics(1.0, emptyList()))
    }

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
