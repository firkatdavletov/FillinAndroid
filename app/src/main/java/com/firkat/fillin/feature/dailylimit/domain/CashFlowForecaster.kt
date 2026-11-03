package com.firkat.fillin.feature.dailylimit.domain

import com.firkat.fillin.feature.dashboard.domain.model.BalanceForecastProjection
import com.firkat.fillin.feature.dashboard.domain.model.ForecastPoint
import com.firkat.fillin.feature.dashboard.domain.model.Money
import java.math.BigDecimal

class CashFlowForecaster {
    fun forecast(input: DailyLimitInput, averageDailySpending: Money?): CashFlowForecast {
        require(averageDailySpending == null ||
            (averageDailySpending.currency == input.balance.currency && averageDailySpending.amount.signum() >= 0))
        val days = calendar(input, horizonEnd(input))
        val limit = DailyLimitCalculator().calculate(input, days)
        val limitPoints = limit.projectedDailyBalances.map {
            ForecastPoint(it.date, it.afterDailySpending)
        }
        val pacePoints = averageDailySpending?.let { average ->
            val remaining = (average.amount - input.todayVariableNet.amount.max(BigDecimal.ZERO)).max(BigDecimal.ZERO)
            days.mapIndexed { index, day ->
                ForecastPoint(day.date,
                    Money((day.withoutDailySpending.amount - remaining - average.amount * BigDecimal(index)).setScale(2), input.balance.currency))
            }
        }
        return CashFlowForecast(limit, BalanceForecastProjection(
            input.today, days.last().date, limitPoints, pacePoints == null, pacePoints,
            input.reserve, limit.warnings, limit.dependsOnPlannedIncome,
        ))
    }
}

data class CashFlowForecast(
    val dailyLimit: DailyLimitResult,
    val projection: BalanceForecastProjection,
)

