package pl.usagi.edziennik.ui.timetable

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.Job
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import pl.usagi.edziennik.MainDispatcherRule
import pl.usagi.edziennik.domain.ActiveStudentManager
import pl.usagi.edziennik.domain.Student
import pl.usagi.edziennik.domain.SchoolDayOff
import pl.usagi.edziennik.domain.TimetableEntry
import pl.usagi.edziennik.domain.TimetableChange
import pl.usagi.edziennik.domain.TimetableChangeKind
import pl.usagi.edziennik.domain.changes.ChangeEvent
import pl.usagi.edziennik.domain.changes.LocalAccountId
import pl.usagi.edziennik.domain.changes.SnapshotArea
import pl.usagi.edziennik.domain.changes.SnapshotScope
import pl.usagi.edziennik.persistence.changes.CurrentSnapshotData
import pl.usagi.edziennik.persistence.changes.LocalIdentityStore
import pl.usagi.edziennik.persistence.changes.LocalSnapshotIdentity
import pl.usagi.edziennik.persistence.changes.LocalTimetableReader
import pl.usagi.edziennik.persistence.changes.LocalTimetableResult
import pl.usagi.edziennik.persistence.changes.SnapshotStore
import pl.usagi.edziennik.persistence.changes.SnapshotSyncManager
import pl.usagi.edziennik.persistence.changes.SnapshotWrite
import pl.usagi.edziennik.persistence.changes.SyncCompleteness
import pl.usagi.edziennik.repository.TimetableRepository
import pl.usagi.edziennik.widget.TimetableWidgetUpdater
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@OptIn(ExperimentalCoroutinesApi::class)
class TimetableViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val identityStore = MemoryIdentityStore()
    private val identity = LocalSnapshotIdentity(identityStore)
    private val snapshotStore = MemorySnapshotStore()
    private var widgetUpdates = 0

    @Test fun emptyPlanAndNoDaysOffKeepExistingEmptyState() = runTest {
        val viewModel = createViewModel(FakeTimetableRepository(), selectedStudentManager(), fixedClock())
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.showNoLessons)
        assertTrue(viewModel.uiState.value.daysOff.isEmpty())
    }

    @Test fun emptyPlanStillShowsRestrictedDayOffUsingContextUnitId() = runTest {
        val manager = selectedStudentManager()
        manager.initialize(listOf(firstStudent.copy(organizationalUnitId = 1220)))
        val repository = FakeTimetableRepository().apply { daysOff = listOf(dayOff("Wolne", 1220)) }
        val viewModel = createViewModel(repository, manager, fixedClock())
        advanceUntilIdle()
        assertEquals(listOf("Wolne"), viewModel.uiState.value.daysOffFor(LocalDate.of(2026, 9, 9)).map { it.name })
        assertTrue(viewModel.uiState.value.selectedDayEntries.isEmpty())
        assertTrue(!viewModel.uiState.value.showNoLessons)
        assertEquals(listOf(LocalDate.of(2026, 9, 7) to LocalDate.of(2026, 9, 11)), repository.daysOffRequests)
    }

    @Test fun multipleDaysOffRemainVisibleWithoutNoLessonsMessage() = runTest {
        val repository = FakeTimetableRepository().apply {
            daysOff = listOf(
                dayOff("Dzień Edukacji Narodowej", 1220).copy(allUnits = true),
                dayOff("Dzień szkoły", 1220).copy(allUnits = true),
            )
        }
        val viewModel = createViewModel(repository, selectedStudentManager(), fixedClock())
        advanceUntilIdle()
        assertEquals(
            listOf("Dzień Edukacji Narodowej", "Dzień szkoły"),
            viewModel.uiState.value.daysOffFor(viewModel.uiState.value.selectedDate).map { it.name },
        )
        assertTrue(!viewModel.uiState.value.showNoLessons)
    }

    @Test fun dayOffDoesNotHideLessonsReturnedByApi() = runTest {
        val repository = FakeTimetableRepository().apply {
            daysOff = listOf(dayOff("Wolne", 1220).copy(allUnits = true))
            entries = listOf(timetableEntry(LocalDate.of(2026, 9, 9)))
        }
        val viewModel = createViewModel(repository, selectedStudentManager(), fixedClock())
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.selectedDayEntries.size)
        assertEquals(1, viewModel.uiState.value.daysOffFor(viewModel.uiState.value.selectedDate).size)
    }

    @Test fun changingStudentUsesNewContextUnitNotPreviousStudentsUnit() = runTest {
        val manager = selectedStudentManager()
        manager.initialize(listOf(firstStudent.copy(organizationalUnitId = 1220)))
        val repository = FakeTimetableRepository().apply {
            daysOff = listOf(dayOff("Pierwsza", 1220), dayOff("Druga", 999))
        }
        val viewModel = createViewModel(repository, manager, fixedClock())
        advanceUntilIdle()
        assertEquals(listOf("Pierwsza"), viewModel.uiState.value.daysOffFor(viewModel.uiState.value.selectedDate).map { it.name })
        manager.setActiveStudent(secondStudent.copy(organizationalUnitId = 999))
        advanceUntilIdle()
        assertEquals(listOf("Druga"), viewModel.uiState.value.daysOffFor(viewModel.uiState.value.selectedDate).map { it.name })
    }

    @Test fun missingContextUnitShowsOnlyAllUnitsDays() = runTest {
        val repository = FakeTimetableRepository().apply {
            daysOff = listOf(dayOff("Ograniczony", 1220), dayOff("Wspólny", 1220).copy(allUnits = true))
        }
        val viewModel = createViewModel(repository, selectedStudentManager(), fixedClock())
        advanceUntilIdle()
        assertEquals(listOf("Wspólny"), viewModel.uiState.value.daysOffFor(viewModel.uiState.value.selectedDate).map { it.name })
    }

    @Test fun daysOffFailureDoesNotBlockPlanOrSnapshot() = runTest {
        val repository = FakeTimetableRepository().apply {
            daysOffFailure = IllegalStateException("test days off failure")
            entries = listOf(timetableEntry(LocalDate.of(2026, 9, 9)))
        }
        val viewModel = createViewModel(repository, selectedStudentManager(), fixedClock())
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.daysOffFailed)
        assertEquals(null, viewModel.uiState.value.errorMessage)
        assertEquals(1, viewModel.uiState.value.selectedDayEntries.size)
        assertEquals(1, widgetUpdates)
    }

    @Test fun lateDaysOffResponseCannotReplaceNewStudentsDays() = runTest {
        val manager = selectedStudentManager()
        var first: Continuation<List<SchoolDayOff>>? = null
        var count = 0
        val repository = object : TimetableRepository {
            override suspend fun getTimetable(from: LocalDate, to: LocalDate) = emptyList<TimetableEntry>()
            override suspend fun getDaysOff(from: LocalDate, to: LocalDate): List<SchoolDayOff> =
                if (++count == 1) suspendCoroutine { first = it }
                else listOf(dayOff("Nowe", 999).copy(allUnits = true))
        }
        val viewModel = createViewModel(repository, manager, fixedClock())
        runCurrent()
        manager.setActiveStudent(secondStudent.copy(organizationalUnitId = 999))
        advanceUntilIdle()
        first!!.resume(listOf(dayOff("Stare", 1220).copy(allUnits = true)))
        advanceUntilIdle()
        assertEquals(listOf("Nowe"), viewModel.uiState.value.daysOffFor(viewModel.uiState.value.selectedDate).map { it.name })
    }

    private fun dayOff(name: String, unit: Int) = SchoolDayOff(name, LocalDate.of(2026, 9, 9),
        LocalDate.of(2026, 9, 9), false, setOf(unit))

    @Test
    fun successfulFetchFromScreenRedrawsWidgetAfterSavingSnapshot() = runTest {
        val repository = FakeTimetableRepository().apply {
            entries = listOf(timetableEntry(LocalDate.of(2026, 9, 9)))
        }
        createViewModel(repository, selectedStudentManager(), fixedClock())
        advanceUntilIdle()
        assertEquals(1, widgetUpdates)
        assertEquals("Matematyka", (localReader().read() as LocalTimetableResult.Available).lessons.single().subject)
    }

    @Test
    fun planFetchedByScreenIsReadableAfterRecreatingLocalReaderWithoutHttp() = runTest {
        val date = LocalDate.of(2026, 9, 9)
        val manager = selectedStudentManager()
        val repository = FakeTimetableRepository().apply { entries = listOf(timetableEntry(date)) }
        createViewModel(repository, manager, fixedClock())
        advanceUntilIdle()

        val reader = LocalTimetableReader(snapshotStore, LocalSnapshotIdentity(identityStore), Json, fixedClock())
        val result = reader.read(date) as LocalTimetableResult.Available
        assertEquals("Matematyka", result.lessons.single().subject)
        assertEquals(fixedClock().instant(), result.lastCompleteAt)
        assertEquals(1, repository.requests.size)
        assertEquals("2026-09-07/2026-09-11", snapshotStore.lastScope!!.range)
        assertEquals(identity.currentAccountId(), snapshotStore.lastScope!!.accountId)
        assertEquals(identity.selectedStudentScopeId(), snapshotStore.lastScope!!.studentScopeId)
    }

    @Test
    fun completeEmptyPlanFromScreenIsAvailableNotNoBaseline() = runTest {
        createViewModel(FakeTimetableRepository(), selectedStudentManager(), fixedClock())
        advanceUntilIdle()

        val result = localReader().read() as LocalTimetableResult.Available
        assertTrue(result.lessons.isEmpty())
        assertEquals(fixedClock().instant(), result.lastCompleteAt)
    }

    @Test
    fun failedScreenFetchDoesNotCreateBaseline() = runTest {
        val repository = FakeTimetableRepository().apply { failure = IllegalStateException("Invalid response") }
        val viewModel = createViewModel(repository, selectedStudentManager(), fixedClock())
        advanceUntilIdle()

        assertEquals(LocalTimetableResult.NoBaseline, localReader().read())
        assertTrue(snapshotStore.payloads.isEmpty())
        assertEquals(0, widgetUpdates)
        assertTrue(viewModel.uiState.value.errorMessage != null)
    }

    @Test
    fun failedRefreshPreservesPlanAndLastCompleteTime() = runTest {
        val date = LocalDate.of(2026, 9, 9)
        val repository = FakeTimetableRepository().apply { entries = listOf(timetableEntry(date)) }
        val manager = selectedStudentManager()
        createViewModel(repository, manager, fixedClock())
        advanceUntilIdle()
        val saved = snapshotStore.payloads.toMap()

        repository.failure = IllegalStateException("Failed fetch")
        createViewModel(repository, manager, fixedClock(date.plusDays(1)))
        advanceUntilIdle()

        assertEquals(saved, snapshotStore.payloads)
        assertEquals(fixedClock().instant(), (localReader().read(date) as LocalTimetableResult.Available).lastCompleteAt)
    }

    @Test
    fun screenDetectsChangeAndBackgroundDoesNotRepeatIt() = runTest {
        val date = LocalDate.of(2026, 9, 9)
        val manager = selectedStudentManager()
        val scope = SnapshotScope(identity.currentAccountId()!!, identity.selectedStudentScopeId(),
            SnapshotArea.TIMETABLE, "2026-09-07/2026-09-11")
        val sync = SnapshotSyncManager(snapshotStore, Json, identity, fixedClock())
        val ordinary = timetableEntry(date)
        sync.sync(scope, SyncCompleteness.Complete, CurrentSnapshotData.Timetable(listOf(ordinary)))
        val cancelled = ordinary.copy(changes = listOf(TimetableChange(
            changeType = 4,
            kind = TimetableChangeKind.Cancelled,
            teacherType = null,
            day = null,
            lessonNumber = null,
            startTime = null,
            endTime = null,
            group = null,
            subject = null,
            teacher = null,
            room = null,
            absenceInformation = null,
        )))
        createViewModel(FakeTimetableRepository().apply { entries = listOf(cancelled) }, manager, fixedClock())
        advanceUntilIdle()

        assertTrue((localReader().read(date) as LocalTimetableResult.Available).lessons.single().cancelled)
        assertEquals(1, snapshotStore.emittedEvents.size)
        assertTrue(snapshotStore.emittedEvents.single() is ChangeEvent.LessonCancelled)
        assertTrue(sync.sync(scope, SyncCompleteness.Complete, CurrentSnapshotData.Timetable(listOf(cancelled))).isEmpty())
        assertEquals(1, sync.sync(scope, SyncCompleteness.Complete,
            CurrentSnapshotData.Timetable(listOf(ordinary))).size)
    }

    @Test
    fun missingActiveStudentLeavesNoBaseline() = runTest {
        identity.ensureAccountId()
        identity.selectStudent(firstStudent.diaryId)
        val repository = FakeTimetableRepository()
        createViewModel(repository, ActiveStudentManager(), fixedClock())
        advanceUntilIdle()

        assertEquals(LocalTimetableResult.NoBaseline, localReader().read())
        assertTrue(repository.requests.isEmpty())
    }

    @Test
    fun localWriteFailureDoesNotHideFetchedPlanOrCreateBaseline() = runTest {
        snapshotStore.failWrites = true
        val repository = FakeTimetableRepository().apply {
            entries = listOf(timetableEntry(LocalDate.of(2026, 9, 9)))
        }
        val viewModel = createViewModel(repository, selectedStudentManager(), fixedClock())
        advanceUntilIdle()

        assertEquals("Matematyka", viewModel.uiState.value.entries.single().subject)
        assertEquals(null, viewModel.uiState.value.errorMessage)
        assertEquals(LocalTimetableResult.NoBaseline, localReader().read())
        assertEquals(0, widgetUpdates)
    }

    private fun selectedStudentManager(): ActiveStudentManager {
        identity.ensureAccountId()
        identity.selectStudent(firstStudent.diaryId)
        return ActiveStudentManager().apply { initialize(listOf(firstStudent)) }
    }

    private fun localReader() = LocalTimetableReader(snapshotStore, identity, Json, fixedClock())

    private fun createViewModel(repository: TimetableRepository, manager: ActiveStudentManager, clock: Clock) =
        TimetableViewModel(repository, manager, clock, SnapshotSyncManager(snapshotStore, Json, identity, clock,
            TimetableWidgetUpdater {
                assertTrue(snapshotStore.payloads.isNotEmpty())
                widgetUpdates++
            }), identity)

    @Test
    fun lateWeekResponseCannotReplaceNewerWeek() = runTest {
        identity.ensureAccountId()
        identity.selectStudent(firstStudent.diaryId)
        val manager = ActiveStudentManager()
        val repository = DelayedTimetableRepository()
        val viewModel = createViewModel(repository, manager, fixedClock())
        manager.initialize(listOf(firstStudent))
        runCurrent()

        viewModel.nextWeek()
        runCurrent()
        assertEquals("New", viewModel.uiState.value.entries.single().subject)
        assertTrue(repository.firstJob!!.isCancelled)

        repository.finishFirst(timetableEntry(LocalDate.of(2026, 9, 9)).copy(subject = "Old"))
        advanceUntilIdle()
        assertEquals("New", viewModel.uiState.value.entries.single().subject)
        assertEquals(LocalTimetableResult.NoBaseline, localReader().read(LocalDate.of(2026, 9, 9)))
        assertEquals("New", (localReader().read(LocalDate.of(2026, 9, 14)) as LocalTimetableResult.Available)
            .lessons.single().subject)
    }

    @Test
    fun lateStudentResponseCannotReplaceNewerStudent() = runTest {
        val manager = ActiveStudentManager()
        val repository = DelayedTimetableRepository()
        val viewModel = createViewModel(repository, manager, fixedClock())
        manager.initialize(listOf(firstStudent, secondStudent))
        manager.setActiveStudent(firstStudent)
        runCurrent()

        manager.setActiveStudent(secondStudent)
        runCurrent()
        repository.finishFirst(timetableEntry(LocalDate.of(2026, 9, 9)).copy(subject = "Old"))
        advanceUntilIdle()

        assertEquals(secondStudent, viewModel.uiState.value.activeStudent)
        assertEquals("New", viewModel.uiState.value.entries.single().subject)
    }

    private class DelayedTimetableRepository : TimetableRepository {
        private var count = 0
        private lateinit var first: Continuation<List<TimetableEntry>>
        var firstJob: Job? = null

        override suspend fun getTimetable(from: LocalDate, to: LocalDate): List<TimetableEntry> =
            if (++count == 1) suspendCoroutine { continuation ->
                first = continuation
                firstJob = continuation.context[Job]
            } else listOf(timetableEntry(from).copy(subject = "New"))

        fun finishFirst(entry: TimetableEntry) = first.resume(listOf(entry))
    }

    @Test
    fun activeStudentChangeReloadsTimetable() = runTest {
        val activeStudentManager = ActiveStudentManager()
        val repository = FakeTimetableRepository()
        val viewModel = createViewModel(repository, activeStudentManager, fixedClock())

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
        val viewModel = createViewModel(repository, activeStudentManager, fixedClock())

        activeStudentManager.initialize(listOf(firstStudent))
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isEmpty)
        assertEquals(null, viewModel.uiState.value.errorMessage)
    }

    @Test
    fun changingDayInsideLoadedWeekDoesNotReloadTimetable() = runTest {
        val activeStudentManager = ActiveStudentManager()
        val repository = FakeTimetableRepository()
        val viewModel = createViewModel(repository, activeStudentManager, fixedClock())

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
        val viewModel = createViewModel(repository, activeStudentManager, fixedClock())

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
        val viewModel = createViewModel(repository, activeStudentManager, fixedClock())

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
        val viewModel = createViewModel(repository, activeStudentManager, fixedClock())

        activeStudentManager.initialize(listOf(firstStudent))
        advanceUntilIdle()
        val selectedDate = viewModel.uiState.value.selectedDate
        viewModel.nextWeek()
        advanceUntilIdle()

        assertEquals(selectedDate.plusWeeks(1), viewModel.uiState.value.selectedDate)
        assertEquals(2, repository.requests.size)
    }

    @Test
    fun previousWeekIsDisabledBeforeSchoolYearStartAndRequestIsClamped() = runTest {
        val activeStudentManager = ActiveStudentManager()
        val repository = FakeTimetableRepository()
        val viewModel = createViewModel(repository, activeStudentManager, fixedClock(LocalDate.of(2026, 9, 1)))

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
        val repository = FakeTimetableRepository()
        val viewModel = createViewModel(repository, activeStudentManager, fixedClock(LocalDate.of(2027, 6, 30)))

        activeStudentManager.initialize(listOf(firstStudent))
        advanceUntilIdle()
        viewModel.nextWeek()
        advanceUntilIdle()

        assertEquals(LocalDate.of(2027, 6, 30), viewModel.uiState.value.selectedDate)
        assertEquals(false, viewModel.uiState.value.canGoNextWeek)
        assertEquals(listOf(LocalDate.of(2027, 6, 28) to LocalDate.of(2027, 6, 30)), repository.requests)
    }

    @Test
    fun cannotSelectDatesOutsideSchoolYear() = runTest {
        val activeStudentManager = ActiveStudentManager()
        val repository = FakeTimetableRepository()
        val viewModel = createViewModel(repository, activeStudentManager, fixedClock())

        activeStudentManager.initialize(listOf(firstStudent))
        advanceUntilIdle()
        val selectedDate = viewModel.uiState.value.selectedDate
        viewModel.selectDate(LocalDate.of(2026, 8, 31))
        viewModel.selectDate(LocalDate.of(2027, 7, 1))
        advanceUntilIdle()

        assertEquals(selectedDate, viewModel.uiState.value.selectedDate)
        assertEquals(1, repository.requests.size)
    }

    @Test
    fun activeStudentChangeReloadsDisplayedWeekWithoutChangingSelectedDate() = runTest {
        val activeStudentManager = ActiveStudentManager()
        val repository = FakeTimetableRepository()
        val viewModel = createViewModel(repository, activeStudentManager, fixedClock())

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
        var daysOff: List<SchoolDayOff> = emptyList()
        var daysOffFailure: Exception? = null
        val daysOffRequests = mutableListOf<Pair<LocalDate, LocalDate>>()
        override suspend fun getDaysOff(from: LocalDate, to: LocalDate): List<SchoolDayOff> {
            daysOffRequests += from to to
            daysOffFailure?.let { throw it }
            return daysOff
        }
        val requests = mutableListOf<Pair<LocalDate, LocalDate>>()
        var entries: List<TimetableEntry> = emptyList()
        var failure: Exception? = null

        override suspend fun getTimetable(from: LocalDate, to: LocalDate): List<TimetableEntry> {
            requests += from to to
            failure?.let { throw it }
            return entries
        }
    }

    private class MemoryIdentityStore : LocalIdentityStore {
        private val values = mutableMapOf<String, String>()
        override fun get(key: String): String? = values[key]
        override fun put(key: String, value: String) { values[key] = value }
        override fun clear() { values.clear() }
    }

    private class MemorySnapshotStore : SnapshotStore {
        val payloads = mutableMapOf<String, String>()
        val emittedEvents = mutableListOf<ChangeEvent>()
        var lastScope: SnapshotScope? = null
        var failWrites = false

        override suspend fun read(scope: SnapshotScope): String? = payloads[scope.storageKey]
        override suspend fun update(scope: SnapshotScope, transform: (String?) -> SnapshotWrite): List<ChangeEvent> {
            if (failWrites) error("Storage unavailable")
            lastScope = scope
            val write = transform(payloads[scope.storageKey])
            write.payload?.let { payloads[scope.storageKey] = it }
            emittedEvents += write.events
            return write.events
        }
        override suspend fun deleteAccount(accountId: LocalAccountId) {
            payloads.keys.removeAll { it.startsWith("${accountId.value}|") }
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

        fun fixedClock(date: LocalDate = LocalDate.of(2026, 9, 9)): Clock {
            val zoneId = ZoneId.of("Europe/Warsaw")
            return Clock.fixed(date.atStartOfDay(zoneId).toInstant(), zoneId)
        }
    }
}
