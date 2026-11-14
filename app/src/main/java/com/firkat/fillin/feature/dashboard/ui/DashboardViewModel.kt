package com.firkat.fillin.feature.dashboard.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firkat.fillin.feature.dashboard.domain.DashboardDataSource
import com.firkat.fillin.feature.profile.domain.ProfileBudgetsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class DashboardViewModel(
    budgets: ProfileBudgetsRepository,
    private val source: DashboardDataSource,
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
                initialValue = "",
            )

    val uiState =
        source.state
            .map {
                DashboardUiState(
                    date = it.dashboard?.date,
                    status = it.status,
                    isRefreshing = it.isRefreshing,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(0, 0),
                initialValue = DashboardUiState(),
            )

    fun onEvent(
        event: DashboardEvent,
    ) {
        when (event) {
            DashboardEvent.Refresh, DashboardEvent.Retry -> source.refresh()
            DashboardEvent.Resume -> source.resume()
        }
    }
}
