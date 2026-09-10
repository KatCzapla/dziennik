package pl.katcz.edziennik.repository

import pl.katcz.edziennik.domain.ActiveStudentManager
import pl.katcz.edziennik.domain.TimetableEntry
import pl.katcz.edziennik.domain.time.atEndOfDayIn
import pl.katcz.edziennik.domain.time.atStartOfDayIn
import pl.katcz.edziennik.domain.time.toApiDateTime
import pl.katcz.edziennik.mapper.toDomainTimetable
import pl.katcz.edziennik.network.timetable.UonetTimetableDataSource
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

class UonetTimetableRepository @Inject constructor(
    private val activeStudentManager: ActiveStudentManager,
    private val dataSource: UonetTimetableDataSource,
) : TimetableRepository {
    override suspend fun getTimetable(
        from: LocalDate,
        to: LocalDate,
    ): List<TimetableEntry> {
        val activeStudent = activeStudentManager.activeStudent.value
            ?: throw TimetableRepositoryException.NoActiveStudent
        val zoneId = ZoneId.systemDefault()

        return dataSource.getTimetable(
            studentKey = activeStudent.key,
            dateFrom = toApiDateTime(from.atStartOfDayIn(zoneId)),
            dateTo = toApiDateTime(to.atEndOfDayIn(zoneId)),
        ).toDomainTimetable()
    }
}

sealed class TimetableRepositoryException(message: String) : Exception(message) {
    data object NoActiveStudent : TimetableRepositoryException("No active student selected.")
}
