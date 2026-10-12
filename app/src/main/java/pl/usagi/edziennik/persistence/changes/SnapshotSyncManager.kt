package pl.usagi.edziennik.persistence.changes

import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import pl.usagi.edziennik.domain.AttendanceEntry
import pl.usagi.edziennik.domain.DashboardInformation
import pl.usagi.edziennik.domain.SchoolTask
import pl.usagi.edziennik.domain.TimetableEntry
import pl.usagi.edziennik.domain.changes.ChangeEvent
import pl.usagi.edziennik.domain.changes.MessageSnapshot
import pl.usagi.edziennik.domain.changes.PaymentsSnapshot
import pl.usagi.edziennik.domain.changes.SnapshotArea
import pl.usagi.edziennik.domain.changes.SnapshotPayload
import pl.usagi.edziennik.domain.changes.SnapshotScope
import pl.usagi.edziennik.domain.changes.TaskKey
import pl.usagi.edziennik.domain.changes.attendanceSnapshots
import pl.usagi.edziennik.domain.changes.detectAttendance
import pl.usagi.edziennik.domain.changes.detectGrades
import pl.usagi.edziennik.domain.changes.detectInformation
import pl.usagi.edziennik.domain.changes.detectMessageSnapshots
import pl.usagi.edziennik.domain.changes.detectPaymentSnapshots
import pl.usagi.edziennik.domain.changes.detectTasks
import pl.usagi.edziennik.domain.changes.detectTimetable
import pl.usagi.edziennik.domain.changes.gradeSnapshots
import pl.usagi.edziennik.domain.changes.informationSnapshots
import pl.usagi.edziennik.domain.changes.taskSnapshots
import pl.usagi.edziennik.domain.changes.timetableSnapshots
import pl.usagi.edziennik.domain.time.isWeekday
import java.time.Clock
import javax.inject.Inject
import pl.usagi.edziennik.widget.TimetableWidgetUpdater
import pl.usagi.edziennik.widget.TasksWidgetUpdater
import pl.usagi.edziennik.widget.updateSafely

sealed interface SyncCompleteness {
    data object Complete : SyncCompleteness
    data object Failed : SyncCompleteness
    data class Partial(
        val mainListComplete: Boolean,
        val attendanceExcusesComplete: Boolean = true,
        val failedTaskDetails: Set<TaskKey> = emptySet(),
    ) : SyncCompleteness
}

sealed interface CurrentSnapshotData {
    data class Messages(val items: List<MessageSnapshot>) : CurrentSnapshotData
    data class Grades(val periodId: Int, val grades: pl.usagi.edziennik.domain.Grades) : CurrentSnapshotData
    data class Timetable(val items: List<TimetableEntry>) : CurrentSnapshotData
    data class Attendance(val items: List<AttendanceEntry>) : CurrentSnapshotData
    data class Tasks(val items: List<SchoolTask>, val calendarRangeComplete: Boolean = false) : CurrentSnapshotData
    data class Information(val items: List<DashboardInformation>) : CurrentSnapshotData
    data class Payments(val payments: PaymentsSnapshot) : CurrentSnapshotData
}

