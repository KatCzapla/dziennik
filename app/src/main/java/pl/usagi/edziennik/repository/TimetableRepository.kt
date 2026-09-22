package pl.usagi.edziennik.repository

import pl.usagi.edziennik.domain.TimetableEntry
import java.time.LocalDate

interface TimetableRepository {
    suspend fun getTimetable(
        from: LocalDate,
        to: LocalDate,
    ): List<TimetableEntry>
}
