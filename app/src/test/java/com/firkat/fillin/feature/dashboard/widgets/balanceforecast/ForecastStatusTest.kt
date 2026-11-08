package com.firkat.fillin.feature.dashboard.widgets.balanceforecast

import com.firkat.fillin.feature.dashboard.domain.model.*
import com.firkat.fillin.feature.dailylimit.domain.*
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class ForecastStatusTest {
    private val start = LocalDate.of(2026, 11, 3)
    private fun money(value: Int) = Money(value.toBigDecimal().setScale(2), "KZT")
    private fun points(vararg amounts: Int) = amounts.mapIndexed { index, value -> ForecastPoint(start.plusDays(index.toLong()), money(value)) }
    private fun projection() = BalanceForecastProjection(start, start.plusDays(2), points(100, 50, 20), false, points(100, 20, 10), averageDailyExpenses = money(80), dailyLimit = money(50))
    @Test fun `temporary deficit matters even when ending is positive`() {
        assertEquals(BalanceForecastStatus.NORMAL, projection().status)
        val risk = projection().copy(currentPacePoints = points(100, -10, 50))
        assertEquals(BalanceForecastStatus.CURRENT_PACE_RISK, risk.status)
        assertEquals(start.plusDays(1), risk.currentPace!!.firstDeficitDate)
        assertEquals(money(30), risk.recommendedReduction)
    }
    @Test fun `structural deficit wins over absent history and keeps negative balances`() {
        val result = CashFlowForecaster().forecast(DailyLimitInput(money(100), money(0), listOf(ForecastEvent("rent", "Rent", start.plusDays(2), money(200), false)), start), null).projection
        assertEquals(BalanceForecastStatus.STRUCTURAL_DEFICIT, result.status)
        assertEquals(start.plusDays(2), result.structuralDeficitDate)
        assertNull(result.currentPace)
        assertEquals(money(-100), result.byLimit.minimum!!.balance)
        assertNull(result.recommendedReduction)
    }
    @Test fun `recommendation requires positive limit and a safe limit series`() {
        assertEquals(BalanceForecastStatus.INSUFFICIENT_DATA, projection().copy(currentPacePoints = null).status)
        assertNull(projection().copy(averageDailyExpenses = null).recommendedReduction)
        assertNull(projection().copy(dailyLimit = money(0)).recommendedReduction)
        assertNull(projection().copy(averageDailyExpenses = money(40)).recommendedReduction)
        assertNull(projection().copy(points = points(100, -1, 20)).recommendedReduction)
        assertNull(projection().copy(structuralDeficitDate = start).recommendedReduction)
    }
    @Test fun `reserve does not turn a positive balance into a deficit in forecast UI`() {
        val result = projection().copy(reserve = money(1000))
        assertNull(result.byLimit.firstDeficitDate)
        assertEquals(BalanceForecastStatus.NORMAL, result.status)
    }
    @Test fun `details reuse exact limit calendar and allocation`() {
        val result = CashFlowForecaster().forecast(DailyLimitInput(money(6000), money(50), listOf(ForecastEvent("salary", "Salary", start.plusDays(5), money(100), true)), start), money(200))
        assertEquals(result.dailyLimit.projectedDailyBalances.flatMap { it.events }, result.projection.events)
        assertEquals(result.dailyLimit.dailyTarget, result.projection.dailyLimit)
        assertEquals(result.dailyLimit.remainingToday, result.projection.remainingToday)
        assertEquals(result.dailyLimit.nextDaysLimit, result.projection.nextDaysLimit)
        assertEquals(money(6000), result.projection.currentBalance)
    }
}
