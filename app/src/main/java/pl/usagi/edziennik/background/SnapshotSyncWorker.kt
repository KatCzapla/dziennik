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
import javax.inject.Inject
import javax.inject.Singleton
import pl.usagi.edziennik.widget.enqueueLocalTimetableWidgetUpdate

@Singleton
class PeriodicSyncRun @Inject constructor(
    private val session: SyncSession,
    private val sync: SnapshotSynchronizer,
    private val notifier: ChangeNotifier,
) {
    private val mutex = Mutex()

    suspend fun run() = mutex.withLock {
        if (!session.prepare()) return@withLock
        val events = sync.syncOnce().events
        if (events.isNotEmpty() && session.isCurrent()) notifier.publish(events)
    }
}

@HiltWorker
class SnapshotSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted parameters: WorkerParameters,
    private val syncRun: PeriodicSyncRun,
    private val manualSyncRun: ManualWidgetSyncRun,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = try {
        if (inputData.getBoolean(MANUAL_REFRESH, false)) manualSyncRun.run() else syncRun.run()
        Result.success()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        if (inputData.getBoolean(MANUAL_REFRESH, false)) Result.failure() else Result.retry()
    } finally {
        if (inputData.getBoolean(MANUAL_REFRESH, false)) {
            enqueueLocalTimetableWidgetUpdate(applicationContext, waitForManualFinish = true)
        }
    }

    companion object {
        const val MANUAL_REFRESH = "manual_widget_refresh"
    }
}
