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
) {
    companion object {
        fun from(dashboard: Dashboard): DailyLimitSummary {
            val limit = dashboard.freeMoney.safeDailyLimit
            val spent = dashboard.todayVariableNetExpenses.amount.max(BigDecimal.ZERO)
            val remaining = limit.amount - spent
            fun percent(amount: BigDecimal) = limit.amount.takeIf { it.signum() > 0 }?.let {
                amount.multiply(BigDecimal(100)).divide(it, 1, RoundingMode.HALF_UP)
            }
            return DailyLimitSummary(
                limit = limit,
                spentToday = Money(spent.setScale(2), limit.currency),
                remainingToday = Money(remaining.setScale(2), limit.currency),
                spentPercent = percent(spent),
                remainingPercent = percent(remaining),
            )
        }
    }
}
