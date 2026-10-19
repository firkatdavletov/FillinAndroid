package com.firkat.fillin.feature.dashboard.widgets.monthlybudget.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firkat.fillin.feature.dashboard.domain.model.MonthBudget
import com.firkat.fillin.feature.dashboard.widgets.monthlybudget.domain.ObserveMonthlyBudget
import java.math.BigDecimal
import java.math.RoundingMode
import kotlinx.coroutines.flow.*

data class MonthlyBudgetUiState(val budget: MonthBudget? = null) {
    val usedPercent: BigDecimal? get() = budget?.takeIf { it.planned.amount.signum() > 0 }?.let {
        it.spent.amount.multiply(BigDecimal(100)).divide(it.planned.amount, 1, RoundingMode.HALF_UP)
    }
    val progress: Float get() = usedPercent?.divide(BigDecimal(100))?.toFloat()?.coerceIn(0f, 1f) ?: 0f
}

class MonthlyBudgetViewModel(observe: ObserveMonthlyBudget) : ViewModel() {
    val uiState = observe().map { MonthlyBudgetUiState(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(0, 0), MonthlyBudgetUiState())
}
