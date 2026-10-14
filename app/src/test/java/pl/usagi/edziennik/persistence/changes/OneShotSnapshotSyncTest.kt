package pl.usagi.edziennik.persistence.changes

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.usagi.edziennik.domain.ActiveStudentManager
import pl.usagi.edziennik.domain.AttendanceEntry
import pl.usagi.edziennik.domain.AttendanceStatus
import pl.usagi.edziennik.domain.BankAccountPayment
import pl.usagi.edziennik.domain.ClassificationPeriod
import pl.usagi.edziennik.domain.DashboardInformation
import pl.usagi.edziennik.domain.Grades
import pl.usagi.edziennik.domain.GradesSettings
import pl.usagi.edziennik.domain.Mailbox
import pl.usagi.edziennik.domain.MessageDetails
import pl.usagi.edziennik.domain.MessageMailboxType
import pl.usagi.edziennik.domain.Payments
import pl.usagi.edziennik.domain.ReceivedMessage
import pl.usagi.edziennik.domain.SchoolTask
import pl.usagi.edziennik.domain.SchoolTaskDetails
import pl.usagi.edziennik.domain.SchoolTaskType
import pl.usagi.edziennik.domain.Student
import pl.usagi.edziennik.domain.TimetableEntry
import pl.usagi.edziennik.domain.changes.ChangeEvent
import pl.usagi.edziennik.domain.changes.LocalAccountId
import pl.usagi.edziennik.domain.changes.SnapshotArea
import pl.usagi.edziennik.domain.changes.SnapshotScope
import pl.usagi.edziennik.domain.changes.TaskKey
import pl.usagi.edziennik.repository.AttendanceFetchResult
import pl.usagi.edziennik.repository.AttendanceRepository
import pl.usagi.edziennik.repository.DashboardRepository
import pl.usagi.edziennik.repository.GradesRepository
import pl.usagi.edziennik.repository.MessagesRepository
import pl.usagi.edziennik.repository.PaymentsRepository
import pl.usagi.edziennik.repository.ReceivedMessagesPage
import pl.usagi.edziennik.repository.SchoolTasksFetchResult
import pl.usagi.edziennik.repository.SchoolTasksRepository
import pl.usagi.edziennik.repository.TimetableRepository
import pl.usagi.edziennik.widget.TimetableWidgetUpdater
import java.io.File
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId

class OneShotSnapshotSyncTest {
    private val today = LocalDate.of(2026, 9, 30)
    private val student = Student("secret-student-key", 17, "Uczeń", "4A", "Szkoła")
    private val activeStudents = ActiveStudentManager().apply { initialize(listOf(student)) }
    private val identity = LocalSnapshotIdentity(MemoryIdentityStore()).apply { ensureAccountId() }
    private val store = MemorySnapshotStore()
    private val messages = FakeMessages()
    private val grades = FakeGrades()
    private val timetable = FakeTimetable()
    private val attendance = FakeAttendance()
    private val tasks = FakeTasks()
    private val information = FakeInformation()
    private val payments = FakePayments()
    private val clock = Clock.fixed(Instant.parse("2026-09-30T12:00:00Z"), ZoneId.of("Europe/Warsaw"))
    private var widgetUpdates = 0
    private val sync = OneShotSnapshotSync(
        identity, activeStudents, SnapshotSyncManager(store, Json, identity, clock,
            TimetableWidgetUpdater { widgetUpdates++ }), messages, grades,
        timetable, attendance, tasks, information, payments, clock,
    )

    @Test fun networkFailureCauseIsPropagatedWithoutBlockingSuccessfulAreas() = runTest {
        timetable.failure = java.io.IOException("test network failure")
        val result = sync.syncOnce()
        assertEquals(SyncFailureCause.NETWORK, result.failureCauses[SnapshotArea.TIMETABLE])
        assertEquals(SyncIssue.FAILED, result.issues[SnapshotArea.TIMETABLE])
        assertTrue(SnapshotArea.MESSAGES in result.successfulAreas)
    }

