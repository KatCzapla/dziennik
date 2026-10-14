package pl.usagi.edziennik.persistence.changes

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.withTransaction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import pl.usagi.edziennik.background.SessionPreparation
import pl.usagi.edziennik.background.SyncRunCounters
import pl.usagi.edziennik.background.SyncRunResult
import pl.usagi.edziennik.background.SyncRunStatus
import java.time.Clock
import javax.inject.Inject

@Entity(tableName = "sync_diagnostics", indices = [Index("accountId")])
data class SyncDiagnosticEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: String,
    val startedAt: Long,
    val source: String,
    val finishedAt: Long? = null,
    val result: String? = null,
    val reason: String? = null,
    val detectedEvents: Int? = null,
    val handedOffNotifications: Int = 0,
)

@Dao
interface SyncDiagnosticsDao {
    @Insert suspend fun insert(entry: SyncDiagnosticEntry): Long

    @Query("SELECT * FROM sync_diagnostics WHERE accountId = :accountId ORDER BY id DESC LIMIT 20")
    suspend fun history(accountId: String): List<SyncDiagnosticEntry>

    @Query("DELETE FROM sync_diagnostics WHERE accountId = :accountId AND id NOT IN (SELECT id FROM sync_diagnostics WHERE accountId = :accountId ORDER BY id DESC LIMIT 20)")
    suspend fun trim(accountId: String)

    @Query("UPDATE sync_diagnostics SET finishedAt = :finishedAt, result = :result, reason = :reason, detectedEvents = :detected, handedOffNotifications = :handedOff WHERE id = :id AND accountId = :accountId")
    suspend fun finish(id: Long, accountId: String, finishedAt: Long, result: String, reason: String, detected: Int?, handedOff: Int)

    @Query("SELECT COUNT(*) FROM notification_outbox WHERE accountId = :accountId AND handedOffAt IS NULL")
    suspend fun pendingCount(accountId: String): Int

    @Query("DELETE FROM sync_diagnostics WHERE accountId = :accountId")
    suspend fun deleteAccount(accountId: String)
}

fun SyncRunResult.diagnosticResult(): String = when {
    retryable -> "RETRY"
    status == SyncRunStatus.COMPLETE -> "SUCCESS"
    status == SyncRunStatus.PARTIAL -> "PARTIAL"
    else -> "FAILURE"
}

private fun safeReason(reason: String): String = reason.takeIf {
    it in SessionPreparation.entries.map { entry -> entry.name } ||
        it in SyncFailureCause.entries.map { entry -> entry.name } ||
        it in setOf("NONE", "ALL_AREAS_FAILED", "INCOMPLETE_AREAS", "CANCELLED")
} ?: "UNKNOWN_FAILURE"

class SyncDiagnostics @Inject constructor(
    private val database: SnapshotDatabase,
    private val identity: LocalSnapshotIdentity,
    private val clock: Clock,
) {
    suspend fun history(): List<SyncDiagnosticEntry> {
        val account = identity.currentAccountId() ?: return emptyList()
        val history = database.syncDiagnosticsDao().history(account.value)
        return if (identity.currentAccountId() == account) history else emptyList()
    }

    suspend fun pendingCount(): Int {
        val account = identity.currentAccountId() ?: return 0
        val count = database.syncDiagnosticsDao().pendingCount(account.value)
        return if (identity.currentAccountId() == account) count else 0
    }

    suspend fun recordRun(manual: Boolean, block: suspend (SyncRunCounters) -> SyncRunResult): SyncRunResult {
        val account = identity.currentAccountId()
        val counters = SyncRunCounters()
        var rowId: Long? = null
        safely {
            if (account != null && identity.currentAccountId() == account) {
                rowId = database.withTransaction {
                    val id = database.syncDiagnosticsDao().insert(SyncDiagnosticEntry(
                        accountId = account.value, startedAt = clock.millis(),
                        source = if (manual) "MANUAL" else "PERIODIC",
                    ))
                    database.syncDiagnosticsDao().trim(account.value)
                    id
                }
            }
        }
        suspend fun finish(result: String, reason: String) = safely {
            if (account != null && rowId != null && identity.currentAccountId() == account) {
                database.syncDiagnosticsDao().finish(rowId!!, account.value, clock.millis(),
                    result, safeReason(reason), counters.detectedEvents, counters.handedOffNotifications)
            }
        }
        return try {
            val result = block(counters)
            finish(result.diagnosticResult(), result.reason)
            result
        } catch (cancelled: CancellationException) {
            finish("FAILURE", "CANCELLED")
            throw cancelled
        } catch (failure: Exception) {
            val cause = failure.syncFailureCause()
            finish(if (cause.retryable) "RETRY" else "FAILURE", cause.name)
            throw failure
        }
    }

    // Diagnostics must not change the worker result, even when storage is unavailable.
    private suspend fun safely(block: suspend () -> Unit) = withContext(NonCancellable) {
        try { block() } catch (_: Exception) { }
    }
}
