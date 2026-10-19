package com.firkat.fillin.core.database

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.firkat.fillin.feature.operationhistory.data.local.LocalCategory
import com.firkat.fillin.feature.operationhistory.data.local.LocalOperation
import com.firkat.fillin.feature.operationhistory.data.local.OperationSyncState
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "budget_sync_records", primaryKeys = ["scopeKey", "kind", "id"])
data class LocalSyncRecord(
    val scopeKey: String,
    val kind: String,
    val id: String,
    val payload: String,
    val revision: Long,
)

@Entity(tableName = "budget_outbox", indices = [Index("scopeKey")])
data class BudgetOutbox(
    @PrimaryKey val requestId: String,
    val scopeKey: String,
    val entityId: String,
    val kind: String,
    val intent: String,
    val createdOrder: Long,
    val predecessor: String? = null,
    val dependency: String? = null,
    val wirePayload: String? = null,
    val status: String = "PENDING",
    val errorCode: String? = null,
    val receiptRevision: Long? = null,
    val receiptToken: Long? = null,
)

@Entity(tableName = "sync_staging_pages")
data class SyncStagingPage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val payload: String,
)

data class BudgetContent(
    @Embedded val sync: OperationSyncState,
    @Relation(parentColumn = "scopeKey", entityColumn = "scopeKey") val categories: List<LocalCategory>,
    @Relation(parentColumn = "scopeKey", entityColumn = "scopeKey") val operations: List<LocalOperation>,
    @Relation(parentColumn = "scopeKey", entityColumn = "scopeKey") val records: List<LocalSyncRecord>,
    @Relation(parentColumn = "scopeKey", entityColumn = "scopeKey") val commands: List<BudgetOutbox>,
)

@Dao
interface BudgetDao {
    @Transaction
    @Query("SELECT * FROM operation_sync_state WHERE id = 1")
    fun observeContent(): Flow<BudgetContent?>

    @Transaction
    @Query("SELECT * FROM operation_sync_state WHERE id = 1")
    suspend fun content(): BudgetContent?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putRecord(record: LocalSyncRecord)

    @Query("DELETE FROM budget_sync_records WHERE scopeKey = :scope AND kind = :kind AND id = :id")
    suspend fun deleteRecord(
        scope: String,
        kind: String,
        id: String,
    )

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun enqueue(command: BudgetOutbox)

    @Update suspend fun update(command: BudgetOutbox)

    @Query("SELECT * FROM budget_outbox WHERE scopeKey = :scope ORDER BY createdOrder, requestId")
    suspend fun commands(scope: String): List<BudgetOutbox>

    @Query("SELECT * FROM budget_outbox WHERE requestId = :id")
    suspend fun command(id: String): BudgetOutbox?

    @Insert suspend fun stage(page: SyncStagingPage)

    @Query("SELECT * FROM sync_staging_pages ORDER BY id")
    suspend fun staged(): List<SyncStagingPage>

    @Query("DELETE FROM sync_staging_pages")
    suspend fun clearStaged()

    @Query(
        "DELETE FROM budget_outbox WHERE scopeKey = :scope AND status = 'ACKNOWLEDGED' AND receiptRevision <= :revision AND NOT EXISTS (SELECT 1 FROM budget_outbox child WHERE child.predecessor = budget_outbox.requestId OR child.dependency = budget_outbox.requestId)",
    )
    suspend fun cleanReceipts(
        scope: String,
        revision: Long,
    )
}

val BUDGET_MIGRATION_1_2 =
    object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE local_categories ADD COLUMN dictionaryKey TEXT")
            db.execSQL("ALTER TABLE operation_sync_state ADD COLUMN role TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE operation_sync_state ADD COLUMN protocolVersion INTEGER NOT NULL DEFAULT 2")
            db.execSQL("UPDATE operation_sync_state SET cursor = NULL, lastRevision = 0, initialSyncComplete = 0")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS budget_sync_records (scopeKey TEXT NOT NULL, kind TEXT NOT NULL, id TEXT NOT NULL, payload TEXT NOT NULL, revision INTEGER NOT NULL, PRIMARY KEY(scopeKey, kind, id))",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS budget_outbox (requestId TEXT NOT NULL PRIMARY KEY, scopeKey TEXT NOT NULL, entityId TEXT NOT NULL, kind TEXT NOT NULL, intent TEXT NOT NULL, createdOrder INTEGER NOT NULL, predecessor TEXT, dependency TEXT, wirePayload TEXT, status TEXT NOT NULL, errorCode TEXT, receiptRevision INTEGER, receiptToken INTEGER)",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_budget_outbox_scopeKey ON budget_outbox(scopeKey)")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS sync_staging_pages (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, payload TEXT NOT NULL)",
            )
        }
    }
