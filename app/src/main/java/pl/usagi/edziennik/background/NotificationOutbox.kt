package pl.usagi.edziennik.background

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import pl.usagi.edziennik.domain.changes.ChangeEvent
import pl.usagi.edziennik.persistence.changes.LocalSnapshotIdentity
import pl.usagi.edziennik.persistence.changes.OutboxEntity
import pl.usagi.edziennik.persistence.changes.SnapshotDatabase
import javax.inject.Inject
import javax.inject.Singleton

fun interface PendingNotificationPublisher {
    suspend fun publishPending()
    suspend fun publishPending(onHandedOff: () -> Unit) { publishPending() }
}

interface NotificationOutboxStore {
    suspend fun pending(accountId: String): List<OutboxEntity>
    suspend fun markHandedOff(id: Int, accountId: String)
}

class RoomNotificationOutboxStore @Inject constructor(
    private val database: SnapshotDatabase,
) : NotificationOutboxStore {
    override suspend fun pending(accountId: String) = database.outboxDao().pending(accountId)
    override suspend fun markHandedOff(id: Int, accountId: String) =
        database.outboxDao().markHandedOff(id, accountId, System.currentTimeMillis())
}

@Singleton
class NotificationOutboxPublisher @Inject constructor(
    private val store: NotificationOutboxStore,
    private val identity: LocalSnapshotIdentity,
    private val notifier: ChangeNotifier,
    private val json: Json,
) : PendingNotificationPublisher {
    private val mutex = Mutex()

    override suspend fun publishPending() = publishPending {}

    override suspend fun publishPending(onHandedOff: () -> Unit) = mutex.withLock {
        val accountId = identity.currentAccountId() ?: return@withLock
        for (row in store.pending(accountId.value)) {
            try {
                val event = json.decodeFromString<ChangeEvent>(row.payload)
                val handedOff = identity.ifCurrentAccount(accountId) { notifier.publish(row.id, event) }
                if (!handedOff) return@withLock
                onHandedOff()
                store.markHandedOff(row.id, accountId.value)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Retain this and later events, preserving FIFO order on the next attempt.
                try { Log.w("NotificationOutbox", "Publication incomplete; pending events retained") }
                catch (_: RuntimeException) { }
                return@withLock
            }
        }
    }
}
