package com.firkat.fillin.feature.budgetmanagement.data

import androidx.room.*
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
)
@Dao
interface BudgetManagementDao {
    @Query("SELECT * FROM budget_management ORDER BY createdAt, requestId")
    suspend fun all(): List<BudgetManagementEntry>
    @Query("SELECT * FROM budget_management ORDER BY createdAt, requestId")
    fun observe(): Flow<List<BudgetManagementEntry>>
    @Insert suspend fun insert(entry: BudgetManagementEntry)
    @Update suspend fun update(entry: BudgetManagementEntry)
    @Query("DELETE FROM budget_management WHERE requestId=:id") suspend fun remove(id: String)
    @Query("DELETE FROM budget_management WHERE profileId=:id") suspend fun removeProfile(id: String)
}
