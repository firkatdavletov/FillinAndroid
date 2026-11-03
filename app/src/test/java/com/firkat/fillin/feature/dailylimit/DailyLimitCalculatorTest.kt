package com.firkat.fillin.feature.dailylimit

import com.firkat.fillin.feature.dailylimit.domain.*
import com.firkat.fillin.feature.dashboard.domain.model.Money
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class DailyLimitCalculatorTest {
    private val today = LocalDate.of(2026,11,1)
    private fun money(value: String) = Money(BigDecimal(value), "KZT")
    private fun event(day: LocalDate, amount: String, income: Boolean) = ForecastEvent("$day-$amount-$income", "Plan", day, money(amount), income)
    private fun calculate(balance: String, events: List<ForecastEvent> = emptyList(), spent: String = "0", date: LocalDate = today) =
        DailyLimitCalculator().calculate(DailyLimitInput(money(balance), money(spent), events, date))
    private fun assertAmount(expected: String, actual: Money) = assertEquals(0, BigDecimal(expected).compareTo(actual.amount))

    @Test fun `DL01 income before obligation makes plan feasible`() {
        val result = calculate("100000", listOf(event(today.plusDays(14),"300000",true), event(today.plusDays(49-5),"300000",true), event(today.plusDays(19),"150000",false)))
        assertAmount("5681", result.dailyTarget)
        assertEquals(44, result.horizonDays)
        assertEquals(LocalDate.of(2026,12,14), result.horizonEnd)
        assertTrue(result.dependsOnPlannedIncome)
        assertTrue(result.projectedDailyBalances.all { it.afterDailySpending.amount.signum() >= 0 })
    }
    @Test fun `DL02 salary at month end has rolling horizon`() {
        val start = LocalDate.of(2026,10,29)
        val result = calculate("400000", listOf(event(LocalDate.of(2026,11,29),"400000",true), event(LocalDate.of(2026,11,10),"220000",false)), date = start)
        assertAmount("5806", result.dailyTarget)
        assertEquals(31,result.horizonDays)
    }
    @Test fun `DL03 horizon excludes boundary income`() {
        for (offset in listOf(30L,59L,60L)) {
            val result = calculate("60000", listOf(event(today.plusDays(offset),"99999999",true)))
            assertEquals(offset.toInt(), result.horizonDays)
            assertFalse(result.dependsOnPlannedIncome)
        }
        assertEquals(60,calculate("60000",listOf(event(today.plusDays(29),"60000",true))).horizonDays)
    }
    @Test fun `DL04 same day income cannot fund obligation`() {
        val result = calculate("100",listOf(event(today.plusDays(2),"1000",true),event(today.plusDays(2),"140",false)))
        assertEquals(ForecastStatus.DEFICIT,result.status)
        assertEquals(today.plusDays(2),result.firstDeficitDate)
        assertAmount("40",requireNotNull(result.deficitAmount))
        assertAmount("0",result.dailyTarget)
    }
    @Test fun `DL07 overdue income excluded and overdue expense due immediately`() {
        val result = calculate("100",listOf(event(today.minusDays(1),"500",true),event(today.minusDays(1),"120",false)))
        assertEquals(today,result.firstDeficitDate)
        assertTrue(ForecastWarning.OVERDUE_INCOME in result.warnings)
    }
    @Test fun `DL08 purchases and refunds preserve target`() {
        val events = listOf(event(today.plusDays(30),"100000",true))
        for ((balance, net) in listOf("60000" to "0", "59000" to "1000", "61000" to "-1000")) {
            assertAmount("2000",calculate(balance,events,net).dailyTarget)
        }
        assertAmount("1000", calculate("59000",events,"1000").remainingToday)
    }
    @Test fun `DL09 overspending adjusts future days`() {
        val result = calculate("57000",spent="3000")
        assertAmount("1000",result.dailyTarget)
        assertAmount("0",result.remainingToday)
        assertAmount("2000",result.overspentToday)
        assertAmount("966",result.nextDaysLimit)
    }
    @Test fun `DL10 monthly clamp retains original day`() {
        val dates = ForecastEventGenerator.dates(LocalDate.of(2028,1,31),IncomeFrequency.MONTHLY,LocalDate.of(2028,3,31)).toList()
        assertEquals(listOf(LocalDate.of(2028,1,31),LocalDate.of(2028,2,29),LocalDate.of(2028,3,31)),dates)
    }
    @Test fun `DL11 no income means sixty conservative days`() {
        val result = calculate("60000")
        assertAmount("1000",result.dailyTarget)
        assertEquals(60,result.horizonDays)
        assertEquals(ForecastStatus.INCOMPLETE_DATA,result.status)
    }
    @Test fun `DL12 next month does not reset window`() {
        for (date in listOf(LocalDate.of(2026,10,31),LocalDate.of(2026,11,1))) assertEquals(60,calculate("60000",date=date).horizonDays)
    }
    @Test fun `negative balance and zero balance before tomorrow income`() {
        assertEquals(ForecastStatus.DEFICIT,calculate("-1").status)
        assertAmount("0",calculate("0",listOf(event(today.plusDays(1),"100000",true))).dailyTarget)
    }
    @Test fun `weekly and fortnightly schedules preserve anchor`() {
        for ((frequency,interval) in listOf(IncomeFrequency.WEEKLY to 7L,IncomeFrequency.BIWEEKLY to 14L)) {
            val dates = ForecastEventGenerator.dates(today,frequency,today.plusDays(29)).toList()
            assertEquals(today.plusDays(interval),dates[1])
        }
    }
    @Test fun `currency mismatch rejected`() {
        try { DailyLimitCalculator().calculate(DailyLimitInput(money("100"),Money(BigDecimal.ZERO,"USD"), emptyList(),today)); fail() }
        catch (_: IllegalArgumentException) { }
    }
}
