package com.firkat.fillin.feature.dashboard.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firkat.fillin.feature.dashboard.domain.DashboardDataSource
import com.firkat.fillin.feature.plannedexpenses.domain.usecase.GetPlannedExpenses
import com.firkat.fillin.feature.plannedincomes.domain.usecase.GetPlannedIncomes
import com.firkat.fillin.feature.profile.domain.ProfileBudgetsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    private var plannedOperationsLoad: Job? = null

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
        if (plannedOperationsLoad?.isActive == true) return

        plannedOperationsLoad = viewModelScope.launch {
            launch {
                val expenses = loadOrNull { getPlannedExpenses().items.take(DASHBOARD_PLANNED_EXPENSE_LIMIT) }
                mutablePlannedOperations.update {
                    it.copy(expenses = expenses ?: it.expenses, expensesPending = false)
                }
            }
            launch {
                val result = loadOrNull { getPlannedIncomes() }
                mutablePlannedOperations.update {
                    val income = if (result == null) it.income else result.items.firstOrNull()
                    it.copy(income = income, incomePending = false)
                }
            }
        }
    }

    private suspend fun <T> loadOrNull(
        load: suspend () -> T
    ): T? = try {
        withContext(ioDispatcher) { load() }
    } catch (exception: CancellationException) {
        throw exception
    } catch (_: Exception) {
        // A failed read must not discard the last successful result or cancel the other read.
        null
    }
}
