package pl.usagi.edziennik.persistence.changes

import android.app.Application
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import pl.usagi.edziennik.domain.SchoolTask
import pl.usagi.edziennik.domain.SchoolTaskType
import pl.usagi.edziennik.domain.changes.*
import java.time.LocalDate
import java.util.UUID
import pl.usagi.edziennik.background.ChangeNotifier
import pl.usagi.edziennik.background.NotificationOutboxPublisher
import pl.usagi.edziennik.background.RoomNotificationOutboxStore
import pl.usagi.edziennik.background.PeriodicSyncRun
import pl.usagi.edziennik.background.SyncRunStatus

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class RoomNotificationOutboxTest {
    private lateinit var db: SnapshotDatabase
    private lateinit var store: RoomSnapshotStore
    private val identity = LocalSnapshotIdentity(TestIdentityStore())
    private val json = Json
    private val day = LocalDate.of(2026, 10, 12)
    private val event = ChangeEvent.NewInformation(day, "Informacja")
    private val account = identity.ensureAccountId()
    private val scope = SnapshotScope(account, null, SnapshotArea.MESSAGES)

    @Before fun createDatabase() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), SnapshotDatabase::class.java)
            .allowMainThreadQueries().build()
        store = RoomSnapshotStore(db, json)
    }

    @After fun closeDatabase() { db.close() }

    @Test fun snapshotAndEventsAreCommittedTogether() = runBlocking {
        store.update(scope) { SnapshotWrite("snapshot", listOf(event, event)) }
        assertEquals("snapshot", store.read(scope))
        val pending = db.outboxDao().pending(account.value)
        assertEquals(2, pending.size)
        assertTrue(pending[0].id < pending[1].id)
        assertEquals(event, json.decodeFromString<ChangeEvent>(pending.first().payload))
    }

    @Test fun snapshotFailureRollsBackAlreadyInsertedEvents() = runBlocking {
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_snapshot BEFORE INSERT ON snapshots BEGIN SELECT RAISE(ABORT, 'test rollback'); END")
        assertTrue(runCatching { store.update(scope) { SnapshotWrite("next", listOf(event)) } }.isFailure)
        assertNull(store.read(scope))
        assertEquals(0, db.outboxDao().count())
    }

    @Test fun eventInsertionFailurePreservesPreviousSnapshot() = runBlocking {
        store.update(scope) { SnapshotWrite("previous") }
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_event BEFORE INSERT ON notification_outbox BEGIN SELECT RAISE(ABORT, 'test rollback'); END")
        assertTrue(runCatching { store.update(scope) { SnapshotWrite("next", listOf(event)) } }.isFailure)
        assertEquals("previous", store.read(scope))
        assertEquals(0, db.outboxDao().count())
    }

    @Test fun firstCompleteBaselineDoesNotQueueEvents() = runBlocking {
        manager().sync(scope, SyncCompleteness.Complete, messages(1))
        assertNotNull(store.read(scope))
        assertEquals(0, db.outboxDao().count())
    }

    @Test fun laterCompleteQueuesExactlyOnce() = runBlocking {
        manager().sync(scope, SyncCompleteness.Complete, messages(1))
        manager().sync(scope, SyncCompleteness.Complete, messages(1, 2))
        manager().sync(scope, SyncCompleteness.Complete, messages(1, 2))
        assertEquals(1, db.outboxDao().pending(account.value).size)
    }

    @Test fun failedAndUnsupportedPartialDoNotWriteSnapshotOrEvents() = runBlocking {
        manager().sync(scope, SyncCompleteness.Complete, messages(1))
        val before = store.read(scope)
        manager().sync(scope, SyncCompleteness.Failed)
        manager().sync(scope, SyncCompleteness.Partial(mainListComplete = false), messages(2))
        assertEquals(before, store.read(scope))
        assertEquals(0, db.outboxDao().count())
    }

    @Test fun supportedPartialQueuesOnlyDetectedChangesAndKeepsMissingTasks() = runBlocking {
        val tasksScope = scope.copy(studentScopeId = identity.studentScopeId(1), area = SnapshotArea.TASKS, range = "week")
        val task = SchoolTask(SchoolTaskType.Homework, 4, day, "Matematyka", false, 1)
        manager().sync(tasksScope, SyncCompleteness.Complete, CurrentSnapshotData.Tasks(listOf(task)))
        val added = task.copy(id = 2)
        manager().sync(tasksScope, SyncCompleteness.Partial(true, failedTaskDetails = setOf(TaskKey(4, 2))),
            CurrentSnapshotData.Tasks(listOf(added)))
        val snapshot = json.decodeFromString<SnapshotPayload>(store.read(tasksScope)!!) as SnapshotPayload.Tasks
        assertEquals(setOf(1, 2), snapshot.items.map { it.key.id }.toSet())
        assertEquals(1, db.outboxDao().pending(account.value).size)
    }

    @Test fun fullOutboxRollsBackWithoutDiscardingPendingEvents() = runBlocking {
        store.update(scope) { SnapshotWrite("previous", List(RoomSnapshotStore.MAX_OUTBOX_EVENTS) { event }) }
        assertTrue(runCatching { store.update(scope) { SnapshotWrite("next", listOf(event)) } }.isFailure)
        assertEquals("previous", store.read(scope))
        assertEquals(RoomSnapshotStore.MAX_OUTBOX_EVENTS, db.outboxDao().pending(account.value).size)
    }

    @Test fun oversizedEventRollsBackSnapshot() = runBlocking {
        val huge = event.copy(subject = "x".repeat(RoomSnapshotStore.MAX_OUTBOX_BYTES.toInt() + 1))
        assertTrue(runCatching { store.update(scope) { SnapshotWrite("next", listOf(huge)) } }.isFailure)
        assertNull(store.read(scope))
        assertEquals(0, db.outboxDao().count())
    }

    @Test fun handedOffRowsCanBePrunedButIdsAreNotReused() = runBlocking {
        store.update(scope) { SnapshotWrite("first", listOf(event)) }
        val id = db.outboxDao().pending(account.value).single().id
        db.outboxDao().markHandedOff(id, account.value, 1)
        store.update(scope) { SnapshotWrite("second", listOf(event)) }
        val next = db.outboxDao().pending(account.value).single()
        assertTrue(next.id > id)
        assertEquals(1, db.outboxDao().count())
    }

    @Test fun deletingAccountRemovesItsSnapshotsAndOutboxOnly() = runBlocking {
        val other = LocalAccountId(UUID.randomUUID().toString())
        store.update(scope) { SnapshotWrite("first", listOf(event)) }
        store.update(scope.copy(accountId = other)) { SnapshotWrite("second", listOf(event)) }
        store.deleteAccount(account)
        assertNull(store.read(scope))
        assertTrue(db.outboxDao().pending(account.value).isEmpty())
        assertEquals(1, db.outboxDao().pending(other.value).size)
    }

    @Test fun invalidatedAccountCannotQueueChangesDuringSync() = runBlocking {
        manager().sync(scope, SyncCompleteness.Complete, messages(1))
        identity.clearAccount()
        assertTrue(manager().sync(scope, SyncCompleteness.Complete, messages(1, 2)).isEmpty())
        assertEquals(0, db.outboxDao().count())
    }

    @Test fun logoutAfterDetectionBeforeCommitCannotPublishOrphanedEvents() = runBlocking {
        manager().sync(scope, SyncCompleteness.Complete, messages(1))
        val logoutDuringWrite = object : SnapshotStore by store {
            override suspend fun update(scope: SnapshotScope, transform: (String?) -> SnapshotWrite) =
                store.update(scope) { saved ->
                    transform(saved).also { identity.clearAccount() }
                }
        }
        SnapshotSyncManager(logoutDuringWrite, json, identity).sync(scope, SyncCompleteness.Complete, messages(1, 2))
        assertEquals(1, db.outboxDao().pending(account.value).size)
        var publishes = 0
        val notifier = object : ChangeNotifier {
            override fun publish(id: Int, event: ChangeEvent): Boolean { publishes++; return true }
        }
        val publisher = NotificationOutboxPublisher(RoomNotificationOutboxStore(db), identity, notifier, json)
        publisher.publishPending()
        identity.ensureAccountId()
        publisher.publishPending()
        assertEquals(0, publishes)
    }

    @Test fun migrationPreservesVersionOneSnapshots() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val name = "outbox-migration-test.db"
        context.deleteDatabase(name)
        context.openOrCreateDatabase(name, 0, null).use { old ->
            old.execSQL("CREATE TABLE snapshots (scopeKey TEXT NOT NULL PRIMARY KEY, accountId TEXT NOT NULL, payload TEXT NOT NULL)")
            old.execSQL("CREATE INDEX index_snapshots_accountId ON snapshots (accountId)")
            old.execSQL("INSERT INTO snapshots VALUES ('scope', 'account', 'baseline')")
            old.version = 1
        }
        val migrated = Room.databaseBuilder(context, SnapshotDatabase::class.java, name)
            .addMigrations(SNAPSHOT_MIGRATION_1_2, SNAPSHOT_MIGRATION_2_3).allowMainThreadQueries().build()
        try {
            assertEquals("baseline", migrated.snapshotDao().find("scope"))
            assertEquals(0, migrated.outboxDao().count())
        } finally { migrated.close(); context.deleteDatabase(name) }
    }

    @Test fun pendingEventSurvivesDatabaseReopenAndIsPublishedFromRoom() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val name = "outbox-restart-test.db"
        context.deleteDatabase(name)
        fun open() = Room.databaseBuilder(context, SnapshotDatabase::class.java, name)
            .allowMainThreadQueries().build()
        var persistent = open()
        try {
            RoomSnapshotStore(persistent, json).update(scope) { SnapshotWrite("baseline", listOf(event)) }
            val id = persistent.outboxDao().pending(account.value).single().id
            persistent.close()
            persistent = open()
            val delivered = mutableListOf<Int>()
            val notifier = object : ChangeNotifier {
                override fun publish(id: Int, event: ChangeEvent): Boolean {
                    delivered += id
                    return true
                }
            }
            val publisher = NotificationOutboxPublisher(RoomNotificationOutboxStore(persistent), identity, notifier, json)
            publisher.publishPending()
            publisher.publishPending()
            assertEquals(listOf(id), delivered)
            assertTrue(persistent.outboxDao().pending(account.value).isEmpty())
        } finally { persistent.close(); context.deleteDatabase(name) }
    }

    @Test fun partialWithoutBaselineDoesNotQueueEvents() = runBlocking {
        val tasksScope = scope.copy(studentScopeId = identity.studentScopeId(1), area = SnapshotArea.TASKS, range = "week")
        val task = SchoolTask(SchoolTaskType.Homework, 4, day, null, false, 1)
        manager().sync(tasksScope, SyncCompleteness.Partial(true, failedTaskDetails = setOf(TaskKey(4, 1))),
            CurrentSnapshotData.Tasks(listOf(task)))
        assertNull(store.read(tasksScope))
        assertEquals(0, db.outboxDao().count())
    }

    @Test fun retryPreservesCommittedSnapshotAndPendingEventsWithoutDuplicates() = runBlocking {
        manager().sync(scope, SyncCompleteness.Complete, messages(1))
        var fail = true
        val sync = object : SnapshotSynchronizer {
            override suspend fun syncOnce(): OneShotSyncResult {
                val events = manager().sync(scope, SyncCompleteness.Complete, messages(1, 2))
                if (fail) throw java.io.IOException("test transport failure after commit")
                return OneShotSyncResult(events, emptyMap())
            }
        }
        val notifier = object : ChangeNotifier {
            override fun publish(id: Int, event: ChangeEvent) = false
        }
        val publisher = NotificationOutboxPublisher(RoomNotificationOutboxStore(db), identity, notifier, json)
        val runner = PeriodicSyncRun({ true }, sync, publisher)
        assertTrue(runner.run().retryable)
        val beforeRetry = store.read(scope)
        val pending = db.outboxDao().pending(account.value)
        assertEquals(1, pending.size)
        fail = false
        assertEquals(SyncRunStatus.COMPLETE, runner.run().status)
        assertEquals(beforeRetry, store.read(scope))
        assertEquals(pending, db.outboxDao().pending(account.value))
    }

    private fun manager() = SnapshotSyncManager(store, json, identity)
    private fun messages(vararg ids: Int) = CurrentSnapshotData.Messages(
        ids.map { MessageSnapshot(it, "Temat", day.atStartOfDay()) })
}

class TestIdentityStore : LocalIdentityStore {
    private val values = mutableMapOf<String, String>()
    override fun get(key: String) = values[key]
    override fun put(key: String, value: String) { values[key] = value }
    override fun clear() { values.clear() }
}
