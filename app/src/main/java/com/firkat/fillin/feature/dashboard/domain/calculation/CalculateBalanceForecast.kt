package com.firkat.fillin.feature.dashboard.domain.calculation

import com.firkat.fillin.feature.dashboard.domain.model.BalanceForecastProjection
import com.firkat.fillin.feature.dashboard.domain.model.ForecastPoint
import com.firkat.fillin.feature.dashboard.domain.model.Money
import java.math.BigDecimal
import java.time.LocalDate

internal object CalculateBalanceForecast {
    fun calculate(
        input: DashboardCalculationInput,
        today: LocalDate,
        balance: BigDecimal,
        dailySpending: BigDecimal,
        todayVariableNet: BigDecimal,
        hasSufficientHistory: Boolean,
    ): BalanceForecastProjection {
        val end = today.plusDays(30)
        if (!hasSufficientHistory) return BalanceForecastProjection(today, end, emptyList(), true)

        val payments = input.payments.filter { it.dueOn <= end }.groupBy { maxOf(it.dueOn, today) }
        val incomes = input.incomes.filter { it.dueOn in today..end }.groupBy { it.dueOn }
        val remainingToday = (dailySpending - todayVariableNet.max(BigDecimal.ZERO)).max(BigDecimal.ZERO)
        var remaining = balance
        val points = (0L..30L).map { offset ->
            val date = today.plusDays(offset)
            remaining += incomes[date].orEmpty().sumOf { it.amount }
            remaining -= payments[date].orEmpty().sumOf { it.remaining }
            remaining -= if (offset == 0L) remainingToday else dailySpending
            ForecastPoint(date, Money(remaining.setScale(2), "KZT"))
        }
        return BalanceForecastProjection(today, end, points, false)
    }
}
