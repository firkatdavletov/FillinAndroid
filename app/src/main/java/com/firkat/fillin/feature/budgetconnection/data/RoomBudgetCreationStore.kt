package com.firkat.fillin.feature.budgetconnection.data

import com.firkat.fillin.feature.budgetprofile.domain.BudgetCurrency

import androidx.room.*
import androidx.room.withTransaction
import com.firkat.fillin.feature.budgetconnection.domain.*
import com.firkat.fillin.feature.budgetprofile.data.BudgetProfileDatabase

@Entity(tableName = "pending_budget_creation")
data class PendingBudgetCreationEntity(
    @PrimaryKey val firebaseUid: String,
    val requestId: String,
    val name: String,
    val openingBalance: String,
    val accountingStartedOn: String,
    @ColumnInfo(defaultValue = "'KZT'") val currency: String = "KZT",
)

@Dao
interface BudgetCreationDao {
    @Query("SELECT * FROM pending_budget_creation WHERE firebaseUid = :uid")
    suspend fun pending(uid: String): PendingBudgetCreationEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(request: PendingBudgetCreationEntity)

    @Query("DELETE FROM pending_budget_creation WHERE firebaseUid = :uid AND requestId = :requestId")
    suspend fun complete(uid: String, requestId: String)
}

class RoomBudgetCreationStore(private val database: BudgetProfileDatabase) : BudgetCreationStore {
    override suspend fun pending(uid: String) = database.creation().pending(uid)?.domain()
    override suspend fun save(uid: String, request: PendingBudgetCreation): PendingBudgetCreation = database.withTransaction {
        val draft = request.draft
        database.creation().insert(PendingBudgetCreationEntity(uid, request.requestId, draft.name, draft.openingBalance, draft.accountingStartedOn, draft.currency.name))
        requireNotNull(pending(uid))
    }
    override suspend fun complete(uid: String, requestId: String) = database.creation().complete(uid, requestId)
    private fun PendingBudgetCreationEntity.domain() = PendingBudgetCreation(requestId, BudgetCreationDraft(name, openingBalance, accountingStartedOn, BudgetCurrency.valueOf(currency)))
}
