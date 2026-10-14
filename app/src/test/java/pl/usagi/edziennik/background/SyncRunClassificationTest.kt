package pl.usagi.edziennik.background

import android.app.Application
import androidx.work.ListenableWorker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pl.usagi.edziennik.domain.changes.SnapshotArea
import pl.usagi.edziennik.persistence.changes.OneShotSyncResult
import pl.usagi.edziennik.persistence.changes.SnapshotSynchronizer
import pl.usagi.edziennik.persistence.changes.SyncFailureCause
import pl.usagi.edziennik.persistence.changes.SyncIssue
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class SyncRunClassificationTest {
    @Test fun successfulSyncWithoutChangesReturnsWorkSuccess() = runTest {
        val result = execute { OneShotSyncResult(emptyList(), emptyMap()) }
        assertEquals(SyncRunStatus.COMPLETE, result.status)
        assertEquals(ListenableWorker.Result.success().javaClass, result.workResult().javaClass)
    }

    @Test fun allFailedAreasDoNotReturnSuccess() = runTest {
        val result = execute { OneShotSyncResult(emptyList(), SnapshotArea.entries.associateWith { SyncIssue.FAILED }) }
        assertEquals(SyncRunStatus.FAILED, result.status)
        assertEquals(ListenableWorker.Result.failure().javaClass, result.workResult().javaClass)
    }

    @Test fun allFailedWithNetworkCauseReturnRetry() = runTest {
        val result = execute {
            OneShotSyncResult(emptyList(), SnapshotArea.entries.associateWith { SyncIssue.FAILED },
                mapOf(SnapshotArea.MESSAGES to SyncFailureCause.NETWORK))
        }
        assertEquals(SyncRunStatus.FAILED, result.status)
        assertEquals(ListenableWorker.Result.retry().javaClass, result.workResult().javaClass)
    }

    @Test fun partialWithoutTransientFailureReturnsSuccessMarkedPartial() = runTest {
        val result = execute { OneShotSyncResult(emptyList(), mapOf(SnapshotArea.TASKS to SyncIssue.PARTIAL)) }
        assertEquals(SyncRunStatus.PARTIAL, result.status)
        assertFalse(result.retryable)
        assertEquals(ListenableWorker.Result.success().javaClass, result.workResult().javaClass)
    }

    @Test fun partialWithServerFailureRequestsRetryWithoutCallingSyncTwice() = runTest {
        var calls = 0
        val result = execute {
            calls++
            OneShotSyncResult(emptyList(), mapOf(SnapshotArea.GRADES to SyncIssue.FAILED),
                mapOf(SnapshotArea.GRADES to SyncFailureCause.SERVER))
        }
        assertEquals(SyncRunStatus.PARTIAL, result.status)
        assertTrue(result.retryable)
        assertEquals(1, calls)
    }

    @Test fun successfulTaskWeekStillMeansPartialIfAnotherTaskWeekFailed() {
        val result = OneShotSyncResult(emptyList(), SnapshotArea.entries.associateWith { SyncIssue.FAILED },
            successfulAreas = setOf(SnapshotArea.TASKS)).runResult()
        assertEquals(SyncRunStatus.PARTIAL, result.status)
    }

    @Test fun networkExceptionRequestsRetry() = runTest {
        val result = execute { throw IOException("test transport failure") }
        assertEquals("NETWORK", result.reason)
        assertTrue(result.retryable)
    }

    @Test fun permanentExceptionDoesNotRetry() = runTest {
        val result = execute { error("Invalid contract") }
        assertEquals(SyncRunStatus.FAILED, result.status)
        assertFalse(result.retryable)
    }

    @Test fun cancellationIsNotConvertedToSuccessOrRetry() = runTest {
        var cancelled = false
        try { execute { throw CancellationException() } } catch (_: CancellationException) { cancelled = true }
        assertTrue(cancelled)
    }

    @Test fun earlierOutboxIsAttemptedDespiteFetchingFailure() = runTest {
        var publications = 0
        val sync = object : SnapshotSynchronizer {
            override suspend fun syncOnce(): OneShotSyncResult = throw IOException()
        }
        val result = PeriodicSyncRun({ true }, sync, PendingNotificationPublisher { publications++ }).run()
        assertTrue(result.retryable)
        assertEquals(2, publications)
    }

    @Test fun earlierOutboxIsAttemptedDespiteUnavailableSession() = runTest {
        var publications = 0
        val sync = object : SnapshotSynchronizer {
            override suspend fun syncOnce(): OneShotSyncResult = error("Must not fetch")
        }
        val result = PeriodicSyncRun({ false }, sync, PendingNotificationPublisher { publications++ }).run()
        assertEquals(SyncRunStatus.FAILED, result.status)
        assertEquals(2, publications)
    }

    @Test fun outboxStorageFailureDoesNotBlockDataSynchronization() = runTest {
        var calls = 0
        val sync = object : SnapshotSynchronizer {
            override suspend fun syncOnce(): OneShotSyncResult {
                calls++
                return OneShotSyncResult(emptyList(), emptyMap())
            }
        }
        val result = PeriodicSyncRun({ true }, sync, PendingNotificationPublisher { error("storage failure") }).run()
        assertEquals(SyncRunStatus.COMPLETE, result.status)
        assertEquals(1, calls)
    }

    private suspend fun execute(block: suspend () -> OneShotSyncResult): SyncRunResult =
        PeriodicSyncRun({ true }, object : SnapshotSynchronizer {
            override suspend fun syncOnce() = block()
        }, PendingNotificationPublisher {}).run()
}