    @Test fun serverFailureCauseIsPropagated() = runTest {
        timetable.failure = pl.usagi.edziennik.network.timetable.TimetableFetchException.Http(503)
        val result = sync.syncOnce()
        assertEquals(SyncFailureCause.SERVER, result.failureCauses[SnapshotArea.TIMETABLE])
    }

    @Test fun partialExcusesNetworkFailureIsAvailableForRetryDecision() = runTest {
        attendance.excusesComplete = false
        attendance.excusesFailure = java.io.IOException()
        val result = sync.syncOnce()
        assertEquals(SyncIssue.PARTIAL, result.issues[SnapshotArea.ATTENDANCE])
        assertEquals(SyncFailureCause.NETWORK, result.failureCauses[SnapshotArea.ATTENDANCE])
    }

    @Test fun partialTaskDetailsServerFailureIsAvailableForRetryDecision() = runTest {
        tasks.items = listOf(task("Opis"))
        val key = TaskKey(tasks.items.single().rawType, tasks.items.single().id)
        tasks.failedDetails = setOf(key)
        tasks.detailFailures = mapOf(key to pl.usagi.edziennik.network.schooltasks.SchoolTasksFetchException.Http(503))
        val result = sync.syncOnce()
        assertEquals(SyncIssue.PARTIAL, result.issues[SnapshotArea.TASKS])
        assertEquals(SyncFailureCause.SERVER, result.failureCauses[SnapshotArea.TASKS])
    }

    @Test fun taskSyncRequestsThreeWeeksIncludingSundays() = runTest {
        sync.syncOnce()
        assertEquals(listOf(LocalDate.of(2026, 9, 28) to LocalDate.of(2026, 10, 4),
            LocalDate.of(2026, 10, 5) to LocalDate.of(2026, 10, 11),
            LocalDate.of(2026, 10, 12) to LocalDate.of(2026, 10, 18)), tasks.requests)
    }

    @Test fun taskSyncOnMondayRequestsTwoWeeks() = runTest {
        synchronizer(LocalDate.of(2026, 10, 12)).syncOnce()
        assertEquals(listOf(LocalDate.of(2026, 10, 12) to LocalDate.of(2026, 10, 18),
            LocalDate.of(2026, 10, 19) to LocalDate.of(2026, 10, 25)), tasks.requests)
    }

    @Test fun weekendTasksReachLocalReaderThroughExistingRepositoryFlow() = runTest {
        identity.selectStudent(student.diaryId)
        tasks.items = listOf(task("Sobota").copy(date = today.plusDays(3)),
            task("Niedziela").copy(date = today.plusDays(4), id = 4))
        val first = sync.syncOnce()
        assertTrue(first.events.isEmpty())
        val result = LocalTasksReader(store, identity, Json, clock).read(today)
        assertEquals(TaskRangeCoverage.FULL, result.coverage)
        assertEquals(listOf("Sobota", "Niedziela"), result.tasks.map { it.description })
        assertTrue(result.weeks.all { it.lastCompleteAt == clock.instant() })
    }

    @Test fun failedWeekKeepsOldDataAndDoesNotStopOtherWeeksOrLoseFailedIssue() = runTest {
        identity.selectStudent(student.diaryId)
        tasks.items = listOf(task("Zachowany opis"))
        sync.syncOnce()
        val firstWeek = taskSnapshotWeeks(today).first()
        val firstScope = SnapshotScope(identity.currentAccountId()!!, identity.selectedStudentScopeId(),
            SnapshotArea.TASKS, firstWeek.scopeRange)
        val oldPayload = store.payloads.getValue(firstScope.storageKey)
        tasks.failedWeeks += firstWeek.from
        val future = task(null).copy(date = today.plusDays(7), id = 4)
        tasks.items += future
        tasks.failedDetails = setOf(TaskKey(4, 4))
        val result = sync.syncOnce()
        assertEquals(SyncIssue.FAILED, result.issues[SnapshotArea.TASKS])
        assertEquals(6, tasks.requests.size)
        assertEquals(oldPayload, store.payloads.getValue(firstScope.storageKey))
        assertEquals(1, result.events.filterIsInstance<ChangeEvent.TaskChanged>().size)
        assertEquals(setOf(3, 4), LocalTasksReader(store, identity, Json, clock).read(today).tasks.map { it.key.id }.toSet())
    }

