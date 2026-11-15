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
    val currentBalance: Money? = null,
    val averageDailyExpenses: Money? = null,
    val dailyLimit: Money? = null,
    val remainingToday: Money? = null,
    val nextDaysLimit: Money? = null,
    val events: List<com.firkat.fillin.feature.dailylimit.domain.ForecastEvent> = emptyList(),
    val structuralDeficitDate: LocalDate? = null
) {
    val byLimit = ForecastSeries.from(points, Money(BigDecimal.ZERO, reserve.currency))
    val currentPace = currentPacePoints?.let { ForecastSeries.from(it, Money(BigDecimal.ZERO, reserve.currency)) }
    val status: BalanceForecastStatus get() = when {
        structuralDeficitDate != null -> BalanceForecastStatus.STRUCTURAL_DEFICIT
        currentPace == null -> BalanceForecastStatus.INSUFFICIENT_DATA
        currentPace.firstDeficitDate != null -> BalanceForecastStatus.CURRENT_PACE_RISK
        else -> BalanceForecastStatus.NORMAL
    }
    val recommendedReduction: Money? get() {
        val average = averageDailyExpenses ?: return null
        val limit = dailyLimit ?: return null
        return if (structuralDeficitDate == null &&
            byLimit.firstDeficitDate == null &&
            limit.amount.signum() > 0 &&
            average.amount > limit.amount
        ) {
            Money(average.amount - limit.amount, limit.currency)
        } else {
            null
        }
    }
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
    val deficitAmount: Money?
) {
    val risk = when {
        endingBalance == null -> ForecastRisk.Unavailable
        firstDeficitDate != null -> ForecastRisk.Deficit
        else -> ForecastRisk.NoDeficit
    }

    companion object {
        fun from(
            points: List<ForecastPoint>,
            reserve: Money
        ): ForecastSeries {
            val deficit = points.firstOrNull { it.balance.amount < reserve.amount }
            return ForecastSeries(
                points,
                points.lastOrNull()?.balance,
                points.minByOrNull { it.balance.amount },
                points.maxByOrNull { it.balance.amount },
                deficit?.date,
                deficit?.let {
                    Money((reserve.amount - it.balance.amount).setScale(2), reserve.currency)
                }
            )
        }
    }
}

data class ForecastPoint(val date: LocalDate, val balance: Money)

enum class BalanceForecastStatus { NORMAL, CURRENT_PACE_RISK, STRUCTURAL_DEFICIT, INSUFFICIENT_DATA }
