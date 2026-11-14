package com.firkat.fillin.feature.dashboard.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firkat.fillin.feature.dashboard.domain.DashboardDataSource
import com.firkat.fillin.feature.plannedexpenses.domain.model.PlannedExpense
import com.firkat.fillin.feature.plannedexpenses.domain.usecase.GetPlannedExpenses
import com.firkat.fillin.feature.plannedincomes.domain.model.PlannedIncome
import com.firkat.fillin.feature.plannedincomes.domain.usecase.GetPlannedIncomes
import com.firkat.fillin.feature.profile.domain.ProfileBudgetsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DashboardViewModel(
    budgets: ProfileBudgetsRepository,
    private val source: DashboardDataSource,
    private val getPlannedExpenses: GetPlannedExpenses,
    private val getPlannedIncomes: GetPlannedIncomes,
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

    private val mutablePlannedExpenses = MutableStateFlow<List<PlannedExpense>>(emptyList())
    val plannedExpenses: StateFlow<List<PlannedExpense>> = mutablePlannedExpenses

    private val mutablePlannedIncome = MutableStateFlow<PlannedIncome?>(null)
    val plannedIncome: StateFlow<PlannedIncome?> = mutablePlannedIncome

    fun onEvent(
        event: DashboardEvent
    ) {
        when (event) {
            DashboardEvent.Refresh, DashboardEvent.Retry -> source.refresh()

            DashboardEvent.Resume -> {
                source.resume()
                getPlannedExpenses()
                getPlannedIncomes()
            }
        }
    }

    private fun getPlannedExpenses() {
        viewModelScope.launch {
            val plannedExpenses = withContext(Dispatchers.IO) {
                getPlannedExpenses.invoke().items.take(3)
            }

            mutablePlannedExpenses.tryEmit(plannedExpenses)
        }
    }

    private fun getPlannedIncomes() {
        viewModelScope.launch {
            val income = withContext(Dispatchers.IO) {
                getPlannedIncomes.invoke().items.firstOrNull()
            }

            mutablePlannedIncome.tryEmit(income)
        }
    }
}
