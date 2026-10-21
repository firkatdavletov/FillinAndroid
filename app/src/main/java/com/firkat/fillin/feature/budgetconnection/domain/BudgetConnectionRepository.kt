package com.firkat.fillin.feature.budgetconnection.domain

data class AvailableBudget(val id: String, val name: String, val memberId: String, val role: String)

interface BudgetConnectionRepository {
    suspend fun budgets(uid: String): List<AvailableBudget>
    suspend fun connect(uid: String, budget: AvailableBudget)
}
