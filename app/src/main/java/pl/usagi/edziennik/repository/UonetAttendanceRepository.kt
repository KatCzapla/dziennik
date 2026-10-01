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
import pl.usagi.edziennik.dto.SubmitAttendanceExcuseDto
import pl.usagi.edziennik.dto.SubmitAttendanceExcuseLessonDto
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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

        val (excuses, excusesComplete, excusesActive) = try {
            val response = dataSource.getExcuses(
                studentKey = activeStudent.key,
                dateFrom = dateFrom,
                dateTo = dateTo,
            )
            val mapped = response.toDomainExcuses()
            Triple(mapped, mapped.size == response.excuses.size, response.active)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Triple(emptyList<AttendanceExcuse>(), false, null)
        }

        return AttendanceFetchResult(
            entries.withExcuses(excuses), excusesComplete, entries.size == classEntries.size, excusesActive,
        )
    }

    override suspend fun submitExcuse(date: LocalDate, lessonTimeId: Int, content: String) {
        require(content.codePointCount(0, content.length) <= 256) { "Excuse content exceeds 256 characters" }
        val student = activeStudentManager.requireActiveStudent(AttendanceRepositoryException.NoActiveStudent)
        dataSource.submitExcuse(
            SubmitAttendanceExcuseDto(
                studentKey = student.key,
                content = content,
                excuses = listOf(
                    SubmitAttendanceExcuseLessonDto(
                        date.atStartOfDay(ZoneId.of("Europe/Warsaw")).format(EXCUSE_DAY_FORMAT),
                        lessonTimeId,
                    ),
                ),
            ),
        )
    }

    override suspend fun getSubjects(): List<AttendanceSubject> {
        val student = activeStudentManager.requireActiveStudent(AttendanceRepositoryException.NoActiveStudent)
        return dataSource.getSubjects(student.key).toDomainAttendanceSubjects()
    }

    override suspend fun getStatistics(subjectId: Int): AttendanceStatistics {
        val student = activeStudentManager.requireActiveStudent(AttendanceRepositoryException.NoActiveStudent)
        return dataSource.getStatistics(student.key, subjectId).toDomain()
    }

    private companion object {
        val EXCUSE_DAY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX")
    }
}

sealed class AttendanceRepositoryException(message: String) : Exception(message) {
    data object NoActiveStudent : AttendanceRepositoryException("No active student selected.")
}
