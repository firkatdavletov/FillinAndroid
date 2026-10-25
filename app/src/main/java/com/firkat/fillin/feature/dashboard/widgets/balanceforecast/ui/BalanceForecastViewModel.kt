package com.firkat.fillin.feature.dashboard.widgets.balanceforecast.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firkat.fillin.feature.dashboard.domain.model.BalanceForecastProjection
import com.firkat.fillin.feature.dashboard.domain.model.ForecastPoint
import com.firkat.fillin.feature.dashboard.widgets.balanceforecast.domain.ObserveBalanceForecast
import java.time.LocalDate
import kotlinx.coroutines.flow.*

data class BalanceForecastUiState(
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val points: List<ForecastPoint> = emptyList(),
    val insufficientHistory: Boolean = false,
) {
    val minimum: ForecastPoint? get() = points.minByOrNull { it.balance.amount }
    val maximum: ForecastPoint? get() = points.maxByOrNull { it.balance.amount }

    companion object {
        fun from(projection: BalanceForecastProjection) = BalanceForecastUiState(
            projection.startDate, projection.endDate, projection.points, projection.insufficientHistory,
        )
    }
}

class BalanceForecastViewModel(observe: ObserveBalanceForecast) : ViewModel() {
    val uiState = observe().map { it?.let(BalanceForecastUiState::from) ?: BalanceForecastUiState() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(0, 0), BalanceForecastUiState())
}
