package com.firkat.fillin.feature.dashboard.widgets.balanceforecast.ui

import com.firkat.fillin.feature.dashboard.domain.model.Money
import com.firkat.fillin.feature.dashboard.domain.model.ForecastSeries
import com.firkat.fillin.feature.dailylimit.domain.ForecastWarning
import java.math.BigDecimal
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firkat.fillin.feature.dashboard.domain.model.BalanceForecastProjection
import com.firkat.fillin.feature.dashboard.domain.model.ForecastPoint
import com.firkat.fillin.feature.dashboard.widgets.balanceforecast.domain.ObserveBalanceForecast
import java.time.LocalDate
import kotlinx.coroutines.flow.*

data class ForecastUiState(
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val points: List<ForecastPoint> = emptyList(),
    val insufficientHistory: Boolean = false,
    val currentPacePoints: List<ForecastPoint>? = null,
    val reserve: Money = Money(BigDecimal.ZERO, "KZT"),
    val warnings: Set<ForecastWarning> = emptySet(),
    val dependsOnPlannedIncome: Boolean = false,
) {
    val byLimit = ForecastSeries.from(points, reserve)
    val currentPace = currentPacePoints?.let { ForecastSeries.from(it, reserve) }
    val minimum = byLimit.minimum
    val maximum = byLimit.maximum
    val scaleMinimum = (points + currentPacePoints.orEmpty()).minOfOrNull { it.balance.amount } ?: BigDecimal.ZERO
    val scaleMaximum = (points + currentPacePoints.orEmpty()).maxOfOrNull { it.balance.amount } ?: BigDecimal.ZERO
    internal val limitGeometry = ForecastChartGeometry(points, scaleMinimum, scaleMaximum)
    internal val paceGeometry = currentPacePoints?.let { ForecastChartGeometry(it, scaleMinimum, scaleMaximum) }

    companion object {
        fun from(projection: BalanceForecastProjection) = ForecastUiStateMapper.map(projection)
    }
}

object ForecastUiStateMapper {
    fun map(projection: BalanceForecastProjection) = ForecastUiState(
        projection.startDate, projection.endDate, projection.points, projection.insufficientHistory,
        projection.currentPacePoints, projection.reserve, projection.warnings, projection.dependsOnPlannedIncome,
    )
}

class BalanceForecastViewModel(observe: ObserveBalanceForecast) : ViewModel() {
    val uiState = observe().map { it?.let(ForecastUiState::from) ?: ForecastUiState() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(0, 0), ForecastUiState())
}
