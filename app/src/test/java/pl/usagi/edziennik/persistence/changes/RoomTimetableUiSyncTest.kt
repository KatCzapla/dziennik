package pl.usagi.edziennik.persistence.changes

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import pl.usagi.edziennik.MainDispatcherRule
import pl.usagi.edziennik.domain.ActiveStudentManager
import pl.usagi.edziennik.domain.Student
import pl.usagi.edziennik.domain.TimetableChange
import pl.usagi.edziennik.domain.TimetableChangeKind
import pl.usagi.edziennik.domain.TimetableEntry
import pl.usagi.edziennik.domain.changes.ChangeEvent
import pl.usagi.edziennik.domain.changes.SnapshotArea
import pl.usagi.edziennik.domain.changes.SnapshotPayload
import pl.usagi.edziennik.domain.changes.SnapshotScope
import pl.usagi.edziennik.repository.TimetableRepository
import pl.usagi.edziennik.ui.timetable.TimetableViewModel
import pl.usagi.edziennik.widget.TimetableWidgetUpdater
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class RoomTimetableUiSyncTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule(UnconfinedTestDispatcher())
    private lateinit var db: SnapshotDatabase
    private lateinit var store: RoomSnapshotStore
    private val viewModels = ViewModelStore()
    private val identity = LocalSnapshotIdentity(TestIdentityStore()).apply {
        ensureAccountId()
        selectStudent(1)
    }
    private val scope = SnapshotScope(identity.currentAccountId()!!, identity.selectedStudentScopeId(),
        SnapshotArea.TIMETABLE, "2026-10-12/2026-10-16")
    private val day = LocalDate.of(2026, 10, 12)
    private val clock = Clock.fixed(Instant.parse("2026-10-12T10:00:00Z"), ZoneId.of("Europe/Warsaw"))
    private val ordinary = TimetableEntry(day, null, LocalTime.of(9, 0), LocalTime.of(9, 45),
        "Matematyka", null, room = "106", changes = emptyList())
    private val cancelled = ordinary.copy(changes = listOf(TimetableChange(
        4, TimetableChangeKind.Cancelled, null, null, null, null, null, null, null, null, null, null)))
    private var updates = 0
    private var updatesWithPendingEvent = 0

    @Before fun createDatabase() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), SnapshotDatabase::class.java)
            .allowMainThreadQueries().build()
        store = RoomSnapshotStore(db, Json)
    }

    @After fun closeDatabase() { viewModels.clear(); db.close() }

    @Test fun uiDetectsChangeBeforeWorkerAndAtomicallyQueuesIt() = runBlocking {
        worker(listOf(ordinary))
        screen(listOf(cancelled))
        assertCancelledEvent()
        val payload = Json.decodeFromString<SnapshotPayload>(store.read(scope)!!) as SnapshotPayload.Timetable
        assertTrue(payload.items.single().cancelled)
    }

    @Test fun workerAfterUiDoesNotQueueDuplicate() = runBlocking {
        worker(listOf(ordinary))
        screen(listOf(cancelled))
        assertTrue(worker(listOf(cancelled)).isEmpty())
        assertCancelledEvent()
    }

    @Test fun uiAfterWorkerDoesNotQueueDuplicate() = runBlocking {
        worker(listOf(ordinary))
        worker(listOf(cancelled))
        screen(listOf(cancelled))
        assertCancelledEvent()
    }

    @Test fun simultaneousUiAndWorkerWritesDetectExactlyOnce() = runBlocking {
        worker(listOf(ordinary))
        val gate = CompletableDeferred<Unit>()
        coroutineScope {
            val ui = async(Dispatchers.Default) {
                gate.await()
                manager().saveCompleteTimetable(scope, listOf(cancelled))
            }
            val background = async(Dispatchers.Default) { gate.await(); worker(listOf(cancelled)) }
            gate.complete(Unit)
            ui.await()
            background.await()
        }
        assertCancelledEvent()
    }

    @Test fun firstUiBaselineDoesNotQueueEvents() = runBlocking {
        screen(listOf(cancelled))
        assertNotNull(store.read(scope))
        assertTrue(pending().isEmpty())
    }

    @Test fun identicalUiPlanDoesNotQueueEvents() = runBlocking {
        worker(listOf(ordinary))
        screen(listOf(ordinary))
        screen(listOf(ordinary))
        assertTrue(pending().isEmpty())
    }

    @Test fun failedUiCommitRollsBackBothSnapshotAndOutboxWithoutHidingFetchedPlan() = runBlocking {
        worker(listOf(ordinary))
        val before = store.read(scope)
        updates = 0
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_snapshot BEFORE INSERT ON snapshots BEGIN SELECT RAISE(ABORT, 'test rollback'); END")
        val viewModel = screen(listOf(cancelled))
        assertTrue(viewModel.uiState.value.entries.single().isCancelled)
        assertEquals(before, store.read(scope))
        assertTrue(pending().isEmpty())
        assertEquals(0, updates)
    }

    @Test fun incompleteFetchDoesNotCreateFalseComplete() = runBlocking {
        screen(emptyList(), IllegalStateException("Incomplete response"))
        assertNull(store.read(scope))
        assertTrue(pending().isEmpty())
        assertEquals(0, updates)
    }

    @Test fun partialAndFailedPreserveCompleteTimeSnapshotAndQueuedEvents() = runBlocking {
        worker(listOf(ordinary))
        screen(listOf(cancelled))
        val before = store.read(scope)
        val queued = pending()
        val later = manager(Clock.fixed(clock.instant().plusSeconds(3600), clock.zone))
        later.sync(scope, SyncCompleteness.Partial(mainListComplete = false), CurrentSnapshotData.Timetable(emptyList()))
        later.sync(scope, SyncCompleteness.Failed)
        assertEquals(before, store.read(scope))
        assertEquals(queued, pending())
    }

    @Test fun successfulUiSaveUpdatesWidgetAfterSnapshotAndOutboxCommit() = runBlocking {
        worker(listOf(ordinary))
        updates = 0
        screen(listOf(cancelled))
        assertEquals(1, updates)
        assertEquals(1, updatesWithPendingEvent)
        val payload = Json.decodeFromString<SnapshotPayload>(store.read(scope)!!) as SnapshotPayload.Timetable
        assertEquals(clock.millis(), payload.lastCompleteAtEpochMillis)
    }

    private fun manager(time: Clock = clock) = SnapshotSyncManager(store, Json, identity, time,
        TimetableWidgetUpdater {
            updates++
            check(store.read(scope) != null)
            if (pending().isNotEmpty()) updatesWithPendingEvent++
        })

    private suspend fun worker(entries: List<TimetableEntry>) =
        manager().sync(scope, SyncCompleteness.Complete, CurrentSnapshotData.Timetable(entries))

    private suspend fun screen(entries: List<TimetableEntry>, failure: Exception? = null): TimetableViewModel {
        val student = Student("runtime-only", 1, "Student", "Class", "School")
        val students = ActiveStudentManager().apply { initialize(listOf(student)) }
        val repository = object : TimetableRepository {
            override suspend fun getTimetable(from: LocalDate, to: LocalDate): List<TimetableEntry> {
                failure?.let { throw it }
                return entries
            }
        }
        val viewModel = TimetableViewModel(repository, students, clock, manager(), identity)
        viewModels.put("screen-${System.identityHashCode(viewModel)}", viewModel)
        withTimeout(10_000) {
            viewModel.uiState.first { it.activeStudent != null && !it.isLoading &&
                (it.errorMessage != null || it.entries == entries) }
        }
        return viewModel
    }

    private suspend fun pending() = db.outboxDao().pending(scope.accountId.value)
    private suspend fun assertCancelledEvent() {
        val rows = pending()
        assertEquals(1, rows.size)
        assertTrue(Json.decodeFromString<ChangeEvent>(rows.single().payload) is ChangeEvent.LessonCancelled)
    }
}
