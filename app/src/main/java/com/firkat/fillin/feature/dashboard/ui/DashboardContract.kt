package com.firkat.fillin.feature.dashboard.ui

import com.firkat.fillin.feature.dashboard.domain.model.DashboardStatus
import com.firkat.fillin.feature.plannedexpenses.domain.model.PlannedExpense
import com.firkat.fillin.feature.plannedincomes.domain.model.PlannedIncome
import java.time.LocalDate

internal const val DASHBOARD_PLANNED_EXPENSE_LIMIT = 3

data class DashboardUiState(
    val budgetName: String? = null,
    val date: LocalDate? = null,
    val status: DashboardStatus = DashboardStatus.WaitingForBudget,
    val isRefreshing: Boolean = false
)

internal data class PlannedOperationsUiState(
    val expenses: List<PlannedExpense> = emptyList(),
    val income: PlannedIncome? = null,
    val expensesPending: Boolean = true,
    val incomePending: Boolean = true
) {
    val isLoading: Boolean get() = expensesPending || incomePending
    val isVisible: Boolean get() = isLoading || expenses.isNotEmpty() || income != null
}

sealed interface DashboardEvent {
    data object Resume : DashboardEvent
    data object Retry : DashboardEvent
    data object Refresh : DashboardEvent
}
