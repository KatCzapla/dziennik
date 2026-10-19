package pl.usagi.edziennik.background

import android.util.Log

internal fun syncStageDiagnostic(stage: String, result: String) {
    try {
        Log.d("SnapshotSync", "stage=$stage result=$result")
    } catch (_: RuntimeException) {
        // Logging is unavailable in plain JVM tests; never alter synchronization.
    }
}
