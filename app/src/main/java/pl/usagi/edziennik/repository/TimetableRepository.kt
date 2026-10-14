package pl.usagi.edziennik.repository

import pl.usagi.edziennik.domain.TimetableEntry
import pl.usagi.edziennik.domain.SchoolDayOff
import java.time.LocalDate

interface TimetableRepository {
    suspend fun getDaysOff(from: LocalDate, to: LocalDate): List<SchoolDayOff> = emptyList()

    suspend fun getTimetable(
        from: LocalDate,
        to: LocalDate,
    ): List<TimetableEntry>
}
