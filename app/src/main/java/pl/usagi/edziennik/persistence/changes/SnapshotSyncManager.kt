package pl.usagi.edziennik.persistence.changes

import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import pl.usagi.edziennik.domain.AttendanceEntry
import pl.usagi.edziennik.domain.DashboardInformation
import pl.usagi.edziennik.domain.ReceivedMessage
import pl.usagi.edziennik.domain.SchoolTask
import pl.usagi.edziennik.domain.TimetableEntry
import pl.usagi.edziennik.domain.changes.ChangeEvent
import pl.usagi.edziennik.domain.changes.SnapshotArea
import pl.usagi.edziennik.domain.changes.SnapshotPayload
import pl.usagi.edziennik.domain.changes.SnapshotScope
import pl.usagi.edziennik.domain.changes.TaskKey
import pl.usagi.edziennik.domain.changes.attendanceSnapshots
import pl.usagi.edziennik.domain.changes.detectAttendance
import pl.usagi.edziennik.domain.changes.detectGrades
import pl.usagi.edziennik.domain.changes.detectInformation
import pl.usagi.edziennik.domain.changes.detectMessages
import pl.usagi.edziennik.domain.changes.detectPayments
import pl.usagi.edziennik.domain.changes.detectTasks
import pl.usagi.edziennik.domain.changes.detectTimetable
import pl.usagi.edziennik.domain.changes.gradeSnapshots
import pl.usagi.edziennik.domain.changes.informationSnapshots
import pl.usagi.edziennik.domain.changes.messageSnapshots
import pl.usagi.edziennik.domain.changes.taskSnapshots
import pl.usagi.edziennik.domain.changes.timetableSnapshots
import pl.usagi.edziennik.domain.changes.toSnapshot
import javax.inject.Inject

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
    data class Messages(val items: List<ReceivedMessage>) : CurrentSnapshotData
    data class Grades(val periodId: Int, val grades: pl.usagi.edziennik.domain.Grades) : CurrentSnapshotData
    data class Timetable(val items: List<TimetableEntry>) : CurrentSnapshotData
    data class Attendance(val items: List<AttendanceEntry>) : CurrentSnapshotData
    data class Tasks(val items: List<SchoolTask>) : CurrentSnapshotData
    data class Information(val items: List<DashboardInformation>) : CurrentSnapshotData
    data class Payments(val payments: pl.usagi.edziennik.domain.Payments) : CurrentSnapshotData
}

class SnapshotSyncManager @Inject constructor(
    private val store: SnapshotStore,
    private val json: Json,
    private val identity: LocalSnapshotIdentity,
) {
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

        return store.update(scope) { saved ->
            if (scope.accountId != identity.currentAccountId()) return@update SnapshotWrite(null)
            val next = current.toPayload()
            if (saved == null) {
                if (completeness == SyncCompleteness.Complete) SnapshotWrite(json.encodeToString<SnapshotPayload>(next))
                else SnapshotWrite(null)
            } else {
                val previous = json.decodeFromString<SnapshotPayload>(saved)
                require(previous.area() == scope.area) { "Stored snapshot does not match its scope" }
                val events = previous.detect(current)
                val persisted = if (completeness is SyncCompleteness.Partial) previous.mergePartial(next, completeness) else next
                SnapshotWrite(json.encodeToString<SnapshotPayload>(persisted), events)
            }
        }
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
        is CurrentSnapshotData.Messages -> SnapshotPayload.Messages(items.messageSnapshots())
        is CurrentSnapshotData.Grades -> SnapshotPayload.Grades(grades.gradeSnapshots(periodId))
        is CurrentSnapshotData.Timetable -> SnapshotPayload.Timetable(items.timetableSnapshots())
        is CurrentSnapshotData.Attendance -> SnapshotPayload.Attendance(items.attendanceSnapshots())
        is CurrentSnapshotData.Tasks -> SnapshotPayload.Tasks(items.taskSnapshots())
        is CurrentSnapshotData.Information -> SnapshotPayload.Information(items.informationSnapshots())
        is CurrentSnapshotData.Payments -> SnapshotPayload.Payments(payments.toSnapshot())
    }

    private fun SnapshotPayload.detect(current: CurrentSnapshotData): List<ChangeEvent> = when {
        this is SnapshotPayload.Messages && current is CurrentSnapshotData.Messages -> detectMessages(items, current.items)
        this is SnapshotPayload.Grades && current is CurrentSnapshotData.Grades ->
            detectGrades(items, current.grades, current.periodId)
        this is SnapshotPayload.Timetable && current is CurrentSnapshotData.Timetable ->
            detectTimetable(items, current.items)
        this is SnapshotPayload.Attendance && current is CurrentSnapshotData.Attendance ->
            detectAttendance(items, current.items)
        this is SnapshotPayload.Tasks && current is CurrentSnapshotData.Tasks -> detectTasks(items, current.items)
        this is SnapshotPayload.Information && current is CurrentSnapshotData.Information ->
            detectInformation(items, current.items)
        this is SnapshotPayload.Payments && current is CurrentSnapshotData.Payments ->
            detectPayments(value, current.payments)
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
            SnapshotPayload.Tasks(items.filter { it.key !in currentKeys } + next.items.map { task ->
                if (task.key in partial.failedTaskDetails) task.copy(details = previousByKey[task.key]?.details)
                else task
            })
        }
        else -> error("Partial merge is only supported for attendance and tasks")
    }
}
