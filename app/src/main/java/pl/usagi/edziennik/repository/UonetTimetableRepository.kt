package pl.usagi.edziennik.repository

import pl.usagi.edziennik.domain.ActiveStudentManager
import pl.usagi.edziennik.domain.TimetableEntry
import pl.usagi.edziennik.domain.SchoolDayOff
import pl.usagi.edziennik.mapper.toDomain
import pl.usagi.edziennik.mapper.toDomainTimetable
import pl.usagi.edziennik.network.timetable.UonetTimetableDataSource
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

class UonetTimetableRepository @Inject constructor(
    private val activeStudentManager: ActiveStudentManager,
    private val dataSource: UonetTimetableDataSource,
) : TimetableRepository {
    override suspend fun getDaysOff(from: LocalDate, to: LocalDate): List<SchoolDayOff> {
        val student = activeStudentManager.requireActiveStudent(TimetableRepositoryException.NoActiveStudent)
        val zoneId = ZoneId.systemDefault()
        return dataSource.getDaysOff(student.key, from.toApiStartOfDay(zoneId), to.toApiEndOfDay(zoneId))
            .map { it.toDomain() }
    }

    override suspend fun getTimetable(
        from: LocalDate,
        to: LocalDate,
    ): List<TimetableEntry> {
        val activeStudent = activeStudentManager.requireActiveStudent(TimetableRepositoryException.NoActiveStudent)
        val zoneId = ZoneId.systemDefault()

        return dataSource.getTimetable(
            studentKey = activeStudent.key,
            dateFrom = from.toApiStartOfDay(zoneId),
            dateTo = to.toApiEndOfDay(zoneId),
        ).toDomainTimetable()
    }
}

sealed class TimetableRepositoryException(message: String) : Exception(message) {
    data object NoActiveStudent : TimetableRepositoryException("No active student selected.")
}
