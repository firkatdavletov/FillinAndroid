package com.firkat.fillin.feature.dashboard.widgets.balanceforecast.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firkat.fillin.feature.dashboard.domain.model.Money
import com.firkat.fillin.feature.dashboard.widgets.balanceforecast.domain.ObserveBalanceForecast
import kotlinx.coroutines.flow.*

data class BalanceForecastUiState(val amount: Money? = null, val insufficientHistory: Boolean = false)

class BalanceForecastViewModel(observe: ObserveBalanceForecast) : ViewModel() {
    val uiState = observe().map { BalanceForecastUiState(it.amount, it.insufficientHistory) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(0, 0), BalanceForecastUiState())
}
