package com.firkat.fillin.feature.dashboard.widgets.dailylimit.domain

import com.firkat.fillin.feature.dashboard.domain.model.Dashboard
import com.firkat.fillin.feature.dashboard.domain.model.Money
import java.math.BigDecimal
import java.math.RoundingMode

data class DailyLimitSummary(
    val limit: Money,
    val spentToday: Money,
    val remainingToday: Money,
    val spentPercent: BigDecimal?,
    val remainingPercent: BigDecimal?,
    val forecast: com.firkat.fillin.feature.dailylimit.domain.DailyLimitResult? = null,
) {
    companion object {
        fun from(dashboard: Dashboard): DailyLimitSummary {
            val result = requireNotNull(dashboard.dailyLimit)
            val limit = result.dailyTarget
            val spent = dashboard.todayVariableNetExpenses.amount.max(BigDecimal.ZERO)
            val remaining = result.remainingToday.amount
            fun percent(amount: BigDecimal) = limit.amount.takeIf { it.signum() > 0 }?.let {
                amount.multiply(BigDecimal(100)).divide(it, 1, RoundingMode.HALF_UP)
            }
            return DailyLimitSummary(
                limit = limit,
                spentToday = Money(spent.setScale(2), limit.currency),
                remainingToday = Money(remaining.setScale(2), limit.currency),
                spentPercent = percent(spent),
                remainingPercent = percent(remaining),
                forecast = result,
            )
        }
    }
}
