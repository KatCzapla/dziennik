package pl.usagi.edziennik.background

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject

interface SyncScheduler {
    fun schedule()
    fun cancel()
}

class PeriodicSyncScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : SyncScheduler {
    override fun schedule() {
        val request = PeriodicWorkRequestBuilder<SnapshotSyncWorker>(30, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            POLICY,
            request,
        )
    }

    override fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    companion object {
        const val WORK_NAME = "snapshot_sync"
        val POLICY = ExistingPeriodicWorkPolicy.KEEP
    }
}
