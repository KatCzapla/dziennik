package pl.usagi.edziennik.persistence.changes

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import pl.usagi.edziennik.domain.SchoolTaskType
import pl.usagi.edziennik.domain.changes.SnapshotArea
import pl.usagi.edziennik.domain.changes.SnapshotPayload
import pl.usagi.edziennik.domain.changes.SnapshotScope
import pl.usagi.edziennik.domain.changes.TaskKey
import pl.usagi.edziennik.domain.time.isWeekday
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

enum class TaskRangeCoverage { FULL, PARTIAL, NO_BASELINE }

data class LocalTask(
    val date: LocalDate,
    val key: TaskKey,
    val type: SchoolTaskType,
    val subject: String?,
    val description: String?,
)

data class LocalTaskWeek(
    val from: LocalDate,
    val to: LocalDate,
    val coverage: TaskRangeCoverage,
    val lastCompleteAt: Instant? = null,
    val failedDetailKeys: Set<TaskKey> = emptySet(),
)

data class LocalTasksResult(
    val from: LocalDate,
    val to: LocalDate,
    val tasks: List<LocalTask>,
    val coverage: TaskRangeCoverage,
    val weeks: List<LocalTaskWeek>,
)

class LocalTasksReader @Inject constructor(
    private val store: SnapshotStore,
    private val identity: LocalSnapshotIdentity,
    private val json: Json,
    private val clock: Clock,
) {
    suspend fun read(start: LocalDate = LocalDate.now(clock)): LocalTasksResult {
        val end = start.plusDays(13)
        val expected = taskSnapshotWeeks(start)
        fun noBaseline() = LocalTasksResult(start, end, emptyList(), TaskRangeCoverage.NO_BASELINE,
            expected.map { LocalTaskWeek(it.from, it.to, TaskRangeCoverage.NO_BASELINE) })

        val accountId = identity.currentAccountId() ?: return noBaseline()
        val studentId = identity.selectedStudentScopeId() ?: return noBaseline()
        val tasks = mutableListOf<LocalTask>()
        val weeks = expected.map { week ->
            val scope = SnapshotScope(accountId, studentId, SnapshotArea.TASKS, week.scopeRange)
            val saved = store.read(scope)
            if (saved == null) {
                LocalTaskWeek(week.from, week.to, TaskRangeCoverage.NO_BASELINE)
            } else {
                val payload = json.decodeFromString<SnapshotPayload>(saved) as SnapshotPayload.Tasks
                val relevant = payload.items.filter { it.date in start..end && it.date in week.from..week.to }
                val relevantKeys = relevant.mapTo(mutableSetOf()) { it.key }
                val failedDetails = payload.failedDetailKeys.intersect(relevantKeys)
                tasks += relevant.map {
                    LocalTask(it.date, it.key, SchoolTaskType.fromRawValue(it.key.rawType), it.subject, it.details?.description)
                }
                val hasWeekend = generateSequence(maxOf(start, week.from)) { it.plusDays(1) }
                    .takeWhile { it <= minOf(end, week.to) }.any { !it.isWeekday() }
                val coverage = if ((!hasWeekend || payload.calendarRangeComplete) && failedDetails.isEmpty())
                    TaskRangeCoverage.FULL else TaskRangeCoverage.PARTIAL
                LocalTaskWeek(week.from, week.to, coverage,
                    payload.lastCompleteAtEpochMillis?.let(Instant::ofEpochMilli), failedDetails)
            }
        }
        if (identity.currentAccountId() != accountId || identity.selectedStudentScopeId() != studentId) return noBaseline()
        val coverage = when {
            weeks.isEmpty() -> TaskRangeCoverage.FULL // No school-year dates fall in the requested range.
            weeks.all { it.coverage == TaskRangeCoverage.NO_BASELINE } -> TaskRangeCoverage.NO_BASELINE
            weeks.all { it.coverage == TaskRangeCoverage.FULL } -> TaskRangeCoverage.FULL
            else -> TaskRangeCoverage.PARTIAL
        }
        return LocalTasksResult(start, end,
            tasks.distinctBy { it.key to it.date }.sortedWith(compareBy<LocalTask> { it.date }
                .thenBy { it.key.rawType }.thenBy { it.key.id }), coverage, weeks)
    }
}
