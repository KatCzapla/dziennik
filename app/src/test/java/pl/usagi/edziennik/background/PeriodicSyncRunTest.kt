package pl.usagi.edziennik.background

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import pl.usagi.edziennik.domain.AttendanceStatus
import pl.usagi.edziennik.domain.changes.AttendanceKey
import pl.usagi.edziennik.domain.changes.ChangeEvent
import pl.usagi.edziennik.domain.changes.SnapshotArea
import pl.usagi.edziennik.persistence.changes.OneShotSyncResult
import pl.usagi.edziennik.persistence.changes.SnapshotSynchronizer
import pl.usagi.edziennik.persistence.changes.SyncIssue
import java.time.LocalDate

class PeriodicSyncRunTest {
    private val date = LocalDate.of(2026, 9, 30)
    private val event = ChangeEvent.AttendanceChanged(
        AttendanceKey(date, 12, null), "Matematyka", AttendanceStatus.Absent, true,
    )

    @Test fun invokesExistingSyncAndPublishesEvents() = runTest {
        val sync = FakeSync(listOf(event))
        val notifier = FakeNotifier()
        PeriodicSyncRun({ true }, sync, notifier).run()
        assertEquals(1, sync.calls)
        assertEquals(listOf(event), notifier.published)
    }

    @Test fun noSessionDoesNotSync() = runTest {
        val sync = FakeSync(listOf(event))
        val notifier = FakeNotifier()
        PeriodicSyncRun({ false }, sync, notifier).run()
        assertEquals(0, sync.calls)
        assertEquals(emptyList<ChangeEvent>(), notifier.published)
    }

    @Test fun baselineAndNoChangesDoNotPublish() = runTest {
        val sync = FakeSync(emptyList())
        val notifier = FakeNotifier()
        val runner = PeriodicSyncRun({ true }, sync, notifier)
        runner.run()
        runner.run()
        assertEquals(2, sync.calls)
        assertEquals(0, notifier.publishCalls)
    }

    @Test fun partialAreaDoesNotSuppressOrRepeatOtherEvents() = runTest {
        val sync = FakeSync(listOf(event), mapOf(SnapshotArea.TASKS to SyncIssue.PARTIAL))
        val notifier = FakeNotifier()
        val runner = PeriodicSyncRun({ true }, sync, notifier)
        runner.run()
        sync.events = emptyList()
        runner.run()
        assertEquals(listOf(event), notifier.published)
        assertEquals(1, notifier.publishCalls)
    }

    @Test fun failedAreaDoesNotBlockOtherEvents() = runTest {
        val sync = FakeSync(listOf(event), mapOf(SnapshotArea.GRADES to SyncIssue.FAILED))
        val notifier = FakeNotifier()
        PeriodicSyncRun({ true }, sync, notifier).run()
        assertEquals(listOf(event), notifier.published)
    }

    @Test fun deniedNotificationsStillCompleteSynchronization() = runTest {
        val sync = FakeSync(listOf(event))
        val notifier = object : ChangeNotifier {
            override fun publish(events: List<ChangeEvent>) = Unit
        }
        PeriodicSyncRun({ true }, sync, notifier).run()
        assertEquals(1, sync.calls)
    }

    @Test fun logoutDuringSyncDoesNotPublishOldAccountEvents() = runTest {
        var current = true
        val session = object : SyncSession {
            override suspend fun prepare() = true
            override fun isCurrent() = current
        }
        val sync = object : SnapshotSynchronizer {
            override suspend fun syncOnce(): OneShotSyncResult {
                current = false
                return OneShotSyncResult(listOf(event), emptyMap())
            }
        }
        val notifier = FakeNotifier()
        PeriodicSyncRun(session, sync, notifier).run()
        assertEquals(0, notifier.publishCalls)
    }

    private class FakeSync(
        var events: List<ChangeEvent>,
        val issues: Map<SnapshotArea, SyncIssue> = emptyMap(),
    ) : SnapshotSynchronizer {
        var calls = 0
        override suspend fun syncOnce(): OneShotSyncResult {
            calls++
            return OneShotSyncResult(events, issues)
        }
    }

    private class FakeNotifier : ChangeNotifier {
        val published = mutableListOf<ChangeEvent>()
        var publishCalls = 0
        override fun publish(events: List<ChangeEvent>) {
            publishCalls++
            published += events
        }
    }
}
