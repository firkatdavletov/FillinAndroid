package com.firkat.fillin.feature.dashboard.widgets.availabletospend.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firkat.fillin.feature.dashboard.domain.model.DashboardStatus
import com.firkat.fillin.feature.dashboard.domain.model.Money
import com.firkat.fillin.feature.dashboard.widgets.availabletospend.domain.ObserveAvailableToSpend
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class AvailableToSpendUiState(
    val amount: Money? = null,
    val dataStatus: DashboardStatus = DashboardStatus.WaitingForBudget
)

class AvailableToSpendViewModel(observe: ObserveAvailableToSpend) : ViewModel() {
    val uiState = observe.states().map { AvailableToSpendUiState(it.value, it.status) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(0, 0), AvailableToSpendUiState())
}
