package pl.usagi.edziennik.background

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import pl.usagi.edziennik.domain.changes.SnapshotArea
import pl.usagi.edziennik.persistence.changes.OneShotSyncResult
import pl.usagi.edziennik.persistence.changes.SnapshotSynchronizer
import pl.usagi.edziennik.persistence.changes.SyncIssue

class PeriodicSyncRunTest {
    @Test fun countsDetectedEventsAndSuccessfulOutboxHandOffs() = runTest {
        val event = pl.usagi.edziennik.domain.changes.ChangeEvent.NewInformation(
            java.time.LocalDate.of(2026, 10, 13), "Not stored in diagnostics",
        )
        var drains = 0
        val publisher = object : PendingNotificationPublisher {
            override suspend fun publishPending() = Unit
            override suspend fun publishPending(onHandedOff: () -> Unit) {
                drains++
                repeat(if (drains == 1) 2 else 1) { onHandedOff() }
            }
        }
        val counters = SyncRunCounters()
        val result = PeriodicSyncRun({ true }, sync { OneShotSyncResult(listOf(event), emptyMap()) }, publisher).run(counters)
        assertEquals(SyncRunStatus.COMPLETE, result.status)
        assertEquals(1, counters.detectedEvents)
        assertEquals(3, counters.handedOffNotifications)
    }

    @Test fun interruptedSyncDoesNotPretendToKnowDetectedCount() = runTest {
        val counters = SyncRunCounters()
        val result = PeriodicSyncRun({ true }, sync { throw java.io.IOException("not recorded") },
            PendingNotificationPublisher {}).run(counters)
        assertEquals(SyncRunStatus.FAILED, result.status)
        assertEquals(null, counters.detectedEvents)
    }

    @Test fun manualAndPeriodicRunsCannotSyncConcurrently() = runTest {
        var active = 0
        var maximumActive = 0
        var calls = 0
        val runner = PeriodicSyncRun({ true }, sync {
            active++
            maximumActive = maxOf(maximumActive, active)
            calls++
            delay(10)
            active--
            OneShotSyncResult(emptyList(), emptyMap())
        }, PendingNotificationPublisher {})
        val first = launch { runner.run() }
        val second = launch { runner.run() }
        first.join()
        second.join()
        assertEquals(2, calls)
        assertEquals(1, maximumActive)
    }

    @Test fun invokesExistingSyncAndDrainsBeforeAndAfter() = runTest {
        val steps = mutableListOf<String>()
        PeriodicSyncRun({ true }, sync {
            steps += "sync"
            OneShotSyncResult(emptyList(), emptyMap())
        }, PendingNotificationPublisher { steps += "outbox" }).run()
        assertEquals(listOf("outbox", "sync", "outbox"), steps)
    }

    @Test fun noSessionDoesNotSyncButStillDrainsPending() = runTest {
        var calls = 0
        var drains = 0
        PeriodicSyncRun({ false }, sync {
            calls++
            OneShotSyncResult(emptyList(), emptyMap())
        }, PendingNotificationPublisher { drains++ }).run()
        assertEquals(0, calls)
        assertEquals(2, drains)
    }

    @Test fun baselineAndNoChangesStillDrainOlderPendingEvents() = runTest {
        var drains = 0
        val runner = PeriodicSyncRun({ true }, sync { OneShotSyncResult(emptyList(), emptyMap()) },
            PendingNotificationPublisher { drains++ })
        runner.run()
        runner.run()
        assertEquals(4, drains)
    }

    @Test fun partialAreaStillDrainsOutbox() = verifyIssues(SyncIssue.PARTIAL)
    @Test fun failedAreaStillDrainsOutbox() = verifyIssues(SyncIssue.FAILED)

    private fun verifyIssues(issue: SyncIssue) = runTest {
        var drains = 0
        PeriodicSyncRun({ true }, sync {
            OneShotSyncResult(emptyList(), mapOf(SnapshotArea.TASKS to issue))
        }, PendingNotificationPublisher { drains++ }).run()
        assertEquals(2, drains)
    }

    @Test fun deniedNotificationsStillCompleteSynchronization() = runTest {
        var calls = 0
        PeriodicSyncRun({ true }, sync {
            calls++
            OneShotSyncResult(emptyList(), emptyMap())
        }, PendingNotificationPublisher {}).run()
        assertEquals(1, calls)
    }

    @Test fun logoutDuringSyncDoesNotPublishOldAccountEvents() = runTest {
        var current = true
        var drains = 0
        val session = object : SyncSession {
            override suspend fun prepare() = true
            override fun isCurrent() = current
        }
        PeriodicSyncRun(session, sync {
            current = false
            OneShotSyncResult(emptyList(), emptyMap())
        }, PendingNotificationPublisher { drains++ }).run()
        assertEquals(2, drains)
    }

    private fun sync(block: suspend () -> OneShotSyncResult) = object : SnapshotSynchronizer {
        override suspend fun syncOnce() = block()
    }
}
