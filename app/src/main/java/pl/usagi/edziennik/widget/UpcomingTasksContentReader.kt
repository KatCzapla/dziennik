package pl.usagi.edziennik.widget

import kotlinx.coroutines.CancellationException
import pl.usagi.edziennik.persistence.changes.LocalTasksReader
import pl.usagi.edziennik.persistence.changes.LocalTasksResult
import pl.usagi.edziennik.persistence.changes.TaskRangeCoverage
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

class UpcomingTasksContentReader @Inject constructor(private val reader: LocalTasksReader) {
    suspend fun read(
        instant: Instant = Instant.now(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): UpcomingTasksPresentation {
        val today = instant.atZone(zoneId).toLocalDate()
        return try {
            presentUpcomingTasks(reader.read(today), zoneId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            presentUpcomingTasks(LocalTasksResult(today, today.plusDays(13), emptyList(),
                TaskRangeCoverage.NO_BASELINE, emptyList()), zoneId)
                .copy(message = "Nie udało się odczytać sprawdzianów i zadań.")
        }
    }
}
