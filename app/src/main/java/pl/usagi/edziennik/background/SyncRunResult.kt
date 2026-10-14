package pl.usagi.edziennik.background

import androidx.work.ListenableWorker
import androidx.work.workDataOf
import pl.usagi.edziennik.persistence.changes.OneShotSyncResult

enum class SyncRunStatus { COMPLETE, PARTIAL, FAILED }

class SyncRunCounters {
    var detectedEvents: Int? = 0
    var handedOffNotifications: Int = 0
}

data class SyncRunResult(
    val status: SyncRunStatus,
    val retryable: Boolean = false,
    val reason: String = "NONE",
) {
    fun workResult(): ListenableWorker.Result {
        val output = workDataOf("sync_status" to status.name, "sync_reason" to reason)
        return when {
            retryable -> ListenableWorker.Result.retry()
            status == SyncRunStatus.FAILED -> ListenableWorker.Result.failure(output)
            else -> ListenableWorker.Result.success(output)
        }
    }
}

fun OneShotSyncResult.runResult(): SyncRunResult {
    if (issues.isEmpty()) return SyncRunResult(SyncRunStatus.COMPLETE)
    val cause = failureCauses.values.firstOrNull { it.retryable }
    return SyncRunResult(
        status = if (successfulAreas.isEmpty()) SyncRunStatus.FAILED else SyncRunStatus.PARTIAL,
        retryable = cause != null,
        reason = cause?.name ?: if (successfulAreas.isEmpty()) "ALL_AREAS_FAILED" else "INCOMPLETE_AREAS",
    )
}
