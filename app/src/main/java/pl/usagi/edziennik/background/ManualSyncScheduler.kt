package pl.usagi.edziennik.background

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import androidx.work.await
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

fun interface ManualSyncWorkQueue {
    suspend fun enqueueUnique(name: String, policy: ExistingWorkPolicy)
}

class WorkManagerManualSyncQueue @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : ManualSyncWorkQueue {
    override suspend fun enqueueUnique(name: String, policy: ExistingWorkPolicy) {
        val request = OneTimeWorkRequestBuilder<SnapshotSyncWorker>()
            .setInputData(workDataOf(SnapshotSyncWorker.MANUAL_REFRESH to true))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(name, policy, request).await()
    }
}

class ManualSyncScheduler @Inject constructor(private val queue: ManualSyncWorkQueue) {
    suspend fun refresh() {
        queue.enqueueUnique(WORK_NAME, ExistingWorkPolicy.KEEP)
    }

    companion object {
        const val WORK_NAME = "widget_timetable_refresh"
    }
}
