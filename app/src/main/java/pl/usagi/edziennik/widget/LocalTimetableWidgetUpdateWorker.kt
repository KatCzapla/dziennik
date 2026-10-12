package pl.usagi.edziennik.widget

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

internal fun enqueueLocalTimetableWidgetUpdate(context: Context, waitForManualFinish: Boolean = false) {
    val name = if (waitForManualFinish) "timetable_widget_refresh_completion" else "timetable_widget_local_update"
    val request = OneTimeWorkRequestBuilder<LocalTimetableWidgetUpdateWorker>()
        .setInputData(workDataOf(LocalTimetableWidgetUpdateWorker.WAIT_FOR_MANUAL_FINISH to waitForManualFinish))
        .build()
    WorkManager.getInstance(context).enqueueUniqueWork(name, ExistingWorkPolicy.KEEP, request)
}

@HiltWorker
class LocalTimetableWidgetUpdateWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted parameters: WorkerParameters,
    private val updater: WidgetUpdater,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = try {
        if (inputData.getBoolean(WAIT_FOR_MANUAL_FINISH, false)) {
            WorkManager.getInstance(applicationContext).manualWidgetRefreshActive().first { !it }
        }
        updater.update()
        Result.success()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        Result.retry()
    }

    companion object {
        const val WAIT_FOR_MANUAL_FINISH = "wait_for_manual_finish"
    }
}
