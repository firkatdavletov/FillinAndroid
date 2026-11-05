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
    @ColumnInfo(defaultValue = "2") val protocolVersion: Int = 2,
    @ColumnInfo(defaultValue = "'[]'") val dependencies: String = "[]",
)

@Entity(tableName = "operation_plan_proposals", primaryKeys = ["scopeKey", "operationId"])
data class OperationPlanProposal(val scopeKey: String, val operationId: String, val payload: String?, val decision: String)

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
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putProposal(proposal: OperationPlanProposal)

    @Query("SELECT * FROM operation_plan_proposals WHERE scopeKey = :scope AND operationId = :id")
    suspend fun proposal(scope: String, id: String): OperationPlanProposal?

    @Query("SELECT operationId FROM operation_plan_proposals WHERE scopeKey = (SELECT scopeKey FROM operation_sync_state WHERE id = 1) AND decision = 'PENDING' ORDER BY rowid LIMIT 1")
    fun observePendingProposal(): Flow<String?>

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

    @Query("SELECT * FROM budget_sync_records WHERE scopeKey = :scope AND kind = :kind AND id = :id")
    suspend fun record(
        scope: String,
        kind: String,
        id: String,
    ): LocalSyncRecord?

    @Insert suspend fun stage(page: SyncStagingPage)

    @Query("SELECT * FROM sync_staging_pages ORDER BY id")
    suspend fun staged(): List<SyncStagingPage>

    @Query("DELETE FROM sync_staging_pages")
    suspend fun clearStaged()

    @Query(
        "DELETE FROM budget_outbox WHERE scopeKey = :scope AND status = 'ACKNOWLEDGED' AND receiptRevision <= :revision AND NOT EXISTS (SELECT 1 FROM budget_outbox child WHERE child.predecessor = budget_outbox.requestId OR child.dependency = budget_outbox.requestId OR instr(child.dependencies, char(34) || budget_outbox.requestId || char(34)) > 0)",
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

val BUDGET_MIGRATION_2_3 =
    object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE local_operations ADD COLUMN occurredAt TEXT")
            db.execSQL("DELETE FROM sync_staging_pages")
            db.execSQL("UPDATE operation_sync_state SET cursor = NULL, lastRevision = 0, initialSyncComplete = 0, protocolVersion = 3, isSyncing = 0")
        }
    }

val BUDGET_MIGRATION_3_4 =
    object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE operation_sync_state ADD COLUMN localReady INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE operation_sync_state ADD COLUMN cloudEnabled INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE operation_sync_state ADD COLUMN cloudBound INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE operation_sync_state ADD COLUMN bootstrapPayload TEXT")
            db.execSQL("ALTER TABLE budget_outbox ADD COLUMN protocolVersion INTEGER NOT NULL DEFAULT 2")
            db.execSQL("UPDATE operation_sync_state SET localReady = initialSyncComplete, cloudEnabled = CASE WHEN scopeKey <> '' THEN 1 ELSE 0 END, cloudBound = CASE WHEN scopeKey <> '' THEN 1 ELSE 0 END, protocolVersion = 4, cursor = NULL, lastRevision = 0, isSyncing = 0")
            db.execSQL("DELETE FROM sync_staging_pages")
        }
    }

val BUDGET_MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE budget_outbox ADD COLUMN dependencies TEXT NOT NULL DEFAULT '[]'")
        db.execSQL("CREATE TABLE IF NOT EXISTS operation_plan_proposals (scopeKey TEXT NOT NULL, operationId TEXT NOT NULL, payload TEXT, decision TEXT NOT NULL, PRIMARY KEY(scopeKey, operationId))")
    }
}

val BUDGET_MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE operation_sync_state ADD COLUMN currency TEXT NOT NULL DEFAULT 'KZT'")
        db.execSQL("ALTER TABLE operation_sync_state ADD COLUMN currencyVersion INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE local_operations ADD COLUMN rubKztRate TEXT")
    }
}

internal val BUDGET_MIGRATIONS = arrayOf(
    BUDGET_MIGRATION_1_2,
    BUDGET_MIGRATION_2_3,
    BUDGET_MIGRATION_3_4,
    BUDGET_MIGRATION_4_5,
    BUDGET_MIGRATION_5_6,
)
