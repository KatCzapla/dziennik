package pl.usagi.edziennik.persistence.changes

import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.usagi.edziennik.domain.AttendanceEntry
import pl.usagi.edziennik.domain.AttendanceStatus
import pl.usagi.edziennik.domain.ReceivedMessage
import pl.usagi.edziennik.domain.SchoolTask
import pl.usagi.edziennik.domain.SchoolTaskDetails
import pl.usagi.edziennik.domain.SchoolTaskType
import pl.usagi.edziennik.domain.changes.ChangeEvent
import pl.usagi.edziennik.domain.changes.LocalAccountId
import pl.usagi.edziennik.domain.changes.AttendanceKey
import pl.usagi.edziennik.domain.changes.AttendanceSnapshot
import pl.usagi.edziennik.domain.changes.GradeFingerprint
import pl.usagi.edziennik.domain.changes.GradeSnapshot
import pl.usagi.edziennik.domain.changes.InformationKey
import pl.usagi.edziennik.domain.changes.InformationSnapshot
import pl.usagi.edziennik.domain.changes.MessageSnapshot
import pl.usagi.edziennik.domain.changes.PaymentAccountSnapshot
import pl.usagi.edziennik.domain.changes.PaymentsSnapshot
import pl.usagi.edziennik.domain.changes.SnapshotArea
import pl.usagi.edziennik.domain.changes.SnapshotPayload
import pl.usagi.edziennik.domain.changes.SnapshotScope
import pl.usagi.edziennik.domain.changes.SubstitutionSnapshot
import pl.usagi.edziennik.domain.changes.TaskDetailsSnapshot
import pl.usagi.edziennik.domain.changes.TaskKey
import pl.usagi.edziennik.domain.changes.TaskSnapshot
import pl.usagi.edziennik.domain.changes.TimetableSlot
import pl.usagi.edziennik.domain.changes.TimetableSnapshot
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime

class SnapshotSyncManagerTest {
    private val day = LocalDate.of(2026, 9, 29)
    private val identity = LocalSnapshotIdentity(MemoryIdentityStore())
    private val store = MemorySnapshotStore()
    private val json = Json

    @Test fun firstCompleteCreatesBaselineWithoutEvent() = runSyncTest {
        val scope = scope(SnapshotArea.MESSAGES)
        assertTrue(manager().sync(scope, SyncCompleteness.Complete, messages(1)).isEmpty())
        assertEquals(1, store.payloads.size)
    }

    @Test fun secondCompleteWithSameDataIsQuiet() = runSyncTest {
        val scope = scope(SnapshotArea.MESSAGES)
        manager().sync(scope, SyncCompleteness.Complete, messages(1))
        assertTrue(manager().sync(scope, SyncCompleteness.Complete, messages(1)).isEmpty())
    }

    @Test fun secondCompleteWithNewDataEmitsChange() = runSyncTest {
        val scope = scope(SnapshotArea.MESSAGES)
        manager().sync(scope, SyncCompleteness.Complete, messages(1))
        val events = manager().sync(scope, SyncCompleteness.Complete, messages(2, 1))
        assertEquals(listOf(ChangeEvent.NewMessage(2, "Temat 2", day.atStartOfDay())), events)
    }

    @Test fun recreatedManagerReadsPersistedBaseline() = runSyncTest {
        val scope = scope(SnapshotArea.MESSAGES)
        manager().sync(scope, SyncCompleteness.Complete, messages(1))
        val recreated = SnapshotSyncManager(store, json, identity)
        assertEquals(1, recreated.sync(scope, SyncCompleteness.Complete, messages(1, 2)).size)
    }

    @Test fun localAccountsDoNotShareSnapshots() = runSyncTest {
        val oldScope = scope(SnapshotArea.MESSAGES)
        manager().sync(oldScope, SyncCompleteness.Complete, messages(1))
        val newAccount = identity.replaceAccountId()
        val newScope = SnapshotScope(newAccount, null, SnapshotArea.MESSAGES)
        assertNotEquals(oldScope.accountId, newScope.accountId)
        assertTrue(manager().sync(newScope, SyncCompleteness.Complete, messages(1, 2)).isEmpty())
        assertTrue(manager().sync(oldScope, SyncCompleteness.Complete, messages(1, 2)).isEmpty())
    }

