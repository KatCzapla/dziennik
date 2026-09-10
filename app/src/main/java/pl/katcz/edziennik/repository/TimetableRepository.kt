package pl.katcz.edziennik.repository

import pl.katcz.edziennik.domain.TimetableEntry
import java.time.LocalDate

interface TimetableRepository {
    suspend fun getTimetable(
        from: LocalDate,
        to: LocalDate,
    ): List<TimetableEntry>
}
