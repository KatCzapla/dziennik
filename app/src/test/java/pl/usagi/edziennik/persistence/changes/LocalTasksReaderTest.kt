package pl.usagi.edziennik.persistence.changes

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.usagi.edziennik.domain.SchoolTask
import pl.usagi.edziennik.domain.SchoolTaskDetails
import pl.usagi.edziennik.domain.SchoolTaskType
import pl.usagi.edziennik.domain.changes.ChangeEvent
import pl.usagi.edziennik.domain.changes.LocalAccountId
import pl.usagi.edziennik.domain.changes.SnapshotArea
import pl.usagi.edziennik.domain.changes.SnapshotPayload
import pl.usagi.edziennik.domain.changes.SnapshotScope
import pl.usagi.edziennik.domain.changes.TaskKey
import pl.usagi.edziennik.domain.changes.taskSnapshots
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class LocalTasksReaderTest {
    private val start = LocalDate.of(2026, 9, 30)
    private val instant = Instant.parse("2026-09-30T12:00:00Z")
    private val clock = Clock.fixed(instant, ZoneId.of("Europe/Warsaw"))
    private val identityStore = MemoryIdentityStore()
    private val identity = LocalSnapshotIdentity(identityStore)
    private val store = MemorySnapshotStore()

    @Test fun missingAccountDoesNotReadOrCreateAnIdentity() = runTest {
        assertEquals(TaskRangeCoverage.NO_BASELINE, reader().read(start).coverage)
        assertEquals(0, store.reads)
        assertNull(identity.currentAccountId())
    }

    @Test fun missingSelectedStudentDoesNotReadSnapshots() = runTest {
        identity.ensureAccountId()
        assertEquals(TaskRangeCoverage.NO_BASELINE, reader().read(start).coverage)
        assertEquals(0, store.reads)
    }

    @Test fun missingAllWeeksIsNoBaselineNotEmptyComplete() = runTest {
        selectStudent()
        val result = reader().read(start)
        assertEquals(TaskRangeCoverage.NO_BASELINE, result.coverage)
        assertEquals(3, result.weeks.count { it.coverage == TaskRangeCoverage.NO_BASELINE })
    }

    @Test fun completeEmptyWeeksGiveFullEmptyResult() = runTest {
        selectStudent()
        completeAll(emptyList())
        val result = reader().read(start)
        assertEquals(TaskRangeCoverage.FULL, result.coverage)
        assertTrue(result.tasks.isEmpty())
        assertTrue(result.weeks.all { it.lastCompleteAt == instant })
    }

    @Test fun missingWeekIsPartialAndDoesNotHideAvailableTasks() = runTest {
        selectStudent()
        complete(taskSnapshotWeeks(start).first(), listOf(task(start)))
        val result = reader().read(start)
        assertEquals(TaskRangeCoverage.PARTIAL, result.coverage)
        assertEquals(2, result.weeks.count { it.coverage == TaskRangeCoverage.NO_BASELINE })
        assertEquals(listOf(start), result.tasks.map { it.date })
    }

    @Test fun readerFiltersExactlyFourteenDaysAndSortsChronologically() = runTest {
        selectStudent()
        completeAll(listOf(task(start.plusDays(14), 4), task(start.plusDays(13), 3),
            task(start.minusDays(1), 1), task(start, 2)))
        val result = reader().read(start)
        assertEquals(start, result.from)
        assertEquals(start.plusDays(13), result.to)
        assertEquals(listOf(start, start.plusDays(13)), result.tasks.map { it.date })
        assertEquals(TaskRangeCoverage.FULL, result.coverage)
    }

    @Test fun saturdayAndSundayTasksAreReturned() = runTest {
        selectStudent()
        completeAll(listOf(task(start.plusDays(3), 1), task(start.plusDays(4), 2)))
        assertEquals(listOf(start.plusDays(3), start.plusDays(4)), reader().read(start).tasks.map { it.date })
    }

    @Test fun returnedTaskHasKeyTypeSubjectAndDescription() = runTest {
        selectStudent()
        completeAll(listOf(task(start, type = 1)))
        assertEquals(LocalTask(start, TaskKey(1, 3), SchoolTaskType.Test, "Matematyka", "Opis"),
            reader().read(start).tasks.single())
    }

    @Test fun unknownTypeIsRetainedWithoutGuessing() = runTest {
        selectStudent()
        completeAll(listOf(task(start, type = 999, description = null)))
        val entry = reader().read(start).tasks.single()
        assertEquals(TaskKey(999, 3), entry.key)
        assertEquals(SchoolTaskType.Unknown(999), entry.type)
        assertNull(entry.description)
    }

    @Test fun accountsAreIsolated() = runTest {
        selectStudent()
        completeAll(listOf(task(start)))
        identity.replaceAccountId()
        identity.selectStudent(17)
        assertEquals(TaskRangeCoverage.NO_BASELINE, reader().read(start).coverage)
    }

    @Test fun studentScopesAreIsolated() = runTest {
        selectStudent()
        completeAll(listOf(task(start)))
        identity.selectStudent(18)
        assertEquals(TaskRangeCoverage.NO_BASELINE, reader().read(start).coverage)
        identity.selectStudent(17)
        assertEquals(1, reader().read(start).tasks.size)
    }

    @Test fun identityChangeDuringReadDiscardsOldAccountData() = runTest {
        selectStudent()
        completeAll(listOf(task(start)))
        store.onRead = { identity.replaceAccountId(); identity.selectStudent(18) }
        val result = reader().read(start)
        assertEquals(TaskRangeCoverage.NO_BASELINE, result.coverage)
        assertTrue(result.tasks.isEmpty())
    }

    @Test fun logoutDuringReadReturnsSignedOutAndDiscardsAllPreviousTasks() = runTest {
        selectStudent()
        completeAll(listOf(task(start)))
        store.onRead = { identity.clearAccount() }
        val result = reader().read(start)
        assertTrue(result.isLoggedOut)
        assertEquals(TaskRangeCoverage.NO_BASELINE, result.coverage)
        assertTrue(result.tasks.isEmpty())
        assertTrue(result.weeks.all { it.lastCompleteAt == null })
    }

    @Test fun recreatedReaderUsesPersistedIdentityAndSnapshotsWithoutHttp() = runTest {
        selectStudent()
        completeAll(listOf(task(start)))
        val restored = LocalTasksReader(store, LocalSnapshotIdentity(identityStore), Json, clock)
        assertEquals(reader().read(start), restored.read(start))
    }

    @Test fun legacyPayloadIsReadableButCannotClaimWeekendCoverageOrTimestamp() = runTest {
        selectStudent()
        for (week in taskSnapshotWeeks(start)) {
            store.payloads[scope(week).storageKey] = Json.encodeToString<SnapshotPayload>(
                SnapshotPayload.Tasks(listOf(task(start)).taskSnapshots()))
        }
        val result = reader().read(start)
        assertEquals(TaskRangeCoverage.PARTIAL, result.coverage)
        assertEquals("Opis", result.tasks.single().description)
        assertTrue(result.weeks.all { it.lastCompleteAt == null })
    }

    @Test fun partialPreservesDescriptionAndCompleteTimeAndExposesFailedDetails() = runTest {
        selectStudent()
        completeAll(listOf(task(start)))
        val week = taskSnapshotWeeks(start).first()
        laterManager().sync(scope(week), SyncCompleteness.Partial(true, failedTaskDetails = setOf(TaskKey(4, 3))),
            CurrentSnapshotData.Tasks(listOf(task(start, description = null)), calendarRangeComplete = true))
        val result = reader().read(start)
        assertEquals(TaskRangeCoverage.PARTIAL, result.coverage)
        assertEquals("Opis", result.tasks.single().description)
        assertEquals(setOf(TaskKey(4, 3)), result.weeks.first().failedDetailKeys)
        assertEquals(instant, result.weeks.first().lastCompleteAt)
    }

    @Test fun partialRetainsRecordsMissingFromItsResult() = runTest {
        selectStudent()
        completeAll(listOf(task(start, 3), task(start.plusDays(1), 4)))
        laterManager().sync(scope(taskSnapshotWeeks(start).first()),
            SyncCompleteness.Partial(true, failedTaskDetails = setOf(TaskKey(4, 3))),
            CurrentSnapshotData.Tasks(listOf(task(start, 3, description = null)), calendarRangeComplete = true))
        assertEquals(setOf(3, 4), reader().read(start).tasks.map { it.key.id }.toSet())
    }

    @Test fun failedSyncPreservesSnapshotAndLastCompleteTime() = runTest {
        selectStudent()
        completeAll(listOf(task(start)))
        val before = reader().read(start)
        laterManager().sync(scope(taskSnapshotWeeks(start).first()), SyncCompleteness.Failed)
        assertEquals(before, reader().read(start))
    }

    @Test fun completeAfterPartialClearsFailureAndUpdatesTimeWithoutRepeatingEvents() = runTest {
        selectStudent()
        completeAll(listOf(task(start)))
        val week = taskSnapshotWeeks(start).first()
        laterManager().sync(scope(week), SyncCompleteness.Partial(true, failedTaskDetails = setOf(TaskKey(4, 3))),
            CurrentSnapshotData.Tasks(listOf(task(start, description = null)), calendarRangeComplete = true))
        assertTrue(laterManager().sync(scope(week), SyncCompleteness.Complete,
            CurrentSnapshotData.Tasks(listOf(task(start)), calendarRangeComplete = true)).isEmpty())
        val result = reader().read(start)
        assertEquals(TaskRangeCoverage.FULL, result.coverage)
        assertTrue(result.weeks.first().failedDetailKeys.isEmpty())
        assertEquals(instant.plusSeconds(3600), result.weeks.first().lastCompleteAt)
    }

    @Test fun partialCannotCreateFirstBaseline() = runTest {
        selectStudent()
        val week = taskSnapshotWeeks(start).first()
        laterManager().sync(scope(week), SyncCompleteness.Partial(true, failedTaskDetails = setOf(TaskKey(4, 3))),
            CurrentSnapshotData.Tasks(listOf(task(start, description = null)), calendarRangeComplete = true))
        assertEquals(TaskRangeCoverage.NO_BASELINE, reader().read(start).coverage)
        assertTrue(store.payloads.isEmpty())
    }

    @Test fun firstCompleteCreatesTaskBaselinesWithoutEvents() = runTest {
        selectStudent()
        for (week in taskSnapshotWeeks(start)) {
            assertTrue(SnapshotSyncManager(store, Json, identity, clock).sync(scope(week), SyncCompleteness.Complete,
                CurrentSnapshotData.Tasks(listOf(task(start)).filter { it.date in week.from..week.to },
                    calendarRangeComplete = true)).isEmpty())
        }
        assertEquals(TaskRangeCoverage.FULL, reader().read(start).coverage)
    }

    @Test fun extendingLegacyCoverageBaselinesNewWeekendRecordsThenDetectsTheirChanges() = runTest {
        selectStudent()
        val week = taskSnapshotWeeks(start).first()
        val manager = SnapshotSyncManager(store, Json, identity, clock)
        manager.sync(scope(week), SyncCompleteness.Complete, CurrentSnapshotData.Tasks(listOf(task(start))))
        val weekend = task(start.plusDays(3), 4)
        assertTrue(manager.sync(scope(week), SyncCompleteness.Complete,
            CurrentSnapshotData.Tasks(listOf(task(start), weekend), calendarRangeComplete = true)).isEmpty())
        assertEquals(listOf(start, start.plusDays(3)), reader().read(start).tasks.map { it.date })
        val changed = manager.sync(scope(week), SyncCompleteness.Complete,
            CurrentSnapshotData.Tasks(listOf(task(start), weekend.copy(details = SchoolTaskDetails("Nowy opis", null, null))),
                calendarRangeComplete = true))
        assertEquals(1, changed.size)
    }

    @Test fun summerRangeIsKnownEmptyWithinSchoolYearPolicyWithoutReadingStorage() = runTest {
        selectStudent()
        val result = reader().read(LocalDate.of(2026, 7, 15))
        assertEquals(TaskRangeCoverage.FULL, result.coverage)
        assertTrue(result.weeks.isEmpty())
        assertTrue(result.tasks.isEmpty())
        assertEquals(0, store.reads)
    }

    @Test fun yearEndFiltersDatesOutsideSchoolYear() = runTest {
        selectStudent()
        val day = LocalDate.of(2024, 6, 29)
        complete(taskSnapshotWeeks(day).single(), listOf(task(day), task(day.plusDays(1), 4), task(day.plusDays(2), 5)))
        val result = reader().read(day)
        assertEquals(listOf(day, day.plusDays(1)), result.tasks.map { it.date })
        assertEquals(TaskRangeCoverage.FULL, result.coverage)
    }

    private fun reader() = LocalTasksReader(store, identity, Json, clock)
    private fun selectStudent() { identity.ensureAccountId(); identity.selectStudent(17) }
    private fun scope(week: TaskSnapshotWeek) = SnapshotScope(identity.currentAccountId()!!,
        identity.selectedStudentScopeId(), SnapshotArea.TASKS, week.scopeRange)
    private fun laterManager() = SnapshotSyncManager(store, Json, identity,
        Clock.fixed(instant.plusSeconds(3600), clock.zone))

    private suspend fun completeAll(items: List<SchoolTask>) {
        for (week in taskSnapshotWeeks(start)) complete(week, items)
    }

    private suspend fun complete(week: TaskSnapshotWeek, items: List<SchoolTask>) {
        SnapshotSyncManager(store, Json, identity, clock).sync(scope(week), SyncCompleteness.Complete,
            CurrentSnapshotData.Tasks(items.filter { it.date in week.from..week.to }, calendarRangeComplete = true))
    }

    private fun task(date: LocalDate, id: Int = 3, type: Int = 4, description: String? = "Opis") =
        SchoolTask(SchoolTaskType.fromRawValue(type), type, date, "Matematyka", false, id,
            description?.let { SchoolTaskDetails(it, null, null) })

    private class MemoryIdentityStore : LocalIdentityStore {
        private val values = mutableMapOf<String, String>()
        override fun get(key: String) = values[key]
        override fun put(key: String, value: String) { values[key] = value }
        override fun clear() { values.clear() }
    }

    private class MemorySnapshotStore : SnapshotStore {
        val payloads = mutableMapOf<String, String>()
        var reads = 0
        var onRead: (() -> Unit)? = null
        override suspend fun read(scope: SnapshotScope): String? {
            reads++
            onRead?.also { onRead = null }?.invoke()
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
