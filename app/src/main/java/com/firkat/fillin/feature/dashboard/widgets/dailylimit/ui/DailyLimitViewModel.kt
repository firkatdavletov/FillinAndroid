package com.firkat.fillin.feature.dashboard.widgets.dailylimit.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firkat.fillin.feature.dashboard.domain.model.Money
import com.firkat.fillin.feature.dashboard.widgets.dailylimit.domain.DailyLimitSummary
import com.firkat.fillin.feature.dashboard.widgets.dailylimit.domain.ObserveDailyLimit
import java.math.BigDecimal
import java.math.RoundingMode
import kotlinx.coroutines.flow.*

enum class DailyLimitCardStatus { Normal, Overspent, Deficit }

data class DailyLimitUiState(
    val forecast: com.firkat.fillin.feature.dailylimit.domain.DailyLimitResult? = null,
    val amount: Money? = null,
    val spentToday: Money? = null,
    val remainingToday: Money? = null,
    val spentPercent: BigDecimal? = null,
    val remainingPercent: BigDecimal? = null,
    val progress: Float = 0f,
    val isOverspent: Boolean = false,
) {
    val status: DailyLimitCardStatus get() = when {
        forecast?.status == com.firkat.fillin.feature.dailylimit.domain.ForecastStatus.DEFICIT -> DailyLimitCardStatus.Deficit
        isOverspent -> DailyLimitCardStatus.Overspent
        else -> DailyLimitCardStatus.Normal
    }
    val showProgress: Boolean get() = amount?.amount?.signum() == 1
    val showRemaining: Boolean get() = showProgress && status != DailyLimitCardStatus.Deficit

    companion object {
        fun from(summary: DailyLimitSummary) = DailyLimitUiState(
            forecast = summary.forecast,
            amount = summary.limit,
            spentToday = summary.spentToday,
            remainingToday = summary.remainingToday.copy(amount = summary.remainingToday.amount.max(BigDecimal.ZERO)),
            spentPercent = summary.spentPercent,
            remainingPercent = summary.remainingPercent?.max(BigDecimal.ZERO),
            progress = when {
                summary.limit.amount.signum() > 0 -> summary.spentToday.amount.min(summary.limit.amount)
                    .divide(summary.limit.amount, 6, RoundingMode.HALF_UP).toFloat().coerceIn(0f, 1f)
                else -> 0f
            },
            isOverspent = summary.spentToday.amount > summary.limit.amount,
        )
    }
}

class DailyLimitViewModel(observe: ObserveDailyLimit) : ViewModel() {
    val uiState = observe().map { it?.let(DailyLimitUiState::from) ?: DailyLimitUiState() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(0, 0), DailyLimitUiState())
}
