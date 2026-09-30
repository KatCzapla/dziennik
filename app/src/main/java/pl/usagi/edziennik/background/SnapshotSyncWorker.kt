package pl.usagi.edziennik.background

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import pl.usagi.edziennik.persistence.changes.SnapshotSynchronizer
import javax.inject.Inject

class PeriodicSyncRun @Inject constructor(
    private val session: SyncSession,
    private val sync: SnapshotSynchronizer,
    private val notifier: ChangeNotifier,
) {
    suspend fun run() {
        if (!session.prepare()) return
        val events = sync.syncOnce().events
        if (events.isNotEmpty() && session.isCurrent()) notifier.publish(events)
    }
}

@HiltWorker
class SnapshotSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted parameters: WorkerParameters,
    private val syncRun: PeriodicSyncRun,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = try {
        syncRun.run()
        Result.success()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        Result.retry()
    }
}
