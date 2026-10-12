package pl.usagi.edziennik.persistence.changes

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.usagi.edziennik.domain.TimetableEntry
import pl.usagi.edziennik.domain.changes.ChangeEvent
import pl.usagi.edziennik.domain.changes.LocalAccountId
import pl.usagi.edziennik.domain.changes.SnapshotArea
import pl.usagi.edziennik.domain.changes.SnapshotScope
import pl.usagi.edziennik.widget.TimetableWidgetUpdater
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class TimetableWidgetUpdatesTest {
    private val day = LocalDate.of(2026, 10, 12)
    private val clock = Clock.fixed(Instant.parse("2026-10-12T10:00:00Z"), ZoneId.of("Europe/Warsaw"))
    private val identity = LocalSnapshotIdentity(MemoryIdentityStore()).apply {
        ensureAccountId()
        selectStudent(17)
    }
    private val scope = SnapshotScope(identity.currentAccountId()!!, identity.selectedStudentScopeId(),
        SnapshotArea.TIMETABLE, "2026-10-12/2026-10-16")
    private val store = MemorySnapshotStore()
    private val reader = LocalTimetableReader(store, identity, Json, clock)
    private val displayed = mutableListOf<LocalTimetableResult>()
    private val updater = TimetableWidgetUpdater { displayed += reader.read(day) }
    private val manager = SnapshotSyncManager(store, Json, identity, clock, updater)
    private val lesson = TimetableEntry(day, null, LocalTime.of(9, 0), LocalTime.of(9, 45),
        "Matematyka", null, room = "215", changes = emptyList())

    @Test fun successfulCompleteSyncRedrawsOnlyAfterSnapshotIsReadable() = runTest {
        manager.sync(scope, SyncCompleteness.Complete, CurrentSnapshotData.Timetable(listOf(lesson)))
        val shown = displayed.single() as LocalTimetableResult.Available
        assertEquals("Matematyka", shown.lessons.single().subject)
        assertEquals(clock.instant(), shown.lastCompleteAt)
    }

    @Test fun uiSaveRedrawsSharedSnapshotWithoutEvents() = runTest {
        manager.saveCompleteTimetable(scope, listOf(lesson))
        manager.saveCompleteTimetable(scope, listOf(lesson.copy(room = "218")))
        assertEquals(2, displayed.size)
        assertEquals("218", (displayed.last() as LocalTimetableResult.Available).lessons.single().room)
        assertTrue(store.events.isEmpty())
    }

    @Test fun completeEmptyPlanAlsoRedrawsAsAvailable() = runTest {
        manager.sync(scope, SyncCompleteness.Complete, CurrentSnapshotData.Timetable(emptyList()))
        assertEquals(LocalTimetableResult.Available(emptyList(), clock.instant()), displayed.single())
    }

    @Test fun failedCommitDoesNotRedrawOrAlterPreviousPlanAndTimestamp() = runTest {
        manager.saveCompleteTimetable(scope, listOf(lesson))
        val before = reader.read(day)
        store.failCommit = true
        try {
            manager.saveCompleteTimetable(scope, emptyList())
            error("Expected write failure")
        } catch (failure: IllegalStateException) {
            assertEquals("Commit failed", failure.message)
            assertEquals(before, reader.read(day))
            assertEquals(1, displayed.size)
        }
    }

    @Test fun failedBackgroundCommitDoesNotRedraw() = runTest {
        store.failCommit = true
        try {
            manager.sync(scope, SyncCompleteness.Complete, CurrentSnapshotData.Timetable(listOf(lesson)))
            error("Expected write failure")
        } catch (failure: IllegalStateException) {
            assertEquals("Commit failed", failure.message)
            assertTrue(displayed.isEmpty())
            assertEquals(LocalTimetableResult.NoBaseline, reader.read(day))
        }
    }

    @Test fun partialAndFailedLeavePlanAndTimestampAndDoNotRedraw() = runTest {
        manager.saveCompleteTimetable(scope, listOf(lesson))
        val before = reader.read(day)
        manager.sync(scope, SyncCompleteness.Failed)
        manager.sync(scope, SyncCompleteness.Partial(mainListComplete = false),
            CurrentSnapshotData.Timetable(emptyList()))
        assertEquals(before, reader.read(day))
        assertEquals(1, displayed.size)
    }

    @Test fun invalidAccountCannotWriteOrRedraw() = runTest {
        identity.replaceAccountId()
        manager.saveCompleteTimetable(scope, listOf(lesson))
        manager.sync(scope, SyncCompleteness.Complete, CurrentSnapshotData.Timetable(listOf(lesson)))
        assertTrue(displayed.isEmpty())
        assertTrue(store.payloads.isEmpty())
    }

    @Test fun renderFailureDoesNotLoseDetectedEventOrSuccessfulSave() = runTest {
        manager.saveCompleteTimetable(scope, listOf(lesson))
        val failingUpdater = TimetableWidgetUpdater { error("Launcher unavailable") }
        val sync = SnapshotSyncManager(store, Json, identity, clock, failingUpdater)
        val events = sync.sync(scope, SyncCompleteness.Complete,
            CurrentSnapshotData.Timetable(listOf(lesson.copy(room = "218"))))
        assertEquals(1, events.size)
        assertEquals("218", (reader.read(day) as LocalTimetableResult.Available).lessons.single().room)
    }

    private class MemoryIdentityStore : LocalIdentityStore {
        private val values = mutableMapOf<String, String>()
        override fun get(key: String) = values[key]
        override fun put(key: String, value: String) { values[key] = value }
        override fun clear() { values.clear() }
    }

    private class MemorySnapshotStore : SnapshotStore {
        val payloads = mutableMapOf<String, String>()
        val events = mutableListOf<ChangeEvent>()
        var failCommit = false
        override suspend fun read(scope: SnapshotScope) = payloads[scope.storageKey]
        override suspend fun update(scope: SnapshotScope, transform: (String?) -> SnapshotWrite): List<ChangeEvent> {
            val write = transform(payloads[scope.storageKey])
            if (failCommit) error("Commit failed")
            write.payload?.let { payloads[scope.storageKey] = it }
            events += write.events
            return write.events
        }
        override suspend fun deleteAccount(accountId: LocalAccountId) {
            payloads.keys.removeAll { it.startsWith("${accountId.value}|") }
        }
    }
}