    @Test fun localStudentScopesDoNotShareSnapshots() = runSyncTest {
        val account = identity.ensureAccountId()
        val first = SnapshotScope(account, identity.studentScopeId(17), SnapshotArea.ATTENDANCE, "2026-09-28/2026-10-02")
        val second = SnapshotScope(account, identity.studentScopeId(18), SnapshotArea.ATTENDANCE, "2026-09-28/2026-10-02")
        assertNotEquals(first.studentScopeId, second.studentScopeId)
        manager().sync(first, SyncCompleteness.Complete, attendance(AttendanceStatus.Absent))
        assertTrue(manager().sync(second, SyncCompleteness.Complete, attendance(AttendanceStatus.Late)).isEmpty())
    }

    @Test fun failedDoesNotCreateOrChangeBaseline() = runSyncTest {
        val scope = scope(SnapshotArea.MESSAGES)
        manager().sync(scope, SyncCompleteness.Failed)
        assertTrue(store.payloads.isEmpty())
        manager().sync(scope, SyncCompleteness.Complete, messages(1))
        manager().sync(scope, SyncCompleteness.Failed, messages(2))
        assertEquals(1, manager().sync(scope, SyncCompleteness.Complete, messages(1, 2)).size)
    }

    @Test fun partialMainListDoesNotDeleteOrNotify() = runSyncTest {
        val scope = scope(SnapshotArea.ATTENDANCE, "week")
        manager().sync(scope, SyncCompleteness.Complete, attendance(AttendanceStatus.Absent))
        val saved = store.payloads.getValue(scope.storageKey)
        assertTrue(manager().sync(scope, SyncCompleteness.Partial(mainListComplete = false),
            CurrentSnapshotData.Attendance(emptyList())).isEmpty())
        assertEquals(saved, store.payloads.getValue(scope.storageKey))
        assertTrue(manager().sync(scope, SyncCompleteness.Complete, attendance(AttendanceStatus.Absent)).isEmpty())
    }

    @Test fun attendanceWithFailedExcusesKeepsKnownEntryChanges() = runSyncTest {
        val scope = scope(SnapshotArea.ATTENDANCE, "week")
        manager().sync(scope, SyncCompleteness.Complete, attendance(AttendanceStatus.Absent))
        val partial = SyncCompleteness.Partial(mainListComplete = true, attendanceExcusesComplete = false)
        assertEquals(1, manager().sync(scope, partial, attendance(AttendanceStatus.Late)).size)
        assertTrue(manager().sync(scope, SyncCompleteness.Complete, attendance(AttendanceStatus.Late)).isEmpty())
    }

    @Test fun failedTaskDetailsPreservePreviousDetailsForLaterComplete() = runSyncTest {
        val scope = scope(SnapshotArea.TASKS, "week")
        manager().sync(scope, SyncCompleteness.Complete, tasks("Stary opis"))
        val partial = SyncCompleteness.Partial(mainListComplete = true, failedTaskDetails = setOf(TaskKey(4, 3)))
        assertTrue(manager().sync(scope, partial, tasks(null)).isEmpty())
        assertTrue(manager().sync(scope, SyncCompleteness.Complete, tasks("Stary opis")).isEmpty())
        assertEquals(1, manager().sync(scope, SyncCompleteness.Complete, tasks("Nowy opis")).size)
    }

    @Test fun partialCannotCreateBaseline() = runSyncTest {
        val scope = scope(SnapshotArea.ATTENDANCE, "week")
        val partial = SyncCompleteness.Partial(mainListComplete = true, attendanceExcusesComplete = false)
        assertTrue(manager().sync(scope, partial, attendance(AttendanceStatus.Absent)).isEmpty())
        assertTrue(store.payloads.isEmpty())
        assertTrue(manager().sync(scope, SyncCompleteness.Complete, attendance(AttendanceStatus.Absent)).isEmpty())
    }

    @Test fun logoutInvalidatesIdentityAndDeletesOnlyItsSnapshots() = runSyncTest {
        val oldScope = scope(SnapshotArea.MESSAGES)
        manager().sync(oldScope, SyncCompleteness.Complete, messages(1))
        val oldId = identity.clearAccount()
        store.deleteAccount(requireNotNull(oldId))
        assertNull(identity.currentAccountId())
        assertTrue(store.payloads.isEmpty())
        assertTrue(manager().sync(oldScope, SyncCompleteness.Complete, messages(2)).isEmpty())
    }

