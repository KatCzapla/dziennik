package pl.usagi.edziennik.network.messages

import android.util.Log
import okhttp3.HttpUrl
import okhttp3.Response

internal object MessagesDiagnostics {
    private const val TAG = "UonetMessages"

    fun debug(message: String) {
        try {
            Log.d(TAG, message)
        } catch (_: RuntimeException) {
            // android.util.Log is unavailable in plain JVM unit tests.
        }
    }

    fun warn(message: String) {
        try {
            Log.w(TAG, message)
        } catch (_: RuntimeException) {
            // android.util.Log is unavailable in plain JVM unit tests.
        }
    }
}

internal fun HttpUrl.safeHostAndPath(): String =
    "host=$host, path=$encodedPath"

internal fun Response.safeSummary(): String =
    "host=${request.url.host}, path=${request.url.encodedPath}, status=$code, contentType=${header("Content-Type").orEmpty()}"
