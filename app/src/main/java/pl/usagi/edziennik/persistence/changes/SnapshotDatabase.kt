package pl.usagi.edziennik.persistence.changes

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import androidx.room.Insert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Entity(tableName = "snapshots", indices = [Index("accountId")])
data class SnapshotEntity(
    @PrimaryKey val scopeKey: String,
    val accountId: String,
    val payload: String,
)

@Dao
interface SnapshotDao {
    @Query("SELECT payload FROM snapshots WHERE scopeKey = :scopeKey")
    suspend fun find(scopeKey: String): String?

    @Upsert
    suspend fun upsert(snapshot: SnapshotEntity)

    @Query("DELETE FROM snapshots WHERE accountId = :accountId")
    suspend fun deleteAccount(accountId: String)
}

@Entity(tableName = "notification_outbox", indices = [Index("accountId")])
data class OutboxEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val accountId: String,
    val payload: String,
    val createdAt: Long,
    val handedOffAt: Long? = null,
)

@Dao
interface OutboxDao {
    @Insert
    suspend fun insert(events: List<OutboxEntity>)

    @Query("SELECT * FROM notification_outbox WHERE accountId = :accountId AND handedOffAt IS NULL ORDER BY id")
    suspend fun pending(accountId: String): List<OutboxEntity>

    @Query("UPDATE notification_outbox SET handedOffAt = :time WHERE id = :id AND accountId = :accountId")
    suspend fun markHandedOff(id: Int, accountId: String, time: Long)

    @Query("DELETE FROM notification_outbox WHERE handedOffAt IS NOT NULL")
    suspend fun pruneHandedOff()

    @Query("SELECT COUNT(*) FROM notification_outbox")
    suspend fun count(): Int

    @Query("SELECT COALESCE(SUM(length(CAST(payload AS BLOB))), 0) FROM notification_outbox")
    suspend fun payloadBytes(): Long

    @Query("DELETE FROM notification_outbox WHERE accountId = :accountId")
    suspend fun deleteAccount(accountId: String)
}

val SNAPSHOT_MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS notification_outbox (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, accountId TEXT NOT NULL, payload TEXT NOT NULL, createdAt INTEGER NOT NULL, handedOffAt INTEGER)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_notification_outbox_accountId ON notification_outbox (accountId)")
    }
}

val SNAPSHOT_MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS sync_diagnostics (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, accountId TEXT NOT NULL, startedAt INTEGER NOT NULL, source TEXT NOT NULL, finishedAt INTEGER, result TEXT, reason TEXT, detectedEvents INTEGER, handedOffNotifications INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_diagnostics_accountId ON sync_diagnostics (accountId)")
    }
}

@Database(entities = [SnapshotEntity::class, OutboxEntity::class, SyncDiagnosticEntry::class], version = 3, exportSchema = false)
abstract class SnapshotDatabase : RoomDatabase() {
    abstract fun snapshotDao(): SnapshotDao
    abstract fun outboxDao(): OutboxDao
    abstract fun syncDiagnosticsDao(): SyncDiagnosticsDao
}
