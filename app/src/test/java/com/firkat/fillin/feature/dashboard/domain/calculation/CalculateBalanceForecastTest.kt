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


    @Test fun `both series use limit horizon and income is available the next day`() {
        val dashboard = CalculateDashboard.calculate(input().copy(
            payments = listOf(DashboardPayment("overdue", "Rent", today.minusDays(3), amount("200"))),
            incomes = listOf(DashboardExpectedIncome(today.plusDays(2), amount("1000"))),
        ))
        val result = dashboard.balanceForecast
        assertEquals(today.plusDays(59), result.endDate)
        assertEquals(60, result.points.size)
        assertEquals(result.points.map { it.date }, result.currentPacePoints!!.map { it.date })
        assertEquals(amount("9500"), result.currentPacePoints[2].balance.amount)
        assertEquals(amount("10400"), result.currentPacePoints[3].balance.amount)
        assertEquals(dashboard.dailyLimit!!.projectedDailyBalances.map { it.afterDailySpending }, result.points.map { it.balance })
        assertEquals(result.points.last().balance, result.byLimit.endingBalance)
        assertEquals(result.currentPacePoints.last().balance, result.currentPace!!.endingBalance)
    }

    @Test fun `income boundary ends both scenarios before that income`() {
        for (offset in listOf(30L, 44L, 60L)) {
            val result = forecast(input().copy(incomes = listOf(DashboardExpectedIncome(today.plusDays(offset), amount("99999")))))
            assertEquals(today.plusDays(offset - 1), result.endDate)
            assertEquals(offset.toInt(), result.points.size)
            assertEquals(result.points.size, result.currentPacePoints!!.size)
            assertFalse(result.dependsOnPlannedIncome)
        }
    }

    @Test fun `pace starts at the first Almaty midnight and includes days without spending`() {
        val source = input().copy(
            asOf = today.atTime(23, 59, 59).atZone(zone).toInstant(),
            accountingStartedAt = today.atStartOfDay(zone).toInstant(),
            operations = listOf(expense("first-day", "1000", today)),
        )
        val firstDay = forecast(source)
        assertTrue(firstDay.insufficientHistory)
        assertEquals(60, firstDay.points.size)
        assertNull(firstDay.averageDailyExpenses)
        assertNull(firstDay.currentPacePoints)

        val secondDay = forecast(source.copy(asOf = source.asOf.plusSeconds(1)))
        assertFalse(secondDay.insufficientHistory)
        assertEquals(amount("1000"), secondDay.averageDailyExpenses!!.amount)
        assertNotNull(secondDay.currentPace)

        val thirdDay = forecast(source.copy(asOf = today.plusDays(2).atStartOfDay(zone).toInstant()))
        assertEquals(amount("500"), thirdDay.averageDailyExpenses!!.amount)
    }

    @Test fun `one tracked day without operations gives a real zero average`() {
        val result = forecast(input().copy(operations = emptyList(), accountingStartedAt = today.minusDays(1).atStartOfDay(zone).toInstant()))
        assertFalse(result.insufficientHistory)
        assertEquals(amount("0"), result.averageDailyExpenses!!.amount)
        assertNotNull(result.currentPace)
    }

    @Test fun `history window grows to thirty days and excludes older spending`() {
        val source = input()
        val operations = source.operations + listOf(
            expense("thirty-days-ago", "300", today.minusDays(30)),
            expense("thirty-one-days-ago", "600", today.minusDays(31)),
        )
        for ((days, expected) in listOf(29L to "103.45", 30L to "110", 31L to "110")) {
            val result = forecast(source.copy(
                accountingStartedAt = today.minusDays(days).atStartOfDay(zone).toInstant(),
                operations = operations,
            ))
            assertEquals(amount(expected), result.averageDailyExpenses!!.amount)
        }
    }

    @Test fun `today spending is not subtracted twice and excess refunds do not raise spending estimate`() {
        for (spent in listOf("40", "100", "150")) {
            val source = input()
            val result = forecast(source.copy(operations = source.operations + expense("today", spent, today)))
            assertEquals(amount("100"), result.averageDailyExpenses!!.amount)
            assertEquals(amount("10000") - maxOf(amount("100"), amount(spent)), result.currentPacePoints!!.first().balance.amount)
        }
        val source = input()
        val refund = expense("refund", "50", today).copy(kind = DashboardOperationKind.Refund, refundOf = "history")
        assertEquals(amount("9950"), forecast(source.copy(operations = source.operations + refund)).currentPacePoints!!.first().balance.amount)
    }

    @Test fun `history refunds retain original classification and scheduled payments do not inflate average`() {
        val source = input()
        val regular = expense("regular", "600")
        val refund = expense("refund", "300").copy(kind = DashboardOperationKind.Refund, refundOf = "history")
        val regularRefund = expense("regular-refund", "200").copy(kind = DashboardOperationKind.Refund, refundOf = "regular")
        val result = forecast(source.copy(operations = source.operations + regular + refund + regularRefund,
            excludedVariableExpenseIds = setOf("regular")))
        assertEquals(amount("9810"), result.currentPacePoints!!.first().balance.amount)
    }

    @Test fun `history excludes older days and average rounds half up`() {
        val result = forecast(input().copy(openingBalance = amount("2000"),
            operations = listOf(expense("old", "1000", today.minusDays(31)), expense("history", "100.05"))))
        assertEquals(amount("896.61"), result.currentPacePoints!!.first().balance.amount)
        assertEquals(amount("796.41"), result.currentPacePoints[30].balance.amount)
    }

    @Test fun `series report first deficit relative to reserve and retain earliest extrema`() {
        val points = listOf("100", "40", "40", "-10").mapIndexed { index, value ->
            com.firkat.fillin.feature.dashboard.domain.model.ForecastPoint(today.plusDays(index.toLong()),
                com.firkat.fillin.feature.dashboard.domain.model.Money(amount(value), "KZT"))
        }
        val series = com.firkat.fillin.feature.dashboard.domain.model.ForecastSeries.from(points,
            com.firkat.fillin.feature.dashboard.domain.model.Money(amount("50"), "KZT"))
        assertEquals(today.plusDays(1), series.firstDeficitDate)
        assertEquals(amount("10"), series.deficitAmount!!.amount)
        val flat = forecast(input().copy(openingBalance = amount("0"), operations = emptyList()))
        assertEquals(flat.minimum, flat.maximum)
        assertEquals(today, flat.minimum!!.date)
    }

    @Test fun `crosses calendar boundaries and updates both endpoints at Almaty midnight`() {
        for (date in listOf(LocalDate.of(2024, 2, 28), LocalDate.of(2026, 12, 25))) {
            assertEquals(date.plusDays(59), forecast(input(date)).points.last().date)
        }
        val source = input().copy(asOf = today.atTime(23, 59, 59).atZone(zone).toInstant())
        assertEquals(today.plusDays(1), forecast(source.copy(asOf = source.asOf.plusSeconds(1))).startDate)
    }
}
