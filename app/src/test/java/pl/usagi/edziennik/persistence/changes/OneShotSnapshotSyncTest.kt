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
    private val sync = OneShotSnapshotSync(
        identity, activeStudents, SnapshotSyncManager(store, Json, identity), messages, grades,
        timetable, attendance, tasks, information, payments, clock,
    )

    @Test fun firstCompleteSyncBuildsBaselinesWithoutEvents() = runTest {
        val result = sync.syncOnce()

        assertTrue(result.events.isEmpty())
        assertTrue(result.issues.isEmpty())
        assertEquals(7, store.payloads.size)
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
        assertEquals(7, store.payloads.size)
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
        assertEquals(6, store.payloads.size)
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
        override suspend fun getTimetable(from: LocalDate, to: LocalDate): List<TimetableEntry> {
            if (fail) error("Timetable failed")
            return emptyList()
        }
    }

    private class FakeAttendance : AttendanceRepository {
        var items = emptyList<AttendanceEntry>()
        var excusesComplete = true
        var entriesComplete = true
        override suspend fun getAttendance(from: LocalDate, to: LocalDate): List<AttendanceEntry> = items
        override suspend fun getAttendanceWithCompleteness(from: LocalDate, to: LocalDate) =
            AttendanceFetchResult(items, excusesComplete, entriesComplete)
        override suspend fun getSubjects() = error("Not used")
        override suspend fun getStatistics(subjectId: Int) = error("Not used")
    }

    private class FakeTasks : SchoolTasksRepository {
        var items = emptyList<SchoolTask>()
        var failedDetails = emptySet<TaskKey>()
        override suspend fun getSchoolTasks(from: LocalDate, to: LocalDate): List<SchoolTask> = items
        override suspend fun getSchoolTasksWithCompleteness(from: LocalDate, to: LocalDate) =
            SchoolTasksFetchResult(items, failedDetails)
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
