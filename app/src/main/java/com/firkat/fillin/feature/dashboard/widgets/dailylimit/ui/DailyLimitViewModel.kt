package com.firkat.fillin.feature.dashboard.widgets.dailylimit.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firkat.fillin.feature.dashboard.domain.model.Money
import com.firkat.fillin.feature.dashboard.widgets.dailylimit.domain.DailyLimitSummary
import com.firkat.fillin.feature.dashboard.widgets.dailylimit.domain.ObserveDailyLimit
import java.math.BigDecimal
import java.math.RoundingMode
import kotlinx.coroutines.flow.*

data class DailyLimitUiState(
    val amount: Money? = null,
    val spentToday: Money? = null,
    val remainingToday: Money? = null,
    val spentPercent: BigDecimal? = null,
    val remainingPercent: BigDecimal? = null,
    val progress: Float = 0f,
    val isOverspent: Boolean = false,
) {
    companion object {
        fun from(summary: DailyLimitSummary) = DailyLimitUiState(
            amount = summary.limit,
            spentToday = summary.spentToday,
            remainingToday = summary.remainingToday.copy(amount = summary.remainingToday.amount.abs()),
            spentPercent = summary.spentPercent,
            remainingPercent = summary.remainingPercent?.abs(),
            progress = when {
                summary.limit.amount.signum() > 0 -> summary.spentToday.amount.min(summary.limit.amount)
                    .divide(summary.limit.amount, 6, RoundingMode.HALF_UP).toFloat()
                summary.spentToday.amount.signum() > 0 -> 1f
                else -> 0f
            },
            isOverspent = summary.remainingToday.amount.signum() < 0,
        )
    }
}

class DailyLimitViewModel(observe: ObserveDailyLimit) : ViewModel() {
    val uiState = observe().map { it?.let(DailyLimitUiState::from) ?: DailyLimitUiState() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(0, 0), DailyLimitUiState())
}
