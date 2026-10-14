package pl.usagi.edziennik.background

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import pl.usagi.edziennik.widget.WidgetUpdater
import pl.usagi.edziennik.widget.updateSafely
import javax.inject.Inject

class ManualWidgetSyncRun @Inject constructor(
    private val syncRun: PeriodicSyncRun,
    private val widgetUpdater: WidgetUpdater,
) {
    suspend fun run(counters: SyncRunCounters = SyncRunCounters()): SyncRunResult {
        try {
            widgetUpdater.updateSafely()
            return syncRun.run(counters)
        } finally {
            withContext(NonCancellable) {
                widgetUpdater.updateSafely()
            }
        }
    }
}
