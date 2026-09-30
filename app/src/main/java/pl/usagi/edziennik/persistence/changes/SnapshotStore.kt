package pl.usagi.edziennik.persistence.changes

import androidx.room.withTransaction
import pl.usagi.edziennik.domain.changes.ChangeEvent
import pl.usagi.edziennik.domain.changes.LocalAccountId
import pl.usagi.edziennik.domain.changes.SnapshotScope
import javax.inject.Inject

data class SnapshotWrite(val payload: String?, val events: List<ChangeEvent> = emptyList())

interface SnapshotStore {
    suspend fun update(scope: SnapshotScope, transform: (String?) -> SnapshotWrite): List<ChangeEvent>
    suspend fun deleteAccount(accountId: LocalAccountId)
}

class RoomSnapshotStore @Inject constructor(private val database: SnapshotDatabase) : SnapshotStore {
    override suspend fun update(scope: SnapshotScope, transform: (String?) -> SnapshotWrite): List<ChangeEvent> =
        database.withTransaction {
            val write = transform(database.snapshotDao().find(scope.storageKey))
            write.payload?.let { payload ->
                database.snapshotDao().upsert(SnapshotEntity(scope.storageKey, scope.accountId.value, payload))
            }
            write.events
        }

    override suspend fun deleteAccount(accountId: LocalAccountId) {
        database.snapshotDao().deleteAccount(accountId.value)
    }
}
