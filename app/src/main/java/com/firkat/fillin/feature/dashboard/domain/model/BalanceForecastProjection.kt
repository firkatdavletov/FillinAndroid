package com.firkat.fillin.feature.dashboard.domain.model

import com.firkat.fillin.feature.dailylimit.domain.ForecastWarning
import java.math.BigDecimal
import java.time.LocalDate

/** Both scenarios contain end-of-day balances on the daily-limit horizon. */
data class BalanceForecastProjection(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val points: List<ForecastPoint>,
    val insufficientHistory: Boolean,
    val currentPacePoints: List<ForecastPoint>? = null,
    val reserve: Money = Money(BigDecimal.ZERO, "KZT"),
    val warnings: Set<ForecastWarning> = emptySet(),
    val dependsOnPlannedIncome: Boolean = false,
) {
    val byLimit = ForecastSeries.from(points, reserve)
    val currentPace = currentPacePoints?.let { ForecastSeries.from(it, reserve) }
    val minimum: ForecastPoint? get() = byLimit.minimum
    val maximum: ForecastPoint? get() = byLimit.maximum
}

enum class ForecastRisk { Unavailable, NoDeficit, Deficit }

data class ForecastSeries(
    val points: List<ForecastPoint>,
    val endingBalance: Money?,
    val minimum: ForecastPoint?,
    val maximum: ForecastPoint?,
    val firstDeficitDate: LocalDate?,
    val deficitAmount: Money?,
) {
    val risk = when {
        endingBalance == null -> ForecastRisk.Unavailable
        firstDeficitDate != null -> ForecastRisk.Deficit
        else -> ForecastRisk.NoDeficit
    }

    companion object {
        fun from(points: List<ForecastPoint>, reserve: Money): ForecastSeries {
            val deficit = points.firstOrNull { it.balance.amount < reserve.amount }
            return ForecastSeries(points, points.lastOrNull()?.balance,
                points.minByOrNull { it.balance.amount }, points.maxByOrNull { it.balance.amount },
                deficit?.date, deficit?.let { Money((reserve.amount - it.balance.amount).setScale(2), reserve.currency) })
        }
    }
}

data class ForecastPoint(val date: LocalDate, val balance: Money)
