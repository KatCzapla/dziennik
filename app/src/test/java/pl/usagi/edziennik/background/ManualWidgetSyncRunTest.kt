package pl.usagi.edziennik.background

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import pl.usagi.edziennik.domain.changes.ChangeEvent
import pl.usagi.edziennik.domain.changes.SnapshotArea
import pl.usagi.edziennik.persistence.changes.OneShotSyncResult
import pl.usagi.edziennik.persistence.changes.SnapshotSynchronizer
import pl.usagi.edziennik.persistence.changes.SyncIssue
import pl.usagi.edziennik.widget.TimetableWidgetUpdater
import java.time.LocalDate

class ManualWidgetSyncRunTest {
    private var updates = 0
    private val updater = TimetableWidgetUpdater { updates++ }
    private var syncCalls = 0
    private var prepareCalls = 0
    private val published = mutableListOf<ChangeEvent>()

    @Test fun runsExistingSessionPreparationAndSyncExactlyOnce() = runTest {
        runner {
            OneShotSyncResult(emptyList(), emptyMap())
        }.run()
        assertEquals(1, prepareCalls)
        assertEquals(1, syncCalls)
        assertEquals(2, updates)
    }

    @Test fun unavailableSessionClearsRefreshingWithoutFetching() = runTest {
        runner(available = false) { error("Must not fetch") }.run()
        assertEquals(0, syncCalls)
        assertEquals(2, updates)
    }

    @Test fun syncFailureClearsRefreshingAndRedrawsSavedData() = runTest {
        val result = runner { error("Sync failed") }.run()
        assertEquals(SyncRunStatus.FAILED, result.status)
        assertEquals(2, updates)
    }

    @Test fun cancellationAlsoClearsRefreshing() = runTest {
        try {
            runner { throw CancellationException() }.run()
            error("Expected cancellation")
        } catch (_: CancellationException) {
            assertEquals(2, updates)
        }
    }

    @Test fun partialSyncPublishesExistingEventsOnce() = runTest {
        val event = ChangeEvent.NewInformation(LocalDate.of(2026, 10, 12), "Informacja")
        val run = runner {
            OneShotSyncResult(if (syncCalls == 1) listOf(event) else emptyList(),
                mapOf(SnapshotArea.TASKS to SyncIssue.PARTIAL))
        }
        run.run()
        run.run()
        assertEquals(listOf(event), published)
        assertEquals(4, updates)
    }

    @Test fun failedTimetableDoesNotBlockOtherAreaEventsOrLeaveRefreshing() = runTest {
        val event = ChangeEvent.NewInformation(LocalDate.of(2026, 10, 12), "Informacja")
        runner { OneShotSyncResult(listOf(event), mapOf(SnapshotArea.TIMETABLE to SyncIssue.FAILED)) }.run()
        assertEquals(listOf(event), published)
        assertEquals(2, updates)
    }

    private fun runner(available: Boolean = true, block: suspend () -> OneShotSyncResult): ManualWidgetSyncRun {
        val session = SyncSession { prepareCalls++; available }
        val pending = mutableListOf<ChangeEvent>()
        val sync = object : SnapshotSynchronizer {
            override suspend fun syncOnce(): OneShotSyncResult {
                syncCalls++
                return block().also { pending += it.events }
            }
        }
        val notifier = PendingNotificationPublisher {
            published += pending
            pending.clear()
        }
        return ManualWidgetSyncRun(PeriodicSyncRun(session, sync, notifier), updater)
    }
}
