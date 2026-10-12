package pl.usagi.edziennik.widget

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.usagi.edziennik.background.ChangeNotifier
import pl.usagi.edziennik.background.ManualWidgetSyncRun
import pl.usagi.edziennik.background.PeriodicSyncRun
import pl.usagi.edziennik.domain.SchoolTask
import pl.usagi.edziennik.domain.SchoolTaskDetails
import pl.usagi.edziennik.domain.SchoolTaskType
import pl.usagi.edziennik.domain.changes.ChangeEvent
import pl.usagi.edziennik.domain.changes.LocalAccountId
import pl.usagi.edziennik.domain.changes.SnapshotArea
import pl.usagi.edziennik.domain.changes.SnapshotScope
import pl.usagi.edziennik.domain.changes.TaskKey
import pl.usagi.edziennik.persistence.changes.CurrentSnapshotData
import pl.usagi.edziennik.persistence.changes.LocalIdentityStore
import pl.usagi.edziennik.persistence.changes.LocalSnapshotIdentity
import pl.usagi.edziennik.persistence.changes.LocalTasksReader
import pl.usagi.edziennik.persistence.changes.OneShotSyncResult
import pl.usagi.edziennik.persistence.changes.SnapshotStore
import pl.usagi.edziennik.persistence.changes.SnapshotSyncManager
import pl.usagi.edziennik.persistence.changes.SnapshotSynchronizer
import pl.usagi.edziennik.persistence.changes.SnapshotWrite
import pl.usagi.edziennik.persistence.changes.SyncCompleteness
import pl.usagi.edziennik.persistence.changes.TaskRangeCoverage
import pl.usagi.edziennik.persistence.changes.taskSnapshotWeeks
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class UpcomingTasksWidgetIntegrationTest {
    private val date = LocalDate.of(2026, 9, 30)
    private val zone = ZoneId.of("Europe/Warsaw")
    private val instant = date.atTime(12, 0).atZone(zone).toInstant()
    private val clock = Clock.fixed(instant, zone)
    private val identityStore = MemoryIdentityStore()
    private val identity = LocalSnapshotIdentity(identityStore).apply { ensureAccountId(); selectStudent(17) }
    private val store = MemorySnapshotStore()
    private val content = UpcomingTasksContentReader(LocalTasksReader(store, identity, Json, clock))
    private val displayed = mutableListOf<UpcomingTasksPresentation>()
    private val tasksUpdater = TasksWidgetUpdater { displayed += content.read(instant, zone) }
    private val manager = SnapshotSyncManager(store, Json, identity, clock, tasksWidgetUpdater = tasksUpdater)

    @Test fun completeTaskWritesAutomaticallyRedrawReadableDataIncludingBaseline() = runTest {
        saveAll(listOf(task(date)))
        assertEquals(3, displayed.size)
        assertEquals(TaskRangeCoverage.PARTIAL, displayed.first().coverage)
        assertEquals(TaskRangeCoverage.FULL, displayed.last().coverage)
        assertEquals("Opis", displayed.last().items.single().description)
    }

    @Test fun firstPartialDoesNotCreateSnapshotOrRequestRedraw() = runTest {
        manager.sync(scope(), SyncCompleteness.Partial(true, failedTaskDetails = setOf(TaskKey(4, 3))),
            CurrentSnapshotData.Tasks(listOf(task(date, null)), true))
        assertTrue(displayed.isEmpty())
        assertEquals(TaskRangeCoverage.NO_BASELINE, content.read(instant, zone).coverage)
    }

    @Test fun partialWriteRedrawsRetainedDescriptionWithPartialWarning() = runTest {
        saveAll(listOf(task(date)))
        manager.sync(scope(), SyncCompleteness.Partial(true, failedTaskDetails = setOf(TaskKey(4, 3))),
            CurrentSnapshotData.Tasks(listOf(task(date, null)), true))
        val shown = displayed.last()
        assertEquals(TaskRangeCoverage.PARTIAL, shown.coverage)
        assertEquals("Opis", shown.items.single().description)
        assertTrue(shown.lastCompleteLabel!!.startsWith("Zapis części danych:"))
    }

    @Test fun failedSyncKeepsPreviousPresentationTimeAndDoesNotRequestRedraw() = runTest {
        saveAll(listOf(task(date)))
        val before = content.read(instant, zone)
        manager.sync(scope(), SyncCompleteness.Failed)
        assertEquals(before, content.read(instant, zone))
        assertEquals(3, displayed.size)
    }

    @Test fun failedCommitDoesNotRequestRedrawOrLoseStoredTasks() = runTest {
        saveAll(listOf(task(date)))
        val before = content.read(instant, zone)
        store.failCommit = true
        try {
            manager.sync(scope(), SyncCompleteness.Complete, CurrentSnapshotData.Tasks(emptyList(), true))
            error("Expected failure")
        } catch (error: IllegalStateException) {
            assertEquals("Commit failed", error.message)
        }
        assertEquals(before, content.read(instant, zone))
        assertEquals(3, displayed.size)
    }

    @Test fun changingDayMovesWindowWithoutHttpAndDropsYesterday() = runTest {
        saveAll(listOf(task(date), task(date.plusDays(1)).copy(id = 4)))
        val nextDay = content.read(date.plusDays(1).atTime(12, 0).atZone(zone).toInstant(), zone)
        assertEquals(listOf(date.plusDays(1)), nextDay.items.map { it.target.date })
        assertEquals(TaskRangeCoverage.FULL, nextDay.coverage)
    }

    @Test fun recreatedWidgetContentReadsPersistedIdentityAndSnapshotWithoutHttp() = runTest {
        saveAll(listOf(task(date)))
        val recreated = UpcomingTasksContentReader(LocalTasksReader(store, LocalSnapshotIdentity(identityStore), Json, clock))
        assertEquals(content.read(instant, zone), recreated.read(instant, zone))
    }

    @Test fun timezoneChangeUsesCurrentLocalDate() = runTest {
        saveAll(listOf(task(date), task(date.plusDays(1)).copy(id = 4)))
        val midnight = date.plusDays(1).atTime(0, 30).atZone(zone).toInstant()
        assertEquals(listOf(date.plusDays(1)), content.read(midnight, zone).items.map { it.target.date })
        assertEquals(listOf(date, date.plusDays(1)), content.read(midnight, ZoneId.of("America/Los_Angeles"))
            .items.map { it.target.date })
    }

    @Test fun manualRunnerReusesExistingSyncAndRedrawsBothWidgets() = runTest {
        var syncCalls = 0
        var timetableUpdates = 0
        val sync = object : SnapshotSynchronizer {
            override suspend fun syncOnce(): OneShotSyncResult {
                syncCalls++
                saveAll(listOf(task(date)))
                return OneShotSyncResult(emptyList(), emptyMap())
            }
        }
        val notifier = object : ChangeNotifier { override fun publish(events: List<ChangeEvent>) = Unit }
        val updater = LocalWidgetsUpdater(TimetableWidgetUpdater { timetableUpdates++ }, tasksUpdater)
        ManualWidgetSyncRun(PeriodicSyncRun({ true }, sync, notifier), updater).run()
        assertEquals(1, syncCalls)
        assertEquals(2, timetableUpdates)
        assertEquals(5, displayed.size)
        assertEquals(TaskRangeCoverage.FULL, displayed.last().coverage)
    }

    @Test fun renderingFailureInFirstWidgetDoesNotPreventSecondRedraw() = runTest {
        var updates = 0
        LocalWidgetsUpdater(TimetableWidgetUpdater { error("Launcher failure") }, TasksWidgetUpdater { updates++ }).update()
        assertEquals(1, updates)
    }

    private fun scope() = SnapshotScope(identity.currentAccountId()!!, identity.selectedStudentScopeId(),
        SnapshotArea.TASKS, taskSnapshotWeeks(date).first().scopeRange)

    private suspend fun saveAll(tasks: List<SchoolTask>) {
        for (week in taskSnapshotWeeks(date)) {
            manager.sync(SnapshotScope(identity.currentAccountId()!!, identity.selectedStudentScopeId(),
                SnapshotArea.TASKS, week.scopeRange), SyncCompleteness.Complete,
                CurrentSnapshotData.Tasks(tasks.filter { it.date in week.from..week.to }, true))
        }
    }

    private fun task(day: LocalDate, description: String? = "Opis") = SchoolTask(SchoolTaskType.Homework,
        4, day, "Matematyka", false, 3, description?.let { SchoolTaskDetails(it, null, null) })

    private class MemoryIdentityStore : LocalIdentityStore {
        private val values = mutableMapOf<String, String>()
        override fun get(key: String) = values[key]
        override fun put(key: String, value: String) { values[key] = value }
        override fun clear() { values.clear() }
    }

    private class MemorySnapshotStore : SnapshotStore {
        private val payloads = mutableMapOf<String, String>()
        var failCommit = false
        override suspend fun read(scope: SnapshotScope) = payloads[scope.storageKey]
        override suspend fun update(scope: SnapshotScope, transform: (String?) -> SnapshotWrite): List<ChangeEvent> {
            val write = transform(payloads[scope.storageKey])
            if (failCommit) error("Commit failed")
            write.payload?.let { payloads[scope.storageKey] = it }
            return write.events
        }
        override suspend fun deleteAccount(accountId: LocalAccountId) { payloads.keys.removeAll { it.startsWith("${accountId.value}|") } }
    }
}
