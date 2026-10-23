package com.firkat.fillin.feature.budgetconnection.data

import androidx.room.*
import com.firkat.fillin.feature.budgetconnection.domain.*
import com.firkat.fillin.feature.budgetprofile.data.BudgetProfileDatabase

@Entity(tableName = "cloud_budget_catalog", primaryKeys = ["firebaseUid", "budgetId"])
data class CachedCloudBudget(
    val firebaseUid: String,
    val budgetId: String,
    val name: String,
    val memberId: String,
    val role: String,
)

@Dao
interface BudgetCatalogDao {
    @Query("SELECT * FROM cloud_budget_catalog WHERE firebaseUid = :uid ORDER BY name, budgetId")
    suspend fun budgets(uid: String): List<CachedCloudBudget>

    @Query("DELETE FROM cloud_budget_catalog WHERE firebaseUid = :uid")
    suspend fun clear(uid: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun remember(budget: CachedCloudBudget)
}

class RoomBudgetCatalogStore(private val database: BudgetProfileDatabase) : BudgetCatalogStore {
    private val dao get() = database.catalog()
    override suspend fun budgets(uid: String) = dao.budgets(uid).map { AvailableBudget(it.budgetId, it.name, it.memberId, it.role) }
    override suspend fun replace(uid: String, budgets: List<AvailableBudget>) = database.withTransaction {
        dao.clear(uid)
        budgets.forEach { remember(uid, it) }
    }
    override suspend fun remember(uid: String, budget: AvailableBudget) = dao.remember(
        CachedCloudBudget(uid, budget.id, budget.name, budget.memberId, budget.role),
    )
}
