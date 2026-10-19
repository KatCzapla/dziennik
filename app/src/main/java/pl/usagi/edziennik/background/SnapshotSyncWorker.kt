package pl.usagi.edziennik.background

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import pl.usagi.edziennik.persistence.changes.SnapshotSynchronizer
import pl.usagi.edziennik.persistence.changes.SyncDiagnostics
import pl.usagi.edziennik.persistence.changes.syncFailureCause
import javax.inject.Inject
import javax.inject.Singleton
import pl.usagi.edziennik.widget.enqueueLocalTimetableWidgetUpdate

@Singleton
class PeriodicSyncRun @Inject constructor(
    private val session: SyncSession,
    private val sync: SnapshotSynchronizer,
    private val outbox: PendingNotificationPublisher,
) {
    private val mutex = Mutex()

    suspend fun run(counters: SyncRunCounters = SyncRunCounters()): SyncRunResult = mutex.withLock {
        publishOutboxSafely(counters)
        try {
            val preparation = session.prepareResult()
            syncStageDiagnostic("SESSION_PREPARATION", preparation.name)
            if (preparation != SessionPreparation.READY) {
                return@withLock SyncRunResult(SyncRunStatus.FAILED, preparation.retryable, preparation.name)
            }
            counters.detectedEvents = null
            syncStageDiagnostic("FETCH_DATA", "START")
            val result = sync.syncOnce()
            syncStageDiagnostic("FETCH_DATA", "FINISHED")
            counters.detectedEvents = result.events.size
            if (!session.isCurrent()) SyncRunResult(SyncRunStatus.FAILED, reason = "ACCOUNT_CHANGED")
            else result.runResult()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            val cause = failure.syncFailureCause()
            SyncRunResult(SyncRunStatus.FAILED, cause.retryable, cause.name)
        } finally {
            publishOutboxSafely(counters)
        }
    }

    private suspend fun publishOutboxSafely(counters: SyncRunCounters) {
        try { outbox.publishPending { counters.handedOffNotifications++ } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            try { android.util.Log.w("SnapshotSync", "Outbox publication incomplete; events retained") }
            catch (_: RuntimeException) { }
        }
    }
}

@HiltWorker
class SnapshotSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted parameters: WorkerParameters,
    private val syncRun: PeriodicSyncRun,
    private val manualSyncRun: ManualWidgetSyncRun,
    private val diagnostics: SyncDiagnostics,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = try {
        syncStageDiagnostic("WORKER", if (inputData.getBoolean(MANUAL_REFRESH, false)) "START_MANUAL" else "START_PERIODIC")
        val result = diagnostics.recordRun(inputData.getBoolean(MANUAL_REFRESH, false)) { counters ->
            if (inputData.getBoolean(MANUAL_REFRESH, false)) manualSyncRun.run(counters) else syncRun.run(counters)
        }
        android.util.Log.d("SnapshotSync", "Sync status=${result.status} retryable=${result.retryable} reason=${result.reason}")
        result.workResult()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        val cause = failure.syncFailureCause()
        SyncRunResult(SyncRunStatus.FAILED, cause.retryable, cause.name).workResult()
    } finally {
        if (inputData.getBoolean(MANUAL_REFRESH, false)) {
            enqueueLocalTimetableWidgetUpdate(applicationContext, waitForManualFinish = true)
        }
    }

    companion object {
        const val MANUAL_REFRESH = "manual_widget_refresh"
    }
}
