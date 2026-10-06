package pl.usagi.edziennik.repository

import pl.usagi.edziennik.domain.AttendanceEntry
import pl.usagi.edziennik.domain.AttendanceStatistics
import pl.usagi.edziennik.domain.AttendanceSubject
import java.time.LocalDate

interface AttendanceRepository {
    suspend fun getAttendance(
        from: LocalDate,
        to: LocalDate,
    ): List<AttendanceEntry>

    suspend fun getAttendanceWithCompleteness(from: LocalDate, to: LocalDate): AttendanceFetchResult =
        AttendanceFetchResult(getAttendance(from, to), excusesComplete = true)

    suspend fun getSubjects(): List<AttendanceSubject>

    suspend fun getStatistics(subjectId: Int): AttendanceStatistics

    suspend fun submitExcuse(date: LocalDate, lessonTimeId: Int?, content: String) =
        submitExcuses(listOf(AttendanceExcuseRequest(date, lessonTimeId)), content)

    suspend fun submitExcuses(items: List<AttendanceExcuseRequest>, content: String) {
        error("Submitting attendance excuses is not supported")
    }
}

data class AttendanceExcuseRequest(val date: LocalDate, val lessonTimeId: Int?)

data class AttendanceFetchResult(
    val entries: List<AttendanceEntry>,
    val excusesComplete: Boolean,
    val entriesComplete: Boolean = true,
    val excusesActive: Boolean? = null,
)
