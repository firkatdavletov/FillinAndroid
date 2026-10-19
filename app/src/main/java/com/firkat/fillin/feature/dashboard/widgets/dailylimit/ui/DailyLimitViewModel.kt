package com.firkat.fillin.feature.dashboard.widgets.dailylimit.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firkat.fillin.feature.dashboard.domain.model.Money
import com.firkat.fillin.feature.dashboard.widgets.dailylimit.domain.ObserveDailyLimit
import kotlinx.coroutines.flow.*

data class DailyLimitUiState(val amount: Money? = null)

class DailyLimitViewModel(observe: ObserveDailyLimit) : ViewModel() {
    val uiState = observe().map { DailyLimitUiState(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(0, 0), DailyLimitUiState())
}
