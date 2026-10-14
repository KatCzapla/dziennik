package pl.usagi.edziennik.network.session

import android.util.Log
import okhttp3.Response

internal fun Response.safeLoginPageDiagnostic(): String {
    val target = when (request.url.host) {
        "projekt-uczen.edu.lublin.eu" -> "PROJECT_UONET"
        "logowanie.edu.lublin.eu" -> "PROJECT_LOGIN"
        "eduvulcan.pl" -> "EDUVULCAN"
        "uczen.edu.lublin.eu" -> "EDUVULCAN_UONET"
        else -> "OTHER"
    }
    val content = header("Content-Type").orEmpty().let {
        when {
            it.contains("text/html", ignoreCase = true) -> "HTML"
            it.contains("application/json", ignoreCase = true) -> "JSON"
            else -> "OTHER"
        }
    }
    return "http=$code target=$target content=$content"
}

internal fun authenticationDiagnostic(stage: String, result: String) {
    try {
        Log.w("UonetAuth", "stage=$stage result=$result")
    } catch (_: RuntimeException) {
        // Android logging is unavailable in plain JVM tests.
    }
}
