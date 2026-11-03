package com.firkat.fillin.feature.dashboard.domain.calculation

import com.firkat.fillin.feature.dashboard.domain.model.BalanceForecastProjection
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class CalculateBalanceForecastTest {
    private val today = LocalDate.of(2026, 10, 25)
    private val category = "00000000-0000-0000-0000-000000000001"
    private val zone = ZoneId.of("Asia/Almaty")
    private fun amount(value: String) = value.toBigDecimal().setScale(2)
    private fun expense(id: String, value: String, date: LocalDate = today.minusDays(1)) = DashboardOperation(
        id, category, DashboardOperationKind.Expense, amount(value), date, date.atStartOfDay(zone).toInstant(),
    )
    private fun input(date: LocalDate = today) = DashboardCalculationInput(
        date.atTime(12, 0).atZone(zone).toInstant(), date.minusDays(60).atStartOfDay(zone).toInstant(),
        amount("13000"), amount("500"), amount("300"),
        listOf(expense("history", "3000", date.minusDays(1))),
        listOf(DashboardCategory(category, "Продукты", true)), emptyList(), emptyList(), emptySet(),
    )
    private fun forecast(input: DashboardCalculationInput = input()) = CalculateDashboard.calculate(input).balanceForecast
    private fun BalanceForecastProjection.balance(day: Int) = points[day].balance.amount

    @Test fun `uses thirty completed days including zero spending days and projects thirty days ahead`() {
        val result = forecast()
        assertEquals(today, result.startDate)
        assertEquals(today.plusDays(30), result.endDate)
        assertEquals(31, result.points.size)
        assertEquals((0L..30L).map(today::plusDays), result.points.map { it.date })
        assertEquals(amount("9900"), result.balance(0))
        assertEquals(amount("6900"), result.balance(30))
        assertEquals(today, result.maximum!!.date)
        assertEquals(today.plusDays(30), result.minimum!!.date)
        assertFalse(result.insufficientHistory)
    }

    @Test fun `groups daily plans includes overdue expenses and ignores overdue incomes`() {
        val result = forecast(input().copy(
            payments = listOf(
                DashboardPayment("overdue", "Кредит", today.minusDays(3), amount("200")),
                DashboardPayment("first", "Аренда", today.plusDays(1), amount("600")),
                DashboardPayment("second", "Связь", today.plusDays(1), amount("100")),
            ),
            incomes = listOf(
                DashboardExpectedIncome(today.minusDays(1), amount("99999")),
                DashboardExpectedIncome(today.plusDays(2), amount("400")),
                DashboardExpectedIncome(today.plusDays(2), amount("600")),
            ),
        ))
        assertEquals(amount("9700"), result.balance(0))
        assertEquals(amount("8900"), result.balance(1))
        assertEquals(amount("9800"), result.balance(2))
        assertEquals(amount("7000"), result.balance(30))
        assertEquals(today.plusDays(2), result.maximum!!.date)
    }

    @Test fun `includes both horizon endpoints and ignores plans beyond the last day`() {
        val result = forecast(input().copy(
            payments = listOf(
                DashboardPayment("today", "Аренда", today, amount("100")),
                DashboardPayment("last", "Связь", today.plusDays(30), amount("200")),
                DashboardPayment("outside", "Связь", today.plusDays(31), amount("99999")),
            ),
            incomes = listOf(
                DashboardExpectedIncome(today, amount("300")),
                DashboardExpectedIncome(today.plusDays(30), amount("400")),
                DashboardExpectedIncome(today.plusDays(31), amount("99999")),
            ),
        ))
        assertEquals(amount("10100"), result.balance(0))
        assertEquals(amount("7300"), result.balance(30))
    }

    @Test fun `already spent today reduces only the remaining daily estimate`() {
        val before = forecast()
        for (spent in listOf("40", "100", "150")) {
            val source = input()
            val result = forecast(source.copy(operations = source.operations + expense("today", spent, today)))
            val expected = amount("10000") - maxOf(amount("100"), amount(spent))
            assertEquals(expected, result.balance(0))
            assertEquals(expected - amount("3000"), result.balance(30))
            if (spent == "40") assertEquals(before.points, result.points)
        }
    }

    @Test fun `refunds above today spending increase balance without increasing the daily estimate`() {
        val source = input()
        val refund = expense("refund", "50", today).copy(kind = DashboardOperationKind.Refund, refundOf = "history")
        val result = forecast(source.copy(operations = source.operations + refund))
        assertEquals(amount("9950"), result.balance(0))
    }

    @Test fun `history refunds use original classification and regular payments do not inflate the average`() {
        val source = input()
        val regular = expense("regular", "600")
        val refund = expense("refund", "300").copy(kind = DashboardOperationKind.Refund, refundOf = "history")
        val regularRefund = expense("regular-refund", "200").copy(kind = DashboardOperationKind.Refund, refundOf = "regular")
        val result = forecast(source.copy(
            operations = source.operations + regular + refund + regularRefund,
            excludedVariableExpenseIds = setOf("regular"),
        ))
        // Actual balance is 9900; variable daily average is (3000 - 300) / 30 = 90.
        assertEquals(amount("9810"), result.balance(0))
        assertEquals(amount("7110"), result.balance(30))
    }

    @Test fun `history excludes days before the rolling window and average rounds half up`() {
        val source = input()
        val result = forecast(source.copy(
            openingBalance = amount("2000"),
            operations = listOf(expense("old", "1000", today.minusDays(31)), expense("history", "100.05")),
        ))
        assertEquals(amount("896.61"), result.balance(0))
        assertEquals(amount("796.41"), result.balance(30))
    }

    @Test fun `thirteen completed days hide the forecast and fourteen days unlock it`() {
        val source = input()
        val fresh = forecast(source.copy(accountingStartedAt = today.minusDays(13).atStartOfDay(zone).toInstant()))
        assertTrue(fresh.insufficientHistory)
        assertTrue(fresh.points.isEmpty())
        assertNull(fresh.minimum)
        assertNull(fresh.maximum)
        val ready = forecast(source.copy(accountingStartedAt = today.minusDays(14).atStartOfDay(zone).toInstant()))
        assertFalse(ready.insufficientHistory)
        assertEquals(amount("9785.71"), ready.balance(0))
    }

    @Test fun `zero and negative flat balances retain their sign and earliest extrema dates`() {
        for (balance in listOf("0", "-50", "100")) {
            val result = forecast(input().copy(openingBalance = amount(balance), operations = emptyList()))
            assertTrue(result.points.all { it.balance.amount == amount(balance) })
            assertEquals(result.points.first(), result.minimum)
            assertEquals(result.minimum, result.maximum)
        }
    }

    @Test fun `crosses leap February and year boundaries in Almaty and advances at midnight`() {
        for (date in listOf(LocalDate.of(2024, 2, 28), LocalDate.of(2026, 12, 25))) {
            val result = forecast(input(date))
            assertEquals(date.plusDays(30), result.points.last().date)
            assertEquals(31, result.points.size)
        }
        val source = input().copy(asOf = today.atTime(23, 59, 59).atZone(zone).toInstant())
        assertEquals(today, forecast(source).startDate)
        assertEquals(today.plusDays(1), forecast(source.copy(asOf = source.asOf.plusSeconds(1))).startDate)
    }

    @Test fun `next month plans change the rolling graph without changing monthly dashboard metrics`() {
        val source = input()
        val before = CalculateDashboard.calculate(source)
        val after = CalculateDashboard.calculate(source.copy(
            payments = listOf(DashboardPayment("next-month", "Аренда", today.plusDays(10), amount("1000"))),
            incomes = listOf(DashboardExpectedIncome(today.plusDays(11), amount("2000"))),
        ))
        assertEquals(before.forecast, after.forecast)
        assertEquals(before.freeMoney.copy(safeDailyLimit = after.freeMoney.safeDailyLimit), after.freeMoney)
        assertEquals(before.budgetExecution, after.budgetExecution)
        assertEquals(before.monthActuals, after.monthActuals)
        assertEquals(before.balanceForecast.balance(30) + amount("1000"), after.balanceForecast.balance(30))
    }
}
