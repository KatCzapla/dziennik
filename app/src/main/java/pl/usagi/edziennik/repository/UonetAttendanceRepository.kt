package pl.usagi.edziennik.repository

import kotlinx.coroutines.CancellationException
import pl.usagi.edziennik.domain.ActiveStudentManager
import pl.usagi.edziennik.domain.AttendanceEntry
import pl.usagi.edziennik.domain.AttendanceExcuse
import pl.usagi.edziennik.domain.AttendanceStatistics
import pl.usagi.edziennik.domain.AttendanceSubject
import pl.usagi.edziennik.mapper.toDomain
import pl.usagi.edziennik.mapper.toDomainAttendance
import pl.usagi.edziennik.mapper.toDomainAttendanceSubjects
import pl.usagi.edziennik.mapper.toDomainExcuses
import pl.usagi.edziennik.mapper.withExcuses
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
    ): List<AttendanceEntry> = getAttendanceWithCompleteness(from, to).entries

    override suspend fun getAttendanceWithCompleteness(
        from: LocalDate,
        to: LocalDate,
    ): AttendanceFetchResult {
        val activeStudent = activeStudentManager.requireActiveStudent(AttendanceRepositoryException.NoActiveStudent)
        val zoneId = ZoneId.systemDefault()
        val dateFrom = from.toApiStartOfDay(zoneId)
        val dateTo = to.toApiEndOfDay(zoneId)

        val classEntries = dataSource.getAttendance(
            studentKey = activeStudent.key,
            dateFrom = dateFrom,
            dateTo = dateTo,
        ).classEntries
        val entries = classEntries.toDomainAttendance()

        val (excuses, excusesComplete) = try {
            val response = dataSource.getExcuses(
                studentKey = activeStudent.key,
                dateFrom = dateFrom,
                dateTo = dateTo,
            )
            val mapped = response.toDomainExcuses()
            mapped to (mapped.size == response.excuses.size)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            emptyList<AttendanceExcuse>() to false
        }

        return AttendanceFetchResult(entries.withExcuses(excuses), excusesComplete, entries.size == classEntries.size)
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
