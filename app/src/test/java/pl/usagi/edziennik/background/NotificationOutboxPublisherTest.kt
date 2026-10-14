package pl.usagi.edziennik.background

import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import pl.usagi.edziennik.domain.changes.ChangeEvent
import pl.usagi.edziennik.persistence.changes.LocalSnapshotIdentity
import pl.usagi.edziennik.persistence.changes.OutboxEntity
import pl.usagi.edziennik.persistence.changes.TestIdentityStore
import java.time.LocalDate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class NotificationOutboxPublisherTest {
    private val identityStore = TestIdentityStore()
    private val identity = LocalSnapshotIdentity(identityStore)
    private val account = identity.ensureAccountId()
    private val store = MemoryOutbox()
    private val event = ChangeEvent.NewInformation(LocalDate.of(2026, 10, 12), "Informacja")
    private val notifier = RecordingNotifier()

    @Test fun successfulNotifyMarksEventHandedOff() = runTest {
        enqueue(1)
        publisher().publishPending()
        assertEquals(listOf(1), notifier.calls)
        assertTrue(store.pending(account.value).isEmpty())
    }

    @Test fun blockedPublicationRetainsAllEventsInOrder() = runTest {
        enqueue(1); enqueue(2)
        notifier.enabled = false
        publisher().publishPending()
        assertEquals(listOf(1), notifier.calls)
        assertEquals(listOf(1, 2), store.pending(account.value).map { it.id })
        notifier.enabled = true
        publisher().publishPending()
        assertEquals(listOf(1, 1, 2), notifier.calls)
    }

    @Test fun exceptionDuringNotifyRetainsThisAndLaterEvents() = runTest {
        enqueue(1); enqueue(2)
        notifier.fail = true
        publisher().publishPending()
        assertEquals(listOf(1, 2), store.pending(account.value).map { it.id })
        notifier.fail = false
        publisher().publishPending()
        assertEquals(listOf(1, 1, 2), notifier.calls)
    }

    @Test fun crashAfterNotifyBeforeAcknowledgementReusesSameIdAfterRestart() = runTest {
        enqueue(1)
        store.failAcknowledgement = true
        publisher().publishPending()
        store.failAcknowledgement = false
        val restoredIdentity = LocalSnapshotIdentity(identityStore)
        NotificationOutboxPublisher(store, restoredIdentity, notifier, Json).publishPending()
        assertEquals(listOf(1, 1), notifier.calls)
        assertEquals(1, notifier.visible.size)
        assertTrue(store.pending(account.value).isEmpty())
    }

    @Test fun parallelDrainsPublishEachEventOnlyOnce() = runTest {
        enqueue(1); enqueue(2)
        store.suspendReads = true
        val publisher = publisher()
        val first = launch { publisher.publishPending() }
        val second = launch { publisher.publishPending() }
        first.join(); second.join()
        assertEquals(listOf(1, 2), notifier.calls)
    }

    @Test fun logoutBetweenEventsStopsOldAccountPublication() = runTest {
        enqueue(1); enqueue(2)
        store.afterAcknowledgement = { identity.clearAccount() }
        publisher().publishPending()
        assertEquals(listOf(1), notifier.calls)
        assertEquals(listOf(2), store.pending(account.value).map { it.id })
        identity.replaceAccountId()
        publisher().publishPending()
        assertEquals(listOf(1), notifier.calls)
    }

    @Test fun logoutDuringPendingReadPreventsNotify() = runTest {
        enqueue(1)
        store.afterRead = { identity.clearAccount() }
        publisher().publishPending()
        assertTrue(notifier.calls.isEmpty())
    }

    @Test fun accountsDoNotShareEventsEvenIfDeletionFails() = runTest {
        enqueue(1)
        identity.clearAccount()
        val next = identity.ensureAccountId()
        store.rows += OutboxEntity(2, next.value, Json.encodeToString<ChangeEvent>(event), 0)
        publisher().publishPending()
        assertEquals(listOf(2), notifier.calls)
        assertEquals(listOf(1), store.pending(account.value).map { it.id })
    }

    @Test fun restartedSignedOutIdentityDoesNotReadOrPublishOldQueue() = runTest {
        enqueue(1)
        identity.clearAccount()
        NotificationOutboxPublisher(store, LocalSnapshotIdentity(identityStore), notifier, Json).publishPending()
        assertTrue(notifier.calls.isEmpty())
        assertEquals(0, store.reads)
    }

    @Test fun corruptEventIsRetainedAndDoesNotDiscardFollowingEvents() = runTest {
        enqueue(1); enqueue(2)
        store.rows[0] = store.rows[0].copy(payload = "invalid")
        publisher().publishPending()
        assertTrue(notifier.calls.isEmpty())
        assertEquals(2, store.pending(account.value).size)
    }

    @Test fun logoutIsSerializedWithInFlightNotify() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val logoutDone = CountDownLatch(1)
        val publishing = Thread {
            identity.ifCurrentAccount(account) {
                started.countDown()
                check(release.await(5, TimeUnit.SECONDS))
                true
            }
        }
        publishing.start()
        assertTrue(started.await(5, TimeUnit.SECONDS))
        val logout = Thread { identity.clearAccount(); logoutDone.countDown() }
        logout.start()
        try {
            assertFalse(logoutDone.await(50, TimeUnit.MILLISECONDS))
        } finally { release.countDown(); publishing.join(5000); logout.join(5000) }
        assertNull(identity.currentAccountId())
        assertFalse(identity.ifCurrentAccount(account) { fail("notify after logout"); true })
    }

    private fun publisher() = NotificationOutboxPublisher(store, identity, notifier, Json)
    private fun enqueue(id: Int) {
        store.rows += OutboxEntity(id, account.value, Json.encodeToString<ChangeEvent>(event), 0)
    }

    private class RecordingNotifier : ChangeNotifier {
        val calls = mutableListOf<Int>()
        val visible = mutableMapOf<Int, ChangeEvent>()
        var enabled = true
        var fail = false
        override fun publish(id: Int, event: ChangeEvent): Boolean {
            calls += id
            if (fail) error("test notify failure")
            if (!enabled) return false
            visible[id] = event
            return true
        }
    }

    private class MemoryOutbox : NotificationOutboxStore {
        val rows = mutableListOf<OutboxEntity>()
        var reads = 0
        var suspendReads = false
        var failAcknowledgement = false
        var afterRead: (() -> Unit)? = null
        var afterAcknowledgement: (() -> Unit)? = null
        override suspend fun pending(accountId: String): List<OutboxEntity> {
            reads++
            val pending = rows.filter { it.accountId == accountId && it.handedOffAt == null }
            if (suspendReads) delay(1)
            afterRead?.invoke()
            return pending
        }
        override suspend fun markHandedOff(id: Int, accountId: String) {
            if (failAcknowledgement) error("test acknowledgement failure")
            val index = rows.indexOfFirst { it.id == id && it.accountId == accountId }
            rows[index] = rows[index].copy(handedOffAt = 1)
            afterAcknowledgement?.invoke()
        }
    }
}
