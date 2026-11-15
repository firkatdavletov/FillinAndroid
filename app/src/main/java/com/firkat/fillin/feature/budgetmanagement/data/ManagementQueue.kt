package com.firkat.fillin.feature.budgetmanagement.data

import androidx.room.*
import com.firkat.fillin.feature.sync.data.BudgetCurrencyOverride
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "budget_management")
data class BudgetManagementEntry(
    @PrimaryKey val requestId: String,
    val profileId: String,
    val uid: String?,
    val budgetId: String?,
    val name: String,
    val deleting: Boolean,
    val createdAt: Long,
    val error: String? = null,
    val currency: String? = null,
    @ColumnInfo(defaultValue = "1") val rename: Boolean = true
)

@Dao
interface BudgetManagementDao {
    @Query("SELECT * FROM budget_management ORDER BY createdAt, requestId")
    suspend fun all(): List<BudgetManagementEntry>

    @Query("SELECT * FROM budget_management ORDER BY createdAt, requestId")
    fun observe(): Flow<List<BudgetManagementEntry>>

    @Query(
        """
        SELECT (
            SELECT currency FROM budget_management
            WHERE uid = :uid AND budgetId = :budgetId AND currency IS NOT NULL AND deleting = 0
                AND (error IS NULL OR error != 'access_denied')
            ORDER BY createdAt DESC, requestId DESC LIMIT 1
        ) AS pending, (
            SELECT currency FROM cloud_budget_catalog WHERE firebaseUid = :uid AND budgetId = :budgetId
        ) AS confirmed, COALESCE((
            SELECT currencyVersion FROM cloud_budget_catalog WHERE firebaseUid = :uid AND budgetId = :budgetId
        ), 0) AS version
    """
    )
    fun observeCurrency(
        uid: String,
        budgetId: String
    ): Flow<BudgetCurrencyOverride>

    @Insert suspend fun insert(
        entry: BudgetManagementEntry
    )

    @Update suspend fun update(
        entry: BudgetManagementEntry
    )

    @Query("DELETE FROM budget_management WHERE requestId=:id")
    suspend fun remove(
        id: String
    )

    @Query("DELETE FROM budget_management WHERE profileId=:id")
    suspend fun removeProfile(
        id: String
    )
}
