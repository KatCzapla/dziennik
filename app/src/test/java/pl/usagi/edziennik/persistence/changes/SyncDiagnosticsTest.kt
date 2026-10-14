package pl.usagi.edziennik.persistence.changes

import android.app.Application
import androidx.room.Room
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import pl.usagi.edziennik.background.SyncRunResult
import pl.usagi.edziennik.background.SyncRunStatus
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
class SyncDiagnosticsTest {
    private lateinit var database: SnapshotDatabase
    private val store = TestIdentityStore()
    private val identity = LocalSnapshotIdentity(store)
    private val clock = Clock.fixed(Instant.parse("2026-10-13T09:00:00Z"), ZoneOffset.UTC)
    private lateinit var diagnostics: SyncDiagnostics

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), SnapshotDatabase::class.java)
            .allowMainThreadQueries().build()
        diagnostics = SyncDiagnostics(database, identity, clock)
    }

    @After fun tearDown() { database.close() }

    @Test fun recordsStartBeforeExecutionAndFinishesWithCounts() = runBlocking {
        identity.ensureAccountId()
        diagnostics.recordRun(false) { counters ->
            val started = diagnostics.history().single()
            assertEquals(clock.millis(), started.startedAt)
            assertNull(started.finishedAt)
            assertNull(started.result)
            counters.detectedEvents = 3
            counters.handedOffNotifications = 2
            SyncRunResult(SyncRunStatus.COMPLETE)
        }
        val row = diagnostics.history().single()
        assertEquals("PERIODIC", row.source)
        assertEquals("SUCCESS", row.result)
        assertEquals(clock.millis(), row.finishedAt)
        assertEquals(3, row.detectedEvents)
        assertEquals(2, row.handedOffNotifications)
    }

    @Test fun classificationMatchesExistingWorkerResults() = runBlocking {
        identity.ensureAccountId()
        val results = listOf(
            SyncRunResult(SyncRunStatus.COMPLETE),
            SyncRunResult(SyncRunStatus.PARTIAL, reason = "INCOMPLETE_AREAS"),
            SyncRunResult(SyncRunStatus.FAILED, retryable = true, reason = "NETWORK"),
            SyncRunResult(SyncRunStatus.PARTIAL, retryable = true, reason = "SERVER"),
            SyncRunResult(SyncRunStatus.FAILED, reason = "LOGIN_REJECTED"),
        )
        results.forEach { expected -> assertEquals(expected, diagnostics.recordRun(true) { expected }) }
        assertEquals(listOf("FAILURE", "RETRY", "RETRY", "PARTIAL", "SUCCESS"), diagnostics.history().map { it.result })
        assertTrue(diagnostics.history().all { it.source == "MANUAL" })
    }

    @Test fun keepsOnlyTwentyMostRecentRuns() = runBlocking {
        identity.ensureAccountId()
        repeat(25) { diagnostics.recordRun(false) { SyncRunResult(SyncRunStatus.COMPLETE) } }
        val rows = diagnostics.history()
        assertEquals(20, rows.size)
        assertEquals(25L, rows.first().id)
        assertEquals(6L, rows.last().id)
        database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM sync_diagnostics").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(20, cursor.getInt(0))
        }
    }

    @Test fun restartRetainsAccountHistory() = runBlocking {
        identity.ensureAccountId()
        diagnostics.recordRun(false) { SyncRunResult(SyncRunStatus.COMPLETE) }
        val restarted = SyncDiagnostics(database, LocalSnapshotIdentity(store), clock)
        assertEquals(diagnostics.history(), restarted.history())
    }

    @Test fun differentAccountAndLogoutHideOldHistoryEvenWithoutDeletion() = runBlocking {
        val old = identity.ensureAccountId()
        diagnostics.recordRun(false) { SyncRunResult(SyncRunStatus.COMPLETE) }
        identity.clearAccount()
        assertTrue(diagnostics.history().isEmpty())
        assertEquals(0, diagnostics.pendingCount())
        val restarted = SyncDiagnostics(database, LocalSnapshotIdentity(store), clock)
        assertTrue(restarted.history().isEmpty())
        val new = identity.ensureAccountId()
        assertNotEquals(old, new)
        assertTrue(diagnostics.history().isEmpty())
        diagnostics.recordRun(true) { SyncRunResult(SyncRunStatus.PARTIAL) }
        assertEquals(listOf(new.value), diagnostics.history().map { it.accountId })
        assertEquals(1, database.syncDiagnosticsDao().history(old.value).size)
    }

    @Test fun logoutDuringRunDoesNotRecordIntoNewAccount() = runBlocking {
        identity.ensureAccountId()
        diagnostics.recordRun(false) {
            identity.clearAccount()
            identity.ensureAccountId()
            SyncRunResult(SyncRunStatus.FAILED, reason = "ACCOUNT_CHANGED")
        }
        assertTrue(diagnostics.history().isEmpty())
    }

    @Test fun logoutCleanupDeletesDiagnosticHistory() = runBlocking {
        val account = identity.ensureAccountId()
        diagnostics.recordRun(false) { SyncRunResult(SyncRunStatus.COMPLETE) }
        identity.clearAccount()
        RoomSnapshotStore(database, Json).deleteAccount(account)
        assertTrue(database.syncDiagnosticsDao().history(account.value).isEmpty())
    }

    @Test fun unsafeReasonIsNeverStored() = runBlocking {
        identity.ensureAccountId()
        diagnostics.recordRun(false) { SyncRunResult(SyncRunStatus.FAILED, reason = "secret-token-example") }
        assertEquals("UNKNOWN_FAILURE", diagnostics.history().single().reason)
    }

    @Test fun cancellationIsRecordedAndRethrown() = runBlocking {
        identity.ensureAccountId()
        try {
            diagnostics.recordRun(false) { throw CancellationException("must not persist") }
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
        assertEquals("FAILURE", diagnostics.history().single().result)
        assertEquals("CANCELLED", diagnostics.history().single().reason)
        assertNotNull(diagnostics.history().single().finishedAt)
    }

    @Test fun transportFailureIsRecordedWithoutExceptionMessage() = runBlocking {
        identity.ensureAccountId()
        try {
            diagnostics.recordRun(false) { throw IOException("private diagnostic message") }
            fail("Failure must propagate")
        } catch (_: IOException) { }
        assertEquals("RETRY", diagnostics.history().single().result)
        assertEquals("NETWORK", diagnostics.history().single().reason)
    }

    @Test fun pendingCountIsAccountScopedAndExcludesHandedOffRows() = runBlocking {
        val account = identity.ensureAccountId()
        database.outboxDao().insert(listOf(
            OutboxEntity(accountId = account.value, payload = "not read", createdAt = 0),
            OutboxEntity(accountId = account.value, payload = "not read", createdAt = 0, handedOffAt = 1),
            OutboxEntity(accountId = "different-local-account", payload = "not read", createdAt = 0),
        ))
        assertEquals(1, diagnostics.pendingCount())
    }

    @Test fun missingAccountDoesNotCreateHistoryOrBlockExecution() = runBlocking {
        var called = false
        diagnostics.recordRun(false) { called = true; SyncRunResult(SyncRunStatus.FAILED, reason = "NO_ACCOUNT") }
        assertTrue(called)
        assertTrue(diagnostics.history().isEmpty())
    }

    @Test fun closingAndReopeningRoomRetainsHistory() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val name = "diagnostics-restart.db"
        context.deleteDatabase(name)
        identity.ensureAccountId()
        val first = Room.databaseBuilder(context, SnapshotDatabase::class.java, name).build()
        try {
            SyncDiagnostics(first, identity, clock).recordRun(true) { SyncRunResult(SyncRunStatus.COMPLETE) }
        } finally { first.close() }
        val reopened = Room.databaseBuilder(context, SnapshotDatabase::class.java, name).build()
        try {
            val rows = SyncDiagnostics(reopened, LocalSnapshotIdentity(store), clock).history()
            assertEquals("SUCCESS", rows.single().result)
            assertEquals("MANUAL", rows.single().source)
        } finally { reopened.close(); context.deleteDatabase(name) }
    }

    @Test fun diagnosticStorageFailureDoesNotChangeSynchronizationResult() = runBlocking {
        identity.ensureAccountId()
        database.close()
        var called = false
        val expected = SyncRunResult(SyncRunStatus.PARTIAL, reason = "INCOMPLETE_AREAS")
        val actual = diagnostics.recordRun(false) { called = true; expected }
        assertTrue(called)
        assertEquals(expected, actual)
    }
}
