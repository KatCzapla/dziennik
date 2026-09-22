package pl.usagi.edziennik.repository

import pl.usagi.edziennik.domain.AttendanceEntry
import java.time.LocalDate

interface AttendanceRepository {
    suspend fun getAttendance(
        from: LocalDate,
        to: LocalDate,
    ): List<AttendanceEntry>
}
