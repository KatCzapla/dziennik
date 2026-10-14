package pl.usagi.edziennik.persistence.changes

import androidx.room.withTransaction
import pl.usagi.edziennik.domain.changes.ChangeEvent
import pl.usagi.edziennik.domain.changes.LocalAccountId
import pl.usagi.edziennik.domain.changes.SnapshotScope
import javax.inject.Inject
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class SnapshotWrite(val payload: String?, val events: List<ChangeEvent> = emptyList())

interface SnapshotStore {
    suspend fun read(scope: SnapshotScope): String?
    suspend fun update(scope: SnapshotScope, transform: (String?) -> SnapshotWrite): List<ChangeEvent>
    suspend fun deleteAccount(accountId: LocalAccountId)
}

class RoomSnapshotStore @Inject constructor(
    private val database: SnapshotDatabase,
    private val json: Json,
) : SnapshotStore {
    override suspend fun read(scope: SnapshotScope): String? = database.snapshotDao().find(scope.storageKey)

    override suspend fun update(scope: SnapshotScope, transform: (String?) -> SnapshotWrite): List<ChangeEvent> =
        database.withTransaction {
            val write = transform(database.snapshotDao().find(scope.storageKey))
            if (write.events.isNotEmpty()) {
                check(write.payload != null) { "Events require a snapshot write" }
                val outbox = database.outboxDao()
                outbox.pruneHandedOff()
                val rows = write.events.map { event ->
                    OutboxEntity(accountId = scope.accountId.value,
                        payload = json.encodeToString(event), createdAt = System.currentTimeMillis())
                }
                check(outbox.count() + rows.size <= MAX_OUTBOX_EVENTS &&
                    outbox.payloadBytes() + rows.sumOf { it.payload.toByteArray(Charsets.UTF_8).size.toLong() } <= MAX_OUTBOX_BYTES) {
                    "Notification outbox capacity reached; snapshot transaction rolled back"
                }
                outbox.insert(rows)
            }
            write.payload?.let { payload ->
                database.snapshotDao().upsert(SnapshotEntity(scope.storageKey, scope.accountId.value, payload))
            }
            write.events
        }

    override suspend fun deleteAccount(accountId: LocalAccountId) {
        database.withTransaction {
            database.snapshotDao().deleteAccount(accountId.value)
            database.outboxDao().deleteAccount(accountId.value)
            database.syncDiagnosticsDao().deleteAccount(accountId.value)
        }
    }

    companion object {
        const val MAX_OUTBOX_EVENTS = 1000
        const val MAX_OUTBOX_BYTES = 4L * 1024 * 1024
    }
}
