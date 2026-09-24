package pl.usagi.edziennik.repository

import pl.usagi.edziennik.domain.ActiveStudentManager
import pl.usagi.edziennik.domain.AttendanceEntry
import pl.usagi.edziennik.domain.AttendanceStatistics
import pl.usagi.edziennik.domain.AttendanceSubject
import pl.usagi.edziennik.mapper.toDomain
import pl.usagi.edziennik.mapper.toDomainAttendance
import pl.usagi.edziennik.mapper.toDomainAttendanceSubjects
import pl.usagi.edziennik.network.attendance.UonetAttendanceDataSource
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

class UonetAttendanceRepository @Inject constructor(
    private val activeStudentManager: ActiveStudentManager,
    private val dataSource: UonetAttendanceDataSource,
) : AttendanceRepository {
    override suspend fun getAttendance(
        from: LocalDate,
        to: LocalDate,
    ): List<AttendanceEntry> {
        val activeStudent = activeStudentManager.requireActiveStudent(AttendanceRepositoryException.NoActiveStudent)
        val zoneId = ZoneId.systemDefault()

        return dataSource.getAttendance(
            studentKey = activeStudent.key,
            dateFrom = from.toApiStartOfDay(zoneId),
            dateTo = to.toApiEndOfDay(zoneId),
        ).classEntries.toDomainAttendance()
    }

    override suspend fun getSubjects(): List<AttendanceSubject> {
        val student = activeStudentManager.requireActiveStudent(AttendanceRepositoryException.NoActiveStudent)
        return dataSource.getSubjects(student.key).toDomainAttendanceSubjects()
    }

    override suspend fun getStatistics(subjectId: Int): AttendanceStatistics {
        val student = activeStudentManager.requireActiveStudent(AttendanceRepositoryException.NoActiveStudent)
        return dataSource.getStatistics(student.key, subjectId).toDomain()
    }
}

sealed class AttendanceRepositoryException(message: String) : Exception(message) {
    data object NoActiveStudent : AttendanceRepositoryException("No active student selected.")
}
