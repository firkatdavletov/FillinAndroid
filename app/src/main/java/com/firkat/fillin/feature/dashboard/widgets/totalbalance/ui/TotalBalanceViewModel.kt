package com.firkat.fillin.feature.dashboard.widgets.totalbalance.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firkat.fillin.feature.dashboard.domain.model.DashboardStatus
import com.firkat.fillin.feature.dashboard.domain.model.Money
import com.firkat.fillin.feature.dashboard.widgets.totalbalance.domain.ObserveTotalBalance
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class TotalBalanceUiState(
    val amount: Money? = null,
    val dataStatus: DashboardStatus = DashboardStatus.WaitingForBudget
)

class TotalBalanceViewModel(observe: ObserveTotalBalance) : ViewModel() {
    val uiState = observe.states().map { TotalBalanceUiState(it.value, it.status) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(0, 0), TotalBalanceUiState())
}
