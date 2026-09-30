package pl.usagi.edziennik.persistence.changes

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert

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

@Database(entities = [SnapshotEntity::class], version = 1, exportSchema = false)
abstract class SnapshotDatabase : RoomDatabase() {
    abstract fun snapshotDao(): SnapshotDao
}