class SnapshotSyncManager @Inject constructor(
    private val store: SnapshotStore,
    private val json: Json,
    private val identity: LocalSnapshotIdentity,
    private val clock: Clock = Clock.systemUTC(),
    private val widgetUpdater: TimetableWidgetUpdater = TimetableWidgetUpdater {},
    private val tasksWidgetUpdater: TasksWidgetUpdater = TasksWidgetUpdater {},
) {
    // Foreground reads refresh the same baseline without producing notification events.
    suspend fun saveCompleteTimetable(scope: SnapshotScope, entries: List<TimetableEntry>) {
        require(scope.area == SnapshotArea.TIMETABLE)
        if (scope.accountId != identity.currentAccountId()) return
        var written = false
        store.update(scope) {
            if (scope.accountId != identity.currentAccountId()) return@update SnapshotWrite(null)
            val payload = SnapshotPayload.Timetable(
                entries.timetableSnapshots(),
                lastCompleteAtEpochMillis = clock.millis(),
            )
            written = true
            SnapshotWrite(json.encodeToString<SnapshotPayload>(payload))
        }
        if (written) widgetUpdater.updateSafely()
    }

    suspend fun knownMessageIds(scope: SnapshotScope): Set<Int>? {
        require(scope.area == SnapshotArea.MESSAGES)
        if (scope.accountId != identity.currentAccountId()) return null
        val saved = store.read(scope) ?: return null
        return (json.decodeFromString<SnapshotPayload>(saved) as SnapshotPayload.Messages)
            .items.mapTo(mutableSetOf()) { it.id }
    }

    suspend fun sync(
        scope: SnapshotScope,
        completeness: SyncCompleteness,
        current: CurrentSnapshotData? = null,
    ): List<ChangeEvent> {
        if (completeness == SyncCompleteness.Failed || scope.accountId != identity.currentAccountId()) return emptyList()
        requireNotNull(current) { "Current data is required for a successful sync" }
        require(scope.area == current.area()) { "Snapshot data does not match its scope" }
        if (completeness is SyncCompleteness.Partial) {
            if (!completeness.mainListComplete) return emptyList()
            if (scope.area == SnapshotArea.ATTENDANCE && completeness.attendanceExcusesComplete) return emptyList()
            if (scope.area == SnapshotArea.TASKS && completeness.failedTaskDetails.isEmpty()) return emptyList()
            if (scope.area !in setOf(SnapshotArea.ATTENDANCE, SnapshotArea.TASKS)) return emptyList()
        }

        var timetableWritten = false
        var tasksWritten = false
        val events = store.update(scope) { saved ->
            if (scope.accountId != identity.currentAccountId()) return@update SnapshotWrite(null)
            timetableWritten = scope.area == SnapshotArea.TIMETABLE && completeness == SyncCompleteness.Complete
            val next = current.toPayload().let { payload ->
                when {
                    completeness != SyncCompleteness.Complete -> payload
                    payload is SnapshotPayload.Timetable -> payload.copy(lastCompleteAtEpochMillis = clock.millis())
                    payload is SnapshotPayload.Tasks -> payload.copy(lastCompleteAtEpochMillis = clock.millis())
                    else -> payload
                }
            }
            val write = if (saved == null) {
                if (completeness == SyncCompleteness.Complete) SnapshotWrite(json.encodeToString<SnapshotPayload>(next))
                else SnapshotWrite(null)
            } else {
                val previous = json.decodeFromString<SnapshotPayload>(saved)
                require(previous.area() == scope.area) { "Stored snapshot does not match its scope" }
                val events = previous.detect(current)
                val persisted = when {
                    completeness is SyncCompleteness.Partial -> previous.mergePartial(next, completeness)
                    previous is SnapshotPayload.Messages && next is SnapshotPayload.Messages ->
                        SnapshotPayload.Messages((next.items + previous.items).distinctBy { it.id })
                    else -> next
                }
                SnapshotWrite(json.encodeToString<SnapshotPayload>(persisted), events)
            }
            tasksWritten = scope.area == SnapshotArea.TASKS && write.payload != null
            write
        }
        if (timetableWritten) widgetUpdater.updateSafely()
        if (tasksWritten) tasksWidgetUpdater.updateSafely()
        return events
    }

    private fun CurrentSnapshotData.area(): SnapshotArea = when (this) {
        is CurrentSnapshotData.Messages -> SnapshotArea.MESSAGES
        is CurrentSnapshotData.Grades -> SnapshotArea.GRADES
        is CurrentSnapshotData.Timetable -> SnapshotArea.TIMETABLE
        is CurrentSnapshotData.Attendance -> SnapshotArea.ATTENDANCE
        is CurrentSnapshotData.Tasks -> SnapshotArea.TASKS
        is CurrentSnapshotData.Information -> SnapshotArea.INFORMATION
        is CurrentSnapshotData.Payments -> SnapshotArea.PAYMENTS
    }

    private fun SnapshotPayload.area(): SnapshotArea = when (this) {
        is SnapshotPayload.Messages -> SnapshotArea.MESSAGES
        is SnapshotPayload.Grades -> SnapshotArea.GRADES
        is SnapshotPayload.Timetable -> SnapshotArea.TIMETABLE
        is SnapshotPayload.Attendance -> SnapshotArea.ATTENDANCE
        is SnapshotPayload.Tasks -> SnapshotArea.TASKS
        is SnapshotPayload.Information -> SnapshotArea.INFORMATION
        is SnapshotPayload.Payments -> SnapshotArea.PAYMENTS
    }

    private fun CurrentSnapshotData.toPayload(): SnapshotPayload = when (this) {
        is CurrentSnapshotData.Messages -> SnapshotPayload.Messages(items)
        is CurrentSnapshotData.Grades -> SnapshotPayload.Grades(grades.gradeSnapshots(periodId))
        is CurrentSnapshotData.Timetable -> SnapshotPayload.Timetable(items.timetableSnapshots())
        is CurrentSnapshotData.Attendance -> SnapshotPayload.Attendance(items.attendanceSnapshots())
        is CurrentSnapshotData.Tasks -> SnapshotPayload.Tasks(items.taskSnapshots(), calendarRangeComplete = calendarRangeComplete)
        is CurrentSnapshotData.Information -> SnapshotPayload.Information(items.informationSnapshots())
        is CurrentSnapshotData.Payments -> SnapshotPayload.Payments(payments)
    }

    private fun SnapshotPayload.detect(current: CurrentSnapshotData): List<ChangeEvent> = when {
        this is SnapshotPayload.Messages && current is CurrentSnapshotData.Messages -> detectMessageSnapshots(items, current.items)
        this is SnapshotPayload.Grades && current is CurrentSnapshotData.Grades ->
            detectGrades(items, current.grades, current.periodId)
        this is SnapshotPayload.Timetable && current is CurrentSnapshotData.Timetable ->
            detectTimetable(items, current.items)
        this is SnapshotPayload.Attendance && current is CurrentSnapshotData.Attendance ->
            detectAttendance(items, current.items)
        this is SnapshotPayload.Tasks && current is CurrentSnapshotData.Tasks -> {
            val previousKeys = items.mapTo(mutableSetOf()) { it.key }
            // Newly covered weekend dates establish baseline; they were not compared before.
            val comparable = if (!calendarRangeComplete && current.calendarRangeComplete) {
                current.items.filter { it.date.isWeekday() || TaskKey(it.rawType, it.id) in previousKeys }
            } else current.items
            detectTasks(items, comparable)
        }
        this is SnapshotPayload.Information && current is CurrentSnapshotData.Information ->
            detectInformation(items, current.items)
        this is SnapshotPayload.Payments && current is CurrentSnapshotData.Payments ->
            detectPaymentSnapshots(value, current.payments)
        else -> error("Snapshot data does not match its stored payload")
    }

    private fun SnapshotPayload.mergePartial(next: SnapshotPayload, partial: SyncCompleteness.Partial): SnapshotPayload = when {
        this is SnapshotPayload.Attendance && next is SnapshotPayload.Attendance -> {
            val currentKeys = next.items.mapTo(mutableSetOf()) { it.key }
            SnapshotPayload.Attendance(items.filter { it.key !in currentKeys } + next.items)
        }
        this is SnapshotPayload.Tasks && next is SnapshotPayload.Tasks -> {
            val previousByKey = items.associateBy { it.key }
            val currentKeys = next.items.mapTo(mutableSetOf()) { it.key }
            SnapshotPayload.Tasks(
                items = items.filter { it.key !in currentKeys } + next.items.map { task ->
                    if (task.key in partial.failedTaskDetails) task.copy(details = previousByKey[task.key]?.details)
                    else task
                },
                lastCompleteAtEpochMillis = lastCompleteAtEpochMillis,
                calendarRangeComplete = calendarRangeComplete || next.calendarRangeComplete,
                failedDetailKeys = (failedDetailKeys - currentKeys) + partial.failedTaskDetails,
            )
        }
        else -> error("Partial merge is only supported for attendance and tasks")
    }
}