    @Test fun partialFirstWeekDoesNotCreateBaselineButOtherWeeksComplete() = runTest {
        identity.selectStudent(student.diaryId)
        tasks.items = listOf(task(null))
        tasks.failedDetails = setOf(TaskKey(4, 3))
        val result = sync.syncOnce()
        assertEquals(SyncIssue.PARTIAL, result.issues[SnapshotArea.TASKS])
        assertTrue(result.events.isEmpty())
        val local = LocalTasksReader(store, identity, Json, clock).read(today)
        assertEquals(TaskRangeCoverage.PARTIAL, local.coverage)
        assertEquals(TaskRangeCoverage.NO_BASELINE, local.weeks.first().coverage)
        assertEquals(2, local.weeks.count { it.coverage == TaskRangeCoverage.FULL })
    }

    @Test fun newFutureWeekBaselineIsQuietThenChangesAreDetectedOnce() = runTest {
        tasks.items = listOf(task("Istniejace zadanie").copy(date = today.plusDays(7)))
        assertTrue(sync.syncOnce().events.isEmpty())
        tasks.items += task("Nowe zadanie").copy(date = today.plusDays(8), id = 4)
        val changed = sync.syncOnce()
        assertEquals(listOf(TaskKey(4, 4)), changed.events.filterIsInstance<ChangeEvent.TaskChanged>().map { it.key })
        assertTrue(sync.syncOnce().events.isEmpty())
    }

    @Test fun tasksAtYearEndUseOnlyClippedCalendarRange() = runTest {
        synchronizer(LocalDate.of(2024, 6, 29)).syncOnce()
        assertEquals(listOf(LocalDate.of(2024, 6, 24) to LocalDate.of(2024, 6, 30)), tasks.requests)
    }

    private fun synchronizer(date: LocalDate): OneShotSnapshotSync {
        val atDate = Clock.fixed(date.atStartOfDay(clock.zone).toInstant(), clock.zone)
        return OneShotSnapshotSync(identity, activeStudents, SnapshotSyncManager(store, Json, identity, atDate),
            messages, grades, timetable, attendance, tasks, information, payments, atDate)
    }

    @Test fun completePlanSyncSavesReadableSnapshotAndRedrawsWidget() = runTest {
        identity.selectStudent(student.diaryId)
        timetable.items = listOf(TimetableEntry(today, null, LocalTime.of(9, 0), LocalTime.of(9, 45),
            "Matematyka", null, room = "215", changes = emptyList()))
        sync.syncOnce()
        val result = LocalTimetableReader(store, identity, Json, clock).read(today) as LocalTimetableResult.Available
        assertEquals("Matematyka", result.lessons.single().subject)
        assertEquals(clock.instant(), result.lastCompleteAt)
        assertEquals(1, widgetUpdates)
    }

    @Test fun failedPlanFetchPreservesPlanTimestampAndDoesNotRequestRedraw() = runTest {
        identity.selectStudent(student.diaryId)
        timetable.items = listOf(TimetableEntry(today, null, LocalTime.of(9, 0), LocalTime.of(9, 45),
            "Matematyka", null, room = "215", changes = emptyList()))
        sync.syncOnce()
        val reader = LocalTimetableReader(store, identity, Json, clock)
        val before = reader.read(today)
        timetable.fail = true
        val result = sync.syncOnce()
        assertEquals(SyncIssue.FAILED, result.issues[SnapshotArea.TIMETABLE])
        assertEquals(before, reader.read(today))
        assertEquals(1, widgetUpdates)
    }