    @Test fun localIdsRemainStableAcrossRegistryRecreation() {
        val preferences = MemoryIdentityStore()
        val first = LocalSnapshotIdentity(preferences)
        val account = first.ensureAccountId()
        val student = first.studentScopeId(17)
        val recreated = LocalSnapshotIdentity(preferences)
        assertEquals(account, recreated.ensureAccountId())
        assertEquals(student, recreated.studentScopeId(17))
        assertNotEquals(student, recreated.studentScopeId(18))
    }

    @Test fun allSnapshotPayloadsRoundTripWithoutSessionKeys() {
        val slot = TimetableSlot(day, LocalTime.of(9, 0), LocalTime.of(9, 45))
        val payloads: List<SnapshotPayload> = listOf(
            SnapshotPayload.Messages(listOf(MessageSnapshot(1, "Temat", day.atStartOfDay()))),
            SnapshotPayload.Grades(listOf(GradeSnapshot(3, 1, "Matematyka", 17,
                GradeFingerprint("5", day, BigDecimal.ONE, "Kartkówka", null, null, null), true))),
            SnapshotPayload.Timetable(listOf(TimetableSnapshot(slot, "Matematyka", null, null, null,
                null, false, listOf(SubstitutionSnapshot("Nauczyciel", null, null)), false))),
            SnapshotPayload.Attendance(listOf(AttendanceSnapshot(AttendanceKey(day, 7, 8), null,
                AttendanceStatus.Unknown(999)))),
            SnapshotPayload.Tasks(listOf(TaskSnapshot(TaskKey(4, 3), day, "Matematyka", false,
                TaskDetailsSnapshot("Opis", day.plusDays(1))))),
            SnapshotPayload.Information(listOf(InformationSnapshot(InformationKey(day, "Temat")))),
            SnapshotPayload.Payments(PaymentsSnapshot(listOf(PaymentAccountSnapshot("Nadpłata", BigDecimal("6.00"))))),
        )

        payloads.forEach { payload ->
            val encoded = json.encodeToString<SnapshotPayload>(payload)
            assertEquals(payload, json.decodeFromString<SnapshotPayload>(encoded))
            assertTrue(!encoded.contains("must-not-be-stored"))
        }
    }

    @Test fun olderSnapshotsWithoutNotificationFieldsStillDecode() {
        val oldMessage = json.encodeToString(MessageSnapshot(1, "Temat", day.atStartOfDay()))
        val oldAttendance = json.encodeToString(AttendanceSnapshot(AttendanceKey(day, 7, 8), null,
            AttendanceStatus.Absent))

        assertEquals(null, json.decodeFromString<MessageSnapshot>(oldMessage).sender)
        assertEquals(null, json.decodeFromString<AttendanceSnapshot>(oldAttendance).startTime)
    }

    private fun manager() = SnapshotSyncManager(store, json, identity)

    private fun scope(area: SnapshotArea, range: String? = null): SnapshotScope {
        val account = identity.ensureAccountId()
        val student = if (area == SnapshotArea.MESSAGES) null else identity.studentScopeId(17)
        return SnapshotScope(account, student, area, range)
    }

    private fun messages(vararg ids: Int) = CurrentSnapshotData.Messages(ids.map { id ->
        MessageSnapshot(id, "Temat $id", day.atStartOfDay())
    })

    private fun attendance(status: AttendanceStatus) = CurrentSnapshotData.Attendance(listOf(
        AttendanceEntry(7, 8, 2, status, day, LocalTime.of(9, 0), LocalTime.of(9, 45), "Matematyka", null),
    ))

    private fun tasks(description: String?) = CurrentSnapshotData.Tasks(listOf(
        SchoolTask(SchoolTaskType.Homework, 4, day, "Matematyka", false, 3,
            description?.let { SchoolTaskDetails(it, null, null) }),
    ))

    private fun runSyncTest(block: suspend () -> Unit) = kotlinx.coroutines.test.runTest { block() }

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
}
