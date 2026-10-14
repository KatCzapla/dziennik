package pl.usagi.edziennik.persistence.changes

import pl.usagi.edziennik.network.UonetApiClient
import pl.usagi.edziennik.network.attendance.AttendanceFetchException
import pl.usagi.edziennik.network.context.ContextFetchException
import pl.usagi.edziennik.network.grades.GradesFetchException
import pl.usagi.edziennik.network.messages.MessagesSessionException
import pl.usagi.edziennik.network.schooltasks.SchoolTasksFetchException
import pl.usagi.edziennik.network.timetable.TimetableFetchException
import java.io.IOException

enum class SyncFailureCause(val retryable: Boolean = false) {
    NETWORK(true), SERVER(true), SESSION_EXPIRED(true), UNAVAILABLE, PERMANENT,
}

fun Throwable.syncFailureCause(): SyncFailureCause {
    if (generateSequence(this) { it.cause }.any { it is IOException }) return SyncFailureCause.NETWORK
    val status = when (this) {
        is UonetApiClient.Failure.Http -> statusCode
        is AttendanceFetchException.Http -> statusCode
        is ContextFetchException.Http -> statusCode
        is GradesFetchException.Http -> statusCode
        is MessagesSessionException.Http -> statusCode
        is SchoolTasksFetchException.Http -> statusCode
        is TimetableFetchException.Http -> statusCode
        else -> return SyncFailureCause.PERMANENT
    }
    return when {
        status == 401 -> SyncFailureCause.SESSION_EXPIRED
        status in setOf(408, 429) || status >= 500 -> SyncFailureCause.SERVER
        else -> SyncFailureCause.PERMANENT
    }
}
