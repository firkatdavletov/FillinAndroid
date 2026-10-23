package com.firkat.fillin.feature.budgetconnection.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

data class AvailableBudget(val id: String, val name: String, val memberId: String, val role: String)

interface BudgetConnectionRepository {
    val activeBudgetId: Flow<String?> get() = flowOf(null)
    suspend fun cachedBudgets(uid: String): List<AvailableBudget> = emptyList()
    suspend fun budgets(uid: String): List<AvailableBudget>
    suspend fun connect(uid: String, budget: AvailableBudget)
}

interface BudgetCatalogStore {
    suspend fun budgets(uid: String): List<AvailableBudget>
    suspend fun replace(uid: String, budgets: List<AvailableBudget>)
    suspend fun remember(uid: String, budget: AvailableBudget)
}
