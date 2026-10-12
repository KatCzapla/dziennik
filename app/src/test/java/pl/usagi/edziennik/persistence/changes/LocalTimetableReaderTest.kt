package pl.usagi.edziennik.persistence.changes

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.usagi.edziennik.domain.TimetableEntry
import pl.usagi.edziennik.domain.changes.ChangeEvent
import pl.usagi.edziennik.domain.changes.LocalAccountId
import pl.usagi.edziennik.domain.changes.SnapshotArea
import pl.usagi.edziennik.domain.changes.SnapshotPayload
import pl.usagi.edziennik.domain.changes.SnapshotScope
import pl.usagi.edziennik.domain.changes.timetableSnapshots
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class LocalTimetableReaderTest {
    private val day = LocalDate.of(2026, 10, 7)
    private val instant = Instant.parse("2026-10-07T08:00:00Z")
    private val clock = Clock.fixed(instant, ZoneId.of("Europe/Warsaw"))
    private val store = MemorySnapshotStore()
    private val identityStore = MemoryIdentityStore()
    private val identity = LocalSnapshotIdentity(identityStore)
    private val json = Json

    @Test fun missingAccountHasNoBaseline() = runTest {
        assertEquals(LocalTimetableResult.NoBaseline, reader().read(day))
        assertNull(store.lastReadScope)
    }

    @Test fun missingSelectedStudentHasNoBaseline() = runTest {
        identity.ensureAccountId()
        assertEquals(LocalTimetableResult.NoBaseline, reader().read(day))
        assertNull(store.lastReadScope)
    }

    @Test fun missingSnapshotHasNoBaseline() = runTest {
        selectStudent()
        assertEquals(LocalTimetableResult.NoBaseline, reader().read(day))
    }

    @Test fun completeEmptyPlanIsAvailableNotMissing() = runTest {
        selectStudent()
        complete(emptyList())
        assertEquals(LocalTimetableResult.Available(emptyList(), instant), reader().read(day))
    }

    @Test fun completePlanKeepsLessonFields() = runTest {
        selectStudent()
        complete(listOf(lesson(day)))
        val result = reader().read(day) as LocalTimetableResult.Available
        val entry = result.lessons.single()
        assertEquals(day, entry.slot.date)
        assertEquals(LocalTime.of(9, 0), entry.slot.start)
        assertEquals(LocalTime.of(9, 45), entry.slot.end)
        assertEquals("Matematyka", entry.subject)
        assertEquals("106", entry.room)
        assertEquals("Nauczyciel", entry.teacher)
        assertEquals(instant, result.lastCompleteAt)
    }

    @Test fun onlyRequestedDayIsReturned() = runTest {
        selectStudent()
        complete(listOf(lesson(day.minusDays(1)), lesson(day.plusDays(1)), lesson(day)))
        val result = reader().read(day) as LocalTimetableResult.Available
        assertEquals(listOf(day), result.lessons.map { it.slot.date })
    }

    @Test fun readUsesSelectedAccountStudentAndSchoolWeek() = runTest {
        selectStudent()
        complete(listOf(lesson(day)))
        reader().read(day)
        val scope = store.lastReadScope!!
        assertEquals(identity.currentAccountId(), scope.accountId)
        assertEquals(identity.selectedStudentScopeId(), scope.studentScopeId)
        assertEquals(SnapshotArea.TIMETABLE, scope.area)
        assertEquals("2026-10-05/2026-10-09", scope.range)
    }

    @Test fun processRecreationReadsPlanWithoutHttpSession() = runTest {
        selectStudent()
        complete(listOf(lesson(day)))
        val restoredIdentity = LocalSnapshotIdentity(identityStore)
        val restoredReader = LocalTimetableReader(store, restoredIdentity, json, clock)
        assertEquals("Matematyka", (restoredReader.read(day) as LocalTimetableResult.Available)
            .lessons.single().subject)
    }

    @Test fun secondCompleteRefreshesTimestampWithoutChangingDetection() = runTest {
        selectStudent()
        complete(listOf(lesson(day)))
        val later = instant.plusSeconds(3600)
        val manager = SnapshotSyncManager(store, json, identity, Clock.fixed(later, clock.zone))
        assertTrue(manager.sync(scope(), SyncCompleteness.Complete,
            CurrentSnapshotData.Timetable(listOf(lesson(day)))).isEmpty())
        assertEquals(later, (reader().read(day) as LocalTimetableResult.Available).lastCompleteAt)
    }

    @Test fun partialDoesNotRefreshLastCompleteTimestamp() = runTest {
        selectStudent()
        complete(listOf(lesson(day)))
        val manager = SnapshotSyncManager(store, json, identity, Clock.fixed(instant.plusSeconds(3600), clock.zone))
        manager.sync(scope(), SyncCompleteness.Partial(mainListComplete = true),
            CurrentSnapshotData.Timetable(emptyList()))
        val result = reader().read(day) as LocalTimetableResult.Available
        assertEquals(instant, result.lastCompleteAt)
        assertEquals(1, result.lessons.size)
    }

    @Test fun partialPlanCannotCreateCompleteBaseline() = runTest {
        selectStudent()
        val manager = SnapshotSyncManager(store, json, identity, clock)
        manager.sync(scope(), SyncCompleteness.Partial(mainListComplete = false),
            CurrentSnapshotData.Timetable(listOf(lesson(day))))
        assertEquals(LocalTimetableResult.NoBaseline, reader().read(day))
        manager.sync(scope(), SyncCompleteness.Partial(mainListComplete = true),
            CurrentSnapshotData.Timetable(listOf(lesson(day))))
        assertEquals(LocalTimetableResult.NoBaseline, reader().read(day))
        assertTrue(store.payloads.isEmpty())
    }

    @Test fun failedDoesNotRefreshLastCompleteTimestamp() = runTest {
        selectStudent()
        complete(listOf(lesson(day)))
        val manager = SnapshotSyncManager(store, json, identity, Clock.fixed(instant.plusSeconds(3600), clock.zone))
        manager.sync(scope(), SyncCompleteness.Failed)
        val result = reader().read(day) as LocalTimetableResult.Available
        assertEquals(instant, result.lastCompleteAt)
        assertEquals(1, result.lessons.size)
    }

    @Test fun oldBaselineWithoutTimestampRemainsReadable() = runTest {
        selectStudent()
        store.payloads[scope().storageKey] = json.encodeToString<SnapshotPayload>(
            SnapshotPayload.Timetable(listOf(lesson(day)).timetableSnapshots()),
        )
        val result = reader().read(day) as LocalTimetableResult.Available
        assertEquals(1, result.lessons.size)
        assertNull(result.lastCompleteAt)
    }

    @Test fun anotherWeekDoesNotFallBackToLastSnapshot() = runTest {
        selectStudent()
        complete(listOf(lesson(day)))
        assertEquals(LocalTimetableResult.NoBaseline, reader().read(day.plusWeeks(1)))
    }

    @Test fun anotherSelectedStudentDoesNotSeePlan() = runTest {
        selectStudent()
        complete(listOf(lesson(day)))
        identity.selectStudent(18)
        assertEquals(LocalTimetableResult.NoBaseline, reader().read(day))
    }

    private fun reader() = LocalTimetableReader(store, identity, json, clock)

    private fun selectStudent() {
        identity.ensureAccountId()
        identity.selectStudent(17)
    }

    private fun scope() = SnapshotScope(identity.currentAccountId()!!, identity.selectedStudentScopeId(),
        SnapshotArea.TIMETABLE, "2026-10-05/2026-10-09")

    private suspend fun complete(entries: List<TimetableEntry>) {
        SnapshotSyncManager(store, json, identity, clock).sync(scope(), SyncCompleteness.Complete,
            CurrentSnapshotData.Timetable(entries))
    }

    private fun lesson(date: LocalDate) = TimetableEntry(date, null, LocalTime.of(9, 0),
        LocalTime.of(9, 45), "Matematyka", "Nauczyciel", null, null, "106", emptyList())

    private class MemoryIdentityStore : LocalIdentityStore {
        private val values = mutableMapOf<String, String>()
        override fun get(key: String): String? = values[key]
        override fun put(key: String, value: String) { values[key] = value }
        override fun clear() { values.clear() }
    }

    private class MemorySnapshotStore : SnapshotStore {
        val payloads = mutableMapOf<String, String>()
        var lastReadScope: SnapshotScope? = null

        override suspend fun read(scope: SnapshotScope): String? {
            lastReadScope = scope
            return payloads[scope.storageKey]
        }

        override suspend fun update(scope: SnapshotScope, transform: (String?) -> SnapshotWrite): List<ChangeEvent> {
            val write = transform(payloads[scope.storageKey])
            write.payload?.let { payloads[scope.storageKey] = it }
            return write.events
        }

        override suspend fun deleteAccount(accountId: LocalAccountId) {
            payloads.keys.removeAll { it.startsWith("${accountId.value}|") }
        }
    }
}