    @Test fun firstCompleteSyncBuildsBaselinesWithoutEvents() = runTest {
        val result = sync.syncOnce()

        assertTrue(result.events.isEmpty())
        assertTrue(result.issues.isEmpty())
        assertEquals(9, store.payloads.size)
    }

    @Test fun secondSyncEmitsChangeAndThirdDoesNotRepeatIt() = runTest {
        sync.syncOnce()
        information.items = listOf(DashboardInformation("Informacja", "Treść", today))

        val second = sync.syncOnce()
        val third = sync.syncOnce()

        assertEquals(listOf(ChangeEvent.NewInformation(today, "Informacja")), second.events)
        assertTrue(third.events.isEmpty())
    }

    @Test fun failureInOneAreaDoesNotStopOthers() = runTest {
        sync.syncOnce()
        timetable.fail = true
        information.items = listOf(DashboardInformation("Nowa", "Treść", today))

        val result = sync.syncOnce()

        assertEquals(SyncIssue.FAILED, result.issues[SnapshotArea.TIMETABLE])
        assertEquals(listOf(ChangeEvent.NewInformation(today, "Nowa")), result.events)
        assertEquals(1, result.issues.size)
    }

    @Test fun failedExcusesArePartialButBaseAttendanceCanAdvance() = runTest {
        attendance.items = listOf(attendance(AttendanceStatus.Absent))
        sync.syncOnce()
        attendance.items = listOf(attendance(AttendanceStatus.Late))
        attendance.excusesComplete = false

        val partial = sync.syncOnce()
        attendance.excusesComplete = true
        val laterComplete = sync.syncOnce()

        assertEquals(SyncIssue.PARTIAL, partial.issues[SnapshotArea.ATTENDANCE])
        assertEquals(1, partial.events.filterIsInstance<ChangeEvent.AttendanceChanged>().size)
        assertTrue(laterComplete.events.isEmpty())
    }

    @Test fun failedOneTaskDetailIsPartialAndPreservesPreviousDetail() = runTest {
        tasks.items = listOf(task("Opis"))
        sync.syncOnce()
        tasks.items = listOf(task(null))
        tasks.failedDetails = setOf(TaskKey(4, 3))

        val partial = sync.syncOnce()
        tasks.items = listOf(task("Opis"))
        tasks.failedDetails = emptySet()
        val laterComplete = sync.syncOnce()

        assertEquals(SyncIssue.PARTIAL, partial.issues[SnapshotArea.TASKS])
        assertTrue(partial.events.isEmpty())
        assertTrue(laterComplete.events.isEmpty())
    }

    @Test fun emptySuccessfulResponsesAreCompleteNotFailures() = runTest {
        val first = sync.syncOnce()
        val second = sync.syncOnce()

        assertTrue(first.issues.isEmpty())
        assertTrue(second.issues.isEmpty())
        assertTrue(second.events.isEmpty())
        assertEquals(9, store.payloads.size)
    }

    @Test fun incompleteMessagePaginationDoesNotAdvanceBaseline() = runTest {
        messages.pages[0] = ReceivedMessagesPage(listOf(message(100)), endReached = false)
        sync.syncOnce()
        messages.pages[0] = ReceivedMessagesPage(listOf(message(102), message(101)), endReached = false)
        messages.failingCursor = 101

        val partial = sync.syncOnce()
        messages.failingCursor = null
        messages.pages[101] = ReceivedMessagesPage(listOf(message(100)), endReached = true)
        val complete = sync.syncOnce()

        assertEquals(SyncIssue.PARTIAL, partial.issues[SnapshotArea.MESSAGES])
        assertTrue(partial.events.isEmpty())
        assertEquals(setOf(101, 102), complete.events.filterIsInstance<ChangeEvent.NewMessage>().map { it.id }.toSet())
    }

