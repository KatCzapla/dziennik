package pl.usagi.edziennik.widget

import kotlinx.coroutines.CancellationException
import pl.usagi.edziennik.persistence.changes.LocalTimetableReader
import pl.usagi.edziennik.persistence.changes.LocalTimetableResult
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

class TodayTimetableContentReader @Inject constructor(private val reader: LocalTimetableReader) {
    suspend fun read(
        instant: Instant = Instant.now(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): TodayTimetablePresentation {
        val today = instant.atZone(zoneId).toLocalDate()
        return try {
            presentTodayTimetable(reader.read(today), today, zoneId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            presentTodayTimetable(LocalTimetableResult.NoBaseline, today, zoneId)
                .copy(message = "Nie udało się odczytać planu.")
        }
    }
}
