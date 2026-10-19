package com.firkat.fillin.feature.operationhistory.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.RoomDatabase
import androidx.room.Transaction
import com.firkat.fillin.core.database.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "local_operations", indices = [Index("scopeKey"), Index("requestId", unique = true)])
data class LocalOperation(
    @PrimaryKey val id: String,
    val scopeKey: String,
    val categoryId: String,
    val type: String,
    val originalAmount: String,
    val originalCurrency: String,
    val amountKzt: String,
    val occurredOn: String,
    val createdAt: String,
    val status: String,
    val version: Long,
    val sequence: Long,
    val description: String?,
    val refundOf: String?,
    val requestId: String?,
    val syncStatus: String = "SYNCED",
    val syncError: String? = null,
)

@Entity(tableName = "local_categories", primaryKeys = ["scopeKey", "id"])
data class LocalCategory(
    val scopeKey: String,
    val id: String,
    val name: String,
    val kind: String,
    val iconCode: String,
    val iconEmoji: String,
    val archived: Boolean,
    val revision: Long,
    val dictionaryKey: String? = null,
)

@Entity(tableName = "operation_outbox", indices = [Index("scopeKey")])
data class OperationOutbox(
    @PrimaryKey val requestId: String,
    val scopeKey: String,
    val operationId: String,
    val payload: String,
    val errorCode: String? = null,
)

@Entity(tableName = "operation_sync_state")
data class OperationSyncState(
    @PrimaryKey val id: Int = 1,
    val scopeKey: String = "",
    val householdId: String = "",
    val memberId: String = "",
    val accountingStartedAt: String? = null,
    val cursor: String? = null,
    val lastRevision: Long = 0,
    val initialSyncComplete: Boolean = false,
    val isSyncing: Boolean = false,
    val lastSyncedAt: String? = null,
    val errorCode: String? = null,
    @ColumnInfo(defaultValue = "''") val role: String = "",
    @ColumnInfo(defaultValue = "2") val protocolVersion: Int = 2,
)

data class LocalHistoryContent(
    @Embedded val sync: OperationSyncState,
    @Relation(parentColumn = "scopeKey", entityColumn = "scopeKey") val operations: List<LocalOperation>,
    @Relation(parentColumn = "scopeKey", entityColumn = "scopeKey") val categories: List<LocalCategory>,
)

@Dao
interface OperationDao {
    @Transaction
    @Query("SELECT * FROM operation_sync_state WHERE id = 1")
    fun observeContent(): Flow<LocalHistoryContent?>

    @Query("SELECT * FROM operation_sync_state WHERE id = 1")
    suspend fun state(): OperationSyncState?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putState(state: OperationSyncState)

    @Query("SELECT * FROM local_operations WHERE id = :id")
    suspend fun operation(id: String): LocalOperation?

    @Query("SELECT * FROM local_operations WHERE requestId = :requestId")
    suspend fun byRequest(requestId: String): LocalOperation?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putOperation(operation: LocalOperation)

    @Query("DELETE FROM local_operations WHERE id = :id")
    suspend fun deleteOperation(id: String)

    @Query("SELECT * FROM local_categories WHERE scopeKey = :scopeKey AND id = :id")
    suspend fun category(
        scopeKey: String,
        id: String,
    ): LocalCategory?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putCategory(category: LocalCategory)

    @Query("DELETE FROM local_categories WHERE scopeKey = :scopeKey AND id = :id")
    suspend fun deleteCategory(
        scopeKey: String,
        id: String,
    )

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun enqueue(command: OperationOutbox)

    @Query("SELECT * FROM operation_outbox WHERE scopeKey = :scopeKey AND errorCode IS NULL ORDER BY rowid")
    suspend fun pending(scopeKey: String): List<OperationOutbox>

    @Query("UPDATE operation_outbox SET errorCode = :error WHERE requestId = :requestId")
    suspend fun commandError(
        requestId: String,
        error: String,
    )

    @Query("DELETE FROM operation_outbox WHERE requestId = :requestId")
    suspend fun complete(requestId: String)
}

@Database(
    entities = [
        LocalOperation::class, LocalCategory::class, OperationOutbox::class, OperationSyncState::class,
        LocalSyncRecord::class, BudgetOutbox::class, SyncStagingPage::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class OperationDatabase : RoomDatabase() {
    abstract fun operations(): OperationDao

    abstract fun budget(): BudgetDao
}
