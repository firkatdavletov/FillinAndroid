package com.firkat.fillin.feature.dashboard.widgets.availabletospend.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firkat.fillin.feature.dashboard.domain.model.Money
import com.firkat.fillin.feature.dashboard.widgets.availabletospend.domain.ObserveAvailableToSpend
import kotlinx.coroutines.flow.*

data class AvailableToSpendUiState(val amount: Money? = null)

class AvailableToSpendViewModel(observe: ObserveAvailableToSpend) : ViewModel() {
    val uiState = observe().map { AvailableToSpendUiState(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(0, 0), AvailableToSpendUiState())
}