    @Test fun previouslySeenMessagesAndLaterOldPagesAreNeverNew() = runTest {
        messages.pages[0] = ReceivedMessagesPage(listOf(message(100), message(99)), endReached = false)
        sync.syncOnce()
        messages.pages[0] = ReceivedMessagesPage(listOf(message(101), message(100)), endReached = false)
        messages.pages[100] = ReceivedMessagesPage(listOf(message(99), message(98)), endReached = true)

        val second = sync.syncOnce()
        val third = sync.syncOnce()

        assertEquals(listOf(101), second.events.filterIsInstance<ChangeEvent.NewMessage>().map { it.id })
        assertTrue(third.events.isEmpty())
        assertTrue(messages.requestedCursors.none { it == 100 })
    }

    @Test fun previouslyUnseenOlderMessageDoesNotBecomeNew() = runTest {
        messages.pages[0] = ReceivedMessagesPage(listOf(message(100), message(99)), endReached = false)
        sync.syncOnce()
        messages.pages[0] = ReceivedMessagesPage(listOf(message(98), message(100)), endReached = false)

        val result = sync.syncOnce()

        assertTrue(result.events.filterIsInstance<ChangeEvent.NewMessage>().isEmpty())
    }

    @Test fun incompleteAttendanceListCannotReplaceCompleteBaseline() = runTest {
        attendance.items = listOf(attendance(AttendanceStatus.Absent))
        sync.syncOnce()
        attendance.items = emptyList()
        attendance.entriesComplete = false

        val partial = sync.syncOnce()
        attendance.items = listOf(attendance(AttendanceStatus.Absent))
        attendance.entriesComplete = true
        val complete = sync.syncOnce()

        assertEquals(SyncIssue.PARTIAL, partial.issues[SnapshotArea.ATTENDANCE])
        assertTrue(partial.events.isEmpty())
        assertTrue(complete.events.isEmpty())
    }

    @Test fun emptyMessagePageWithoutEndMarkerIsPartial() = runTest {
        messages.pages[0] = ReceivedMessagesPage(emptyList(), endReached = false)

        val result = sync.syncOnce()

        assertEquals(SyncIssue.PARTIAL, result.issues[SnapshotArea.MESSAGES])
        assertEquals(8, store.payloads.size)
    }

    @Test fun persistedSnapshotsContainNoSessionKeyStudentKeyOrBankAccountNumber() = runTest {
        messages.pages[0] = ReceivedMessagesPage(listOf(message(100)), endReached = true)
        payments.value = Payments(bankAccounts = listOf(BankAccountPayment(
            "secret-bank-account", null, "Nadpłata", BigDecimal("6.00"), null, null, null, null,
        )))

        sync.syncOnce()

        val saved = store.payloads.values.joinToString()
        assertTrue(!saved.contains("never-persist-api-global-key"))
        assertTrue(!saved.contains("secret-student-key"))
        assertTrue(!saved.contains("secret-bank-account"))
    }

    private fun message(id: Int) = ReceivedMessage(
        id, "never-persist-api-global-key", "Nadawca", "Temat $id", today.atStartOfDay(), true, false,
    )

    private fun attendance(status: AttendanceStatus) = AttendanceEntry(
        7, 8, 2, status, today, LocalTime.of(9, 0), LocalTime.of(9, 45), "Matematyka", null,
    )

    private fun task(description: String?) = SchoolTask(
        SchoolTaskType.Homework, 4, today, "Matematyka", false, 3,
        description?.let { SchoolTaskDetails(it, null, null) },
    )

    private class MemoryIdentityStore : LocalIdentityStore {
        private val values = mutableMapOf<String, String>()
        override fun get(key: String): String? = values[key]
        override fun put(key: String, value: String) { values[key] = value }
        override fun clear() { values.clear() }
    }

    private class MemorySnapshotStore : SnapshotStore {
        val payloads = mutableMapOf<String, String>()
        override suspend fun read(scope: SnapshotScope): String? = payloads[scope.storageKey]
        override suspend fun update(scope: SnapshotScope, transform: (String?) -> SnapshotWrite): List<ChangeEvent> {
            val write = transform(payloads[scope.storageKey])
            write.payload?.let { payloads[scope.storageKey] = it }
            return write.events
        }
        override suspend fun deleteAccount(accountId: LocalAccountId) {
            payloads.keys.removeAll { it.startsWith("${accountId.value}|") }
        }
    }

