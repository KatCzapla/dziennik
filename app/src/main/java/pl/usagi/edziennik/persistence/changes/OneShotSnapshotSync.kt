package pl.usagi.edziennik.persistence.changes

import kotlinx.coroutines.CancellationException
import pl.usagi.edziennik.domain.ActiveStudentManager
import pl.usagi.edziennik.domain.Student
import pl.usagi.edziennik.domain.changes.ChangeEvent
import pl.usagi.edziennik.domain.changes.LocalAccountId
import pl.usagi.edziennik.domain.changes.LocalStudentScopeId
import pl.usagi.edziennik.domain.changes.SnapshotArea
import pl.usagi.edziennik.domain.changes.SnapshotScope
import pl.usagi.edziennik.domain.changes.TaskKey
import pl.usagi.edziennik.domain.changes.messageSnapshots
import pl.usagi.edziennik.domain.changes.toSnapshot
import pl.usagi.edziennik.domain.time.WeekRange
import pl.usagi.edziennik.domain.time.clampedSchoolWeekEnd
import pl.usagi.edziennik.domain.time.clampedSchoolWeekStart
import pl.usagi.edziennik.domain.time.currentSchoolYearRange
import pl.usagi.edziennik.domain.time.intersectsSchoolYear
import pl.usagi.edziennik.repository.AttendanceRepository
import pl.usagi.edziennik.repository.DashboardRepository
import pl.usagi.edziennik.repository.GradesRepository
import pl.usagi.edziennik.repository.MessagesRepository
import pl.usagi.edziennik.repository.PaymentsRepository
import pl.usagi.edziennik.repository.SchoolTasksRepository
import pl.usagi.edziennik.repository.TimetableRepository
import pl.usagi.edziennik.ui.grades.chooseClassificationPeriod
import java.time.Clock
import java.time.LocalDate
import java.time.OffsetDateTime
import javax.inject.Inject

enum class SyncIssue { PARTIAL, FAILED }

data class OneShotSyncResult(
    val events: List<ChangeEvent>,
    val issues: Map<SnapshotArea, SyncIssue>,
)

interface SnapshotSynchronizer {
    suspend fun syncOnce(): OneShotSyncResult
}

