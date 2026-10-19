package com.firkat.fillin.feature.dashboard.widgets.totalbalance.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firkat.fillin.feature.dashboard.domain.model.Money
import com.firkat.fillin.feature.dashboard.widgets.totalbalance.domain.ObserveTotalBalance
import kotlinx.coroutines.flow.*

data class TotalBalanceUiState(val amount: Money? = null)

class TotalBalanceViewModel(observe: ObserveTotalBalance) : ViewModel() {
    val uiState = observe().map { TotalBalanceUiState(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(0, 0), TotalBalanceUiState())
}