    private class FakeMessages : MessagesRepository {
        val pages = mutableMapOf<Int, ReceivedMessagesPage>(0 to ReceivedMessagesPage(emptyList(), true))
        val requestedCursors = mutableListOf<Int>()
        var failingCursor: Int? = null
        override val unreadCountInvalidations: Flow<Unit> = emptyFlow()
        override suspend fun getMailboxes(): List<Mailbox> = emptyList()
        override suspend fun getUnreadCount(): Int = 0
        override suspend fun getMessagesPage(mailboxType: MessageMailboxType, lastMessageId: Int, pageSize: Int): ReceivedMessagesPage {
            requestedCursors += lastMessageId
            if (lastMessageId == failingCursor) error("Page failed")
            return pages.getValue(lastMessageId)
        }
        override suspend fun getMessageDetails(apiGlobalKey: String): MessageDetails = error("Not used")
        override suspend fun downloadAttachment(url: String, destination: File): String? = error("Not used")
        override suspend fun markMessageAsRead(apiGlobalKey: String) = Unit
    }

    private inner class FakeGrades : GradesRepository {
        override suspend fun getClassificationPeriods(student: Student): List<ClassificationPeriod> = listOf(
            ClassificationPeriod(1, 1, OffsetDateTime.parse("2026-09-01T00:00:00+02:00"),
                OffsetDateTime.parse("2027-01-31T23:59:59+01:00")),
        )
        override suspend fun getGrades(student: Student, period: ClassificationPeriod): Grades =
            Grades(emptyList(), GradesSettings(false, false, false, false))
    }

    private class FakeTimetable : TimetableRepository {
        var fail = false
        var failure: Exception? = null
        var items: List<TimetableEntry> = emptyList()
        override suspend fun getTimetable(from: LocalDate, to: LocalDate): List<TimetableEntry> {
            failure?.let { throw it }
            if (fail) error("Timetable failed")
            return items
        }
    }

    private class FakeAttendance : AttendanceRepository {
        var items = emptyList<AttendanceEntry>()
        var excusesComplete = true
        var entriesComplete = true
        var excusesFailure: Exception? = null
        override suspend fun getAttendance(from: LocalDate, to: LocalDate): List<AttendanceEntry> = items
        override suspend fun getAttendanceWithCompleteness(from: LocalDate, to: LocalDate) =
            AttendanceFetchResult(items, excusesComplete, entriesComplete, excusesFailure = excusesFailure)
        override suspend fun getSubjects() = error("Not used")
        override suspend fun getStatistics(subjectId: Int) = error("Not used")
    }

    private class FakeTasks : SchoolTasksRepository {
        var items = emptyList<SchoolTask>()
        var failedDetails = emptySet<TaskKey>()
        var detailFailures = emptyMap<TaskKey, Exception>()
        val requests = mutableListOf<Pair<LocalDate, LocalDate>>()
        val failedWeeks = mutableSetOf<LocalDate>()
        override suspend fun getSchoolTasks(from: LocalDate, to: LocalDate): List<SchoolTask> = items
        override suspend fun getSchoolTasksWithCompleteness(from: LocalDate, to: LocalDate): SchoolTasksFetchResult {
            requests += from to to
            if (from in failedWeeks) error("Task week failed")
            val scoped = items.filter { it.date in from..to }
            val keys = scoped.mapTo(mutableSetOf()) { TaskKey(it.rawType, it.id) }
            return SchoolTasksFetchResult(scoped, failedDetails.intersect(keys), detailFailures.filterKeys { it in keys })
        }
    }

    private class FakeInformation : DashboardRepository {
        var items = emptyList<DashboardInformation>()
        override suspend fun getInformation(student: Student): List<DashboardInformation> = items
    }

    private class FakePayments : PaymentsRepository {
        var value = Payments()
        override suspend fun getPayments(student: Student): Payments = value
    }
}
