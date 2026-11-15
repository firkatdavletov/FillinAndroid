package com.firkat.fillin.feature.dashboard.widgets.balanceforecast.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firkat.fillin.feature.dailylimit.domain.ForecastWarning
import com.firkat.fillin.feature.dashboard.domain.model.BalanceForecastProjection
import com.firkat.fillin.feature.dashboard.domain.model.ForecastPoint
import com.firkat.fillin.feature.dashboard.domain.model.ForecastSeries
import com.firkat.fillin.feature.dashboard.domain.model.Money
import com.firkat.fillin.feature.dashboard.widgets.balanceforecast.domain.ObserveBalanceForecast
import java.math.BigDecimal
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
    val projection: BalanceForecastProjection? = null,
    val dataStatus: com.firkat.fillin.feature.dashboard.domain.model.DashboardStatus = com.firkat.fillin.feature.dashboard.domain.model.DashboardStatus.Loading
) {
    val byLimit = ForecastSeries.from(points, Money(BigDecimal.ZERO, reserve.currency))
    val currentPace = currentPacePoints?.let { ForecastSeries.from(it, Money(BigDecimal.ZERO, reserve.currency)) }
    val minimum = byLimit.minimum
    val maximum = byLimit.maximum
    val financialStatus = projection?.status ?: when {
        byLimit.firstDeficitDate != null -> com.firkat.fillin.feature.dashboard.domain.model.BalanceForecastStatus.STRUCTURAL_DEFICIT
        currentPace == null -> com.firkat.fillin.feature.dashboard.domain.model.BalanceForecastStatus.INSUFFICIENT_DATA
        currentPace.firstDeficitDate != null -> com.firkat.fillin.feature.dashboard.domain.model.BalanceForecastStatus.CURRENT_PACE_RISK
        else -> com.firkat.fillin.feature.dashboard.domain.model.BalanceForecastStatus.NORMAL
    }
    val scaleMinimum = (points + currentPacePoints.orEmpty()).minOfOrNull { it.balance.amount } ?: BigDecimal.ZERO
    val scaleMaximum = (points + currentPacePoints.orEmpty()).maxOfOrNull { it.balance.amount } ?: BigDecimal.ZERO

    companion object {
        fun from(
            projection: BalanceForecastProjection
        ) = ForecastUiStateMapper.map(projection)
    }
}

object ForecastUiStateMapper {
    fun map(
        projection: BalanceForecastProjection
    ) = ForecastUiState(
        projection.startDate, projection.endDate, projection.points, projection.insufficientHistory,
        projection.currentPacePoints, projection.reserve, projection.warnings, projection.dependsOnPlannedIncome, projection, com.firkat.fillin.feature.dashboard.domain.model.DashboardStatus.Ready
    )
}

class BalanceForecastViewModel(observe: ObserveBalanceForecast) : ViewModel() {
    val uiState = observe.states().map { data ->
        data.dashboard?.balanceForecast?.let(ForecastUiState::from)
            ?: ForecastUiState(dataStatus = data.status)
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(0, 0), ForecastUiState())
}
