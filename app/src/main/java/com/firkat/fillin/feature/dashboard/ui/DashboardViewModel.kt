package com.firkat.fillin.feature.dashboard.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firkat.fillin.feature.dashboard.domain.DashboardDataSource
import com.firkat.fillin.feature.plannedexpenses.domain.usecase.GetPlannedExpenses
import com.firkat.fillin.feature.plannedincomes.domain.usecase.GetPlannedIncomes
import com.firkat.fillin.feature.profile.domain.ProfileBudgetsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DashboardViewModel(
    budgets: ProfileBudgetsRepository,
    private val source: DashboardDataSource,
    private val getPlannedExpenses: GetPlannedExpenses,
    private val getPlannedIncomes: GetPlannedIncomes,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {
    val budgetState =
        budgets
            .observe()
            .map { budgets ->
                val current = budgets.firstOrNull { it.selected }
                current?.name
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(0, 0),
                initialValue = ""
            )

    val uiState =
        source.state
            .map {
                DashboardUiState(
                    date = it.dashboard?.date,
                    status = it.status,
                    isRefreshing = it.isRefreshing
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(0, 0),
                initialValue = DashboardUiState()
            )

    private val mutablePlannedOperations = MutableStateFlow(PlannedOperationsUiState())
    internal val plannedOperations = mutablePlannedOperations.asStateFlow()
    private var expensesLoad: Job? = null
    private var incomesLoad: Job? = null

    fun onEvent(
        event: DashboardEvent
    ) {
        when (event) {
            DashboardEvent.Refresh, DashboardEvent.Retry -> source.refresh()

            DashboardEvent.Resume -> {
                source.resume()
                loadPlannedOperations()
            }
        }
    }

    private fun loadPlannedOperations() {
        if (expensesLoad?.isActive != true) {
            expensesLoad = viewModelScope.launch {
                getPlannedExpenses.observe().flowOn(ioDispatcher)
                    .catch { mutablePlannedOperations.update { it.copy(expensesPending = false) } }
                    .collect { plans ->
                        val expenses = plans.items.take(DASHBOARD_PLANNED_EXPENSE_LIMIT)
                        mutablePlannedOperations.update { it.copy(expenses = expenses, expensesPending = false) }
                    }
            }
        }
        if (incomesLoad?.isActive != true) {
            incomesLoad = viewModelScope.launch {
                getPlannedIncomes.observe().flowOn(ioDispatcher)
                    .catch { mutablePlannedOperations.update { it.copy(incomePending = false) } }
                    .collect { plans ->
                        val income = plans.items.firstOrNull()
                        mutablePlannedOperations.update { it.copy(income = income, incomePending = false) }
                    }
            }
        }
    }
}
