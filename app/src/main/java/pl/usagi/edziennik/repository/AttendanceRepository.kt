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

    suspend fun getSubjects(): List<AttendanceSubject>

    suspend fun getStatistics(subjectId: Int): AttendanceStatistics
}
