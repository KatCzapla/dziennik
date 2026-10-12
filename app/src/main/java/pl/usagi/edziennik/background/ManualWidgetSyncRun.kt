package pl.usagi.edziennik.background

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import pl.usagi.edziennik.widget.TimetableWidgetUpdater
import pl.usagi.edziennik.widget.updateSafely
import javax.inject.Inject

class ManualWidgetSyncRun @Inject constructor(
    private val syncRun: PeriodicSyncRun,
    private val widgetUpdater: TimetableWidgetUpdater,
) {
    suspend fun run() {
        try {
            widgetUpdater.updateSafely()
            syncRun.run()
        } finally {
            withContext(NonCancellable) {
                widgetUpdater.updateSafely()
            }
        }
    }
}
