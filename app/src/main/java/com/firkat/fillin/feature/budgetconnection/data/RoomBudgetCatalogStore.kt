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
    @ColumnInfo(defaultValue = "'KZT'") val currency: String = "KZT",
    @ColumnInfo(defaultValue = "0") val currencyVersion: Long = 0
)

@Dao
interface BudgetCatalogDao {
    @Query("SELECT * FROM cloud_budget_catalog")
    fun observe(): kotlinx.coroutines.flow.Flow<List<CachedCloudBudget>>

    @Query("SELECT * FROM cloud_budget_catalog WHERE firebaseUid = :uid ORDER BY name, budgetId")
    suspend fun budgets(
        uid: String
    ): List<CachedCloudBudget>

    @Query("DELETE FROM cloud_budget_catalog WHERE firebaseUid = :uid AND budgetId = :id")
    suspend fun remove(
        uid: String,
        id: String
    )

    @Query("DELETE FROM cloud_budget_catalog WHERE firebaseUid = :uid")
    suspend fun clear(
        uid: String
    )

    @Query("SELECT * FROM cloud_budget_catalog WHERE firebaseUid = :uid AND budgetId = :budgetId")
    suspend fun budget(
        uid: String,
        budgetId: String
    ): CachedCloudBudget?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(
        budget: CachedCloudBudget
    )

    @Transaction
    suspend fun remember(
        budget: CachedCloudBudget
    ) {
        val previous = budget(budget.firebaseUid, budget.budgetId)
        put(
            if (previous != null && previous.currencyVersion > budget.currencyVersion) {
                budget.copy(currency = previous.currency, currencyVersion = previous.currencyVersion)
            } else {
                budget
            }
        )
    }
}

class RoomBudgetCatalogStore(private val database: BudgetProfileDatabase) : BudgetCatalogStore {
    private val dao get() = database.catalog()
    override suspend fun budgets(
        uid: String
    ): List<AvailableBudget> {
        val pending = database.management().all().filter { it.uid == uid }
        return dao.budgets(uid).mapNotNull { row ->
            val changes = pending.filter { it.error != "access_denied" && it.budgetId == row.budgetId }
            if (changes.any { it.deleting }) {
                null
            } else {
                AvailableBudget(
                    row.budgetId,
                    changes.lastOrNull { it.rename }?.name ?: row.name,
                    row.memberId,
                    row.role,
                    changes.asReversed().firstNotNullOfOrNull { it.currency } ?: row.currency,
                    row.currencyVersion
                )
            }
        }
    }
    override suspend fun replace(
        uid: String,
        budgets: List<AvailableBudget>
    ) = database.withTransaction {
        val previous = dao.budgets(uid).associateBy { it.budgetId }
        val received = budgets.mapTo(hashSetOf()) { it.id }
        previous.keys.filterNot(received::contains).forEach { dao.remove(uid, it) }
        budgets.forEach { remember(uid, it) }
    }
    override suspend fun remember(
        uid: String,
        budget: AvailableBudget
    ) = dao.remember(
        CachedCloudBudget(
            uid,
            budget.id,
            budget.name,
            budget.memberId,
            budget.role,
            budget.currency,
            budget.currencyVersion
        )
    )
}