class OneShotSnapshotSync @Inject constructor(
    private val identity: LocalSnapshotIdentity,
    private val activeStudentManager: ActiveStudentManager,
    private val snapshotSyncManager: SnapshotSyncManager,
    private val messagesRepository: MessagesRepository,
    private val gradesRepository: GradesRepository,
    private val timetableRepository: TimetableRepository,
    private val attendanceRepository: AttendanceRepository,
    private val schoolTasksRepository: SchoolTasksRepository,
    private val dashboardRepository: DashboardRepository,
    private val paymentsRepository: PaymentsRepository,
    private val clock: Clock,
) : SnapshotSynchronizer {
    override suspend fun syncOnce(): OneShotSyncResult {
        val accountId = identity.currentAccountId()
            ?: return OneShotSyncResult(emptyList(), SnapshotArea.entries.associateWith { SyncIssue.FAILED })
        val student = activeStudentManager.activeStudent.value
        val events = mutableListOf<ChangeEvent>()
        val issues = mutableMapOf<SnapshotArea, SyncIssue>()

        suspend fun area(area: SnapshotArea, block: suspend () -> AreaResult) {
            try {
                val result = block()
                events += result.events
                result.issue?.let { if (issues[area] != SyncIssue.FAILED) issues[area] = it }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                issues[area] = SyncIssue.FAILED
            }
        }

        area(SnapshotArea.MESSAGES) { syncMessages(accountId) }

        if (student == null) {
            SnapshotArea.entries.filter { it != SnapshotArea.MESSAGES }.forEach { issues[it] = SyncIssue.FAILED }
            return OneShotSyncResult(events, issues)
        }

        val studentId = identity.studentScopeId(student.diaryId)
        val today = LocalDate.now(clock)
        val schoolYear = currentSchoolYearRange(today)
        val week = WeekRange.fromDate(today)

        area(SnapshotArea.GRADES) {
            val periods = gradesRepository.getClassificationPeriods(student)
            val period = chooseClassificationPeriod(periods, OffsetDateTime.now(clock))
                ?: return@area AreaResult(emptyList())
            val grades = gradesRepository.getGrades(student, period)
            requireCurrent(accountId, student)
            AreaResult(snapshotSyncManager.sync(
                SnapshotScope(accountId, studentId, SnapshotArea.GRADES, period.id.toString()),
                SyncCompleteness.Complete,
                CurrentSnapshotData.Grades(period.id, grades),
            ))
        }

        if (week.intersectsSchoolYear(schoolYear)) {
            val from = week.clampedSchoolWeekStart(schoolYear)
            val to = week.clampedSchoolWeekEnd(schoolYear)
            val range = "$from/$to"

            area(SnapshotArea.TIMETABLE) {
                val entries = timetableRepository.getTimetable(from, to)
                requireCurrent(accountId, student)
                AreaResult(snapshotSyncManager.sync(
                    SnapshotScope(accountId, studentId, SnapshotArea.TIMETABLE, range),
                    SyncCompleteness.Complete,
                    CurrentSnapshotData.Timetable(entries),
                ))
            }

            area(SnapshotArea.ATTENDANCE) {
                val fetched = attendanceRepository.getAttendanceWithCompleteness(from, to)
                requireCurrent(accountId, student)
                val complete = fetched.entriesComplete && fetched.excusesComplete
                val completeness = if (complete) SyncCompleteness.Complete
                else SyncCompleteness.Partial(
                    mainListComplete = fetched.entriesComplete,
                    attendanceExcusesComplete = fetched.excusesComplete,
                )
                AreaResult(snapshotSyncManager.sync(
                    SnapshotScope(accountId, studentId, SnapshotArea.ATTENDANCE, range),
                    completeness,
                    CurrentSnapshotData.Attendance(fetched.entries),
                ), if (complete) null else SyncIssue.PARTIAL)
            }

        }

        for (taskWeek in taskSnapshotWeeks(today)) {
            area(SnapshotArea.TASKS) {
                val fetched = schoolTasksRepository.getSchoolTasksWithCompleteness(taskWeek.from, taskWeek.to)
                requireCurrent(accountId, student)
                val tasks = fetched.tasks.filter { it.date in taskWeek.from..taskWeek.to }
                val keys = tasks.mapTo(mutableSetOf()) { TaskKey(it.rawType, it.id) }
                val failedDetails = fetched.failedDetailKeys.intersect(keys)
                val complete = failedDetails.isEmpty()
                val completeness = if (complete) SyncCompleteness.Complete
                else SyncCompleteness.Partial(mainListComplete = true, failedTaskDetails = failedDetails)
                AreaResult(snapshotSyncManager.sync(
                    SnapshotScope(accountId, studentId, SnapshotArea.TASKS, taskWeek.scopeRange),
                    completeness,
                    CurrentSnapshotData.Tasks(tasks, calendarRangeComplete = true),
                ), if (complete) null else SyncIssue.PARTIAL)
            }
        }

        area(SnapshotArea.INFORMATION) {
            val information = dashboardRepository.getInformation(student)
            requireCurrent(accountId, student)
            AreaResult(snapshotSyncManager.sync(
                SnapshotScope(accountId, studentId, SnapshotArea.INFORMATION),
                SyncCompleteness.Complete,
                CurrentSnapshotData.Information(information),
            ))
        }

        area(SnapshotArea.PAYMENTS) {
            val payments = paymentsRepository.getPayments(student)
            requireCurrent(accountId, student)
            AreaResult(snapshotSyncManager.sync(
                SnapshotScope(accountId, studentId, SnapshotArea.PAYMENTS),
                SyncCompleteness.Complete,
                CurrentSnapshotData.Payments(payments.toSnapshot()),
            ))
        }

        return OneShotSyncResult(events, issues)
    }

    private suspend fun syncMessages(accountId: LocalAccountId): AreaResult {
        val scope = SnapshotScope(accountId, null, SnapshotArea.MESSAGES)
        val knownIds = snapshotSyncManager.knownMessageIds(scope)
        val messages = mutableListOf<pl.usagi.edziennik.domain.ReceivedMessage>()
        var lastMessageId = 0

        repeat(MAX_MESSAGE_PAGES) { pageIndex ->
            val page = try {
                messagesRepository.getReceivedMessagesPage(lastMessageId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (pageIndex == 0) throw failure
                return AreaResult(emptyList(), SyncIssue.PARTIAL)
            }
            if (page.messages.isEmpty() && !page.endReached) return AreaResult(emptyList(), SyncIssue.PARTIAL)
            messages += page.messages
            val reachedKnownMessage = knownIds != null && page.messages.any { it.id in knownIds }
            if (knownIds == null || reachedKnownMessage || page.endReached) {
                require(identity.currentAccountId() == accountId)
                // With a bounded inbox baseline, unseen lower IDs may be old messages revealed later.
                val newestKnownId = knownIds?.maxOrNull()
                val safeMessages = messages.messageSnapshots().filter { snapshot ->
                    newestKnownId == null || snapshot.id in knownIds || snapshot.id > newestKnownId
                }
                return AreaResult(snapshotSyncManager.sync(
                    scope,
                    SyncCompleteness.Complete,
                    CurrentSnapshotData.Messages(safeMessages),
                ))
            }
            val nextId = page.messages.lastOrNull()?.id
            if (nextId == null || nextId == lastMessageId || messages.dropLast(page.messages.size).any { it.id == nextId }) {
                return AreaResult(emptyList(), SyncIssue.PARTIAL)
            }
            lastMessageId = nextId
        }
        return AreaResult(emptyList(), SyncIssue.PARTIAL)
    }

    private fun requireCurrent(accountId: LocalAccountId, student: Student) {
        require(identity.currentAccountId() == accountId && activeStudentManager.activeStudent.value == student) {
            "Synchronization scope changed during fetching"
        }
    }

    private data class AreaResult(val events: List<ChangeEvent>, val issue: SyncIssue? = null)

    private companion object {
        const val MAX_MESSAGE_PAGES = 5
    }
}
