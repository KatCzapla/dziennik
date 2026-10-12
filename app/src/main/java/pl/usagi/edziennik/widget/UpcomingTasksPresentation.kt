package pl.usagi.edziennik.widget

import pl.usagi.edziennik.persistence.changes.LocalTasksResult
import pl.usagi.edziennik.persistence.changes.TaskRangeCoverage
import pl.usagi.edziennik.ui.navigation.SchoolTaskNavigationTarget
import pl.usagi.edziennik.ui.schooltasks.typeLabel
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class UpcomingTaskItem(
    val dateLabel: String,
    val subject: String,
    val typeLabel: String,
    val description: String?,
    val target: SchoolTaskNavigationTarget,
)

data class UpcomingTasksPresentation(
    val rangeLabel: String,
    val items: List<UpcomingTaskItem>,
    val coverage: TaskRangeCoverage,
    val message: String?,
    val lastCompleteLabel: String?,
)

private val polishTasks = Locale.forLanguageTag("pl-PL")
private val taskDateFormat = DateTimeFormatter.ofPattern("d MMM", polishTasks)
private val taskYearDateFormat = DateTimeFormatter.ofPattern("d MMM yyyy", polishTasks)
private val taskSyncFormat = DateTimeFormatter.ofPattern("d MMM, HH:mm", polishTasks)
private val taskWhitespace = Regex("[\\s\\p{Z}]+")

internal fun shortWidgetTaskDescription(value: String?): String? {
    val normalized = value?.replace(taskWhitespace, " ")?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return if (normalized.length <= 100) normalized else normalized.take(100).trimEnd() + "..."
}

fun presentUpcomingTasks(result: LocalTasksResult, zoneId: ZoneId): UpcomingTasksPresentation {
    val rangeFormat = if (result.from.year == result.to.year) taskDateFormat else taskYearDateFormat
    val items = if (result.coverage == TaskRangeCoverage.NO_BASELINE) emptyList() else result.tasks
        .filter { it.date in result.from..result.to }
        .sortedWith(compareBy({ it.date }, { it.key.rawType }, { it.key.id }))
        .map { task ->
            UpcomingTaskItem(
                dateLabel = task.date.format(taskDateFormat),
                subject = task.subject?.replace(taskWhitespace, " ")?.trim()?.takeIf { it.isNotEmpty() } ?: "Bez przedmiotu",
                typeLabel = task.type.typeLabel(),
                description = shortWidgetTaskDescription(task.description),
                target = SchoolTaskNavigationTarget(task.key.id, task.key.rawType, task.date),
            )
        }
    val savedWeeks = result.weeks.filter { it.coverage != TaskRangeCoverage.NO_BASELINE }
    val times = savedWeeks.map { it.lastCompleteAt }
    val timestamp = when (result.coverage) {
        TaskRangeCoverage.NO_BASELINE -> null
        TaskRangeCoverage.FULL -> if (times.isNotEmpty() && times.all { it != null }) times.filterNotNull().minOrNull() else null
        TaskRangeCoverage.PARTIAL -> times.filterNotNull().minOrNull()
    }
    val timestampLabel = timestamp?.atZone(zoneId)?.format(taskSyncFormat)?.let { time ->
        if (result.coverage == TaskRangeCoverage.FULL) "Zsynchronizowano $time" else "Zapis części danych: $time"
    }
    val message = when (result.coverage) {
        TaskRangeCoverage.NO_BASELINE -> "Sprawdziany i zadania nie zostały jeszcze zsynchronizowane."
        TaskRangeCoverage.PARTIAL -> if (items.isEmpty()) "Niepełne dane. Brak zadań w dostępnych zapisach."
            else "Niepełne dane dla najbliższych 14 dni."
        TaskRangeCoverage.FULL -> if (items.isEmpty()) "Brak sprawdzianów i zadań w najbliższych 14 dniach." else null
    }
    return UpcomingTasksPresentation("${result.from.format(rangeFormat)} - ${result.to.format(rangeFormat)}",
        items, result.coverage, message, timestampLabel)
}

internal fun visibleTaskCount(heightDp: Float, fontScale: Float, hasMessage: Boolean, refreshing: Boolean): Int {
    val scale = fontScale.coerceAtLeast(1f)
    val reserved = 32f + (112f + if (hasMessage) 32f else 0f) * scale + if (refreshing) 16f * scale else 0f
    return ((heightDp - reserved) / (64f * scale)).toInt().coerceIn(0, 4)
}
