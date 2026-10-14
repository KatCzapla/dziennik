package pl.usagi.edziennik.ui.settings

import androidx.work.WorkInfo
import pl.usagi.edziennik.R

fun WorkInfo.State.labelResource(): Int = when (this) {
    WorkInfo.State.ENQUEUED -> R.string.sync_work_enqueued
    WorkInfo.State.RUNNING -> R.string.sync_work_running
    WorkInfo.State.SUCCEEDED -> R.string.sync_work_succeeded
    WorkInfo.State.FAILED -> R.string.sync_work_failed
    WorkInfo.State.BLOCKED -> R.string.sync_work_blocked
    WorkInfo.State.CANCELLED -> R.string.sync_work_cancelled
}

fun diagnosticResultResource(result: String?): Int = when (result) {
    "SUCCESS" -> R.string.sync_result_success
    "PARTIAL" -> R.string.sync_result_partial
    "RETRY" -> R.string.sync_result_retry
    "FAILURE" -> R.string.sync_result_failure
    else -> R.string.sync_diagnostics_unfinished
}
