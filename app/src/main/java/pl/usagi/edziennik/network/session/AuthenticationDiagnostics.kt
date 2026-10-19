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

internal fun Response.safeHttpDiagnostic(): String {
    var redirects = 0
    var previous = priorResponse
    while (previous != null) {
        if (previous.isRedirect) redirects++
        previous = previous.priorResponse
    }
    val page = when {
        request.url.encodedPath == "/lublin/api/Context" -> "CONTEXT"
        request.url.host == "logowanie.edu.lublin.eu" &&
            request.url.encodedPath.contains("/LoginPage.aspx") -> "PROJECT_LOGIN"
        request.url.host == "eduvulcan.pl" &&
            request.url.encodedPath.startsWith("/logowanie") -> "EDUVULCAN_LOGIN"
        request.url.encodedPath == "/lublin/" -> "APPLICATION_ROOT"
        else -> "OTHER"
    }
    return "${safeLoginPageDiagnostic()} redirects=$redirects page=$page"
}

internal fun Response.httpDiagnostic(stage: String, inspectForm: Boolean = false) {
    try {
        authenticationDiagnostic(stage, safeHttpDiagnostic())
        if (inspectForm) {
            // Peek the entire response without consuming the body used by authentication.
            val diagnostic = inspectHtmlDocument(peekBody(Long.MAX_VALUE).string())
            authenticationDiagnostic("${stage}_ELEMENTS", diagnostic.safeSummary())
        }
    } catch (_: Exception) {
        authenticationDiagnostic(stage, "DIAGNOSTIC_UNAVAILABLE")
    }
}

internal fun sessionCheckDiagnostic(result: SessionCheck, reason: String, response: Response? = null) {
    try {
        val metadata = response?.safeHttpDiagnostic()?.let { " $it" }.orEmpty()
        Log.d("UonetAuth", "stage=SESSION_CHECK result=${result.name} reason=$reason$metadata")
    } catch (_: RuntimeException) {
        // Do not log response bodies, URLs, arbitrary headers or exception messages.
    }
}
