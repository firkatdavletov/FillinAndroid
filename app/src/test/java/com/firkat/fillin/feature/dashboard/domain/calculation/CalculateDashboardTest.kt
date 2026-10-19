package com.firkat.fillin.feature.dashboard.domain.calculation

import com.firkat.fillin.feature.dashboard.domain.model.*
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class CalculateDashboardTest {
    @Test fun `matches every dashboard field of the server integration control example`() {
        // Same financial inputs as FinanceConsultante DashboardApiTests, 2026-09-06.
        val input = input().copy(
            openingBalance = decimal("300000"),
            protectedReserve = decimal("50000"),
            goalReserve = decimal("30000"),
            operations = listOf(
                operation("income", INCOME, DashboardOperationKind.Income, "100000", "2026-09-01"),
                operation("groceries", FOOD, DashboardOperationKind.Expense, "30000", "2026-09-02"),
                operation("transport", TRANSPORT, DashboardOperationKind.Expense, "10000", "2026-09-03"),
                operation("payment", HOUSING, DashboardOperationKind.Expense, "20000", "2026-09-06")
            ),
            payments = listOf(DashboardPayment("payment", "Жильё", LocalDate.parse("2026-09-06"), decimal("80000"))),
            incomes = listOf(DashboardExpectedIncome(LocalDate.parse("2026-09-10"), decimal("50000"))),
            excludedVariableExpenseIds = setOf("payment")
        )
        val expected = Dashboard(
            LocalDate.parse("2026-09-06"), money("340000"),
            FreeMoney(
                money("180000"),
                FreeMoneyState.Available,
                money("80000"),
                money("50000"),
                money("30000"),
                money("160000"),
                25,
                money("7200")
            ),
            MonthActuals(money("100000"), money("60000"), money("0"), money("60000")),
            CashFlow(money("40000")),
            BudgetExecution(
                BudgetBasis.Forecast,
                BudgetProgress(money("60000"), money("173333.25"), BigDecimal("34.6"), PaceStatus.AheadOfPlan),
                BudgetProgress(money("40000"), money("73333.25"), BigDecimal("54.5"), PaceStatus.AheadOfPlan)
            ),
            Forecast(
                LocalDate.parse("2026-09-30"),
                "HISTORICAL_AVERAGE",
                ForecastScenario(money("50000"), money("113333.25"), money("276666.75")),
                ForecastScenario(money("50000"), money("120000"), money("270000"))
            ),
            listOf(
                ExpenseCategory("Продукты", money("30000"), BigDecimal("50.0")),
                ExpenseCategory("Жильё", money("20000"), BigDecimal("33.3")),
                ExpenseCategory("Транспорт", money("10000"), BigDecimal("16.7"))
            ),
            listOf(UpcomingPayment("Жильё", LocalDate.parse("2026-09-06"), money("80000")))
        )
        assertEquals(expected, CalculateDashboard.calculate(input))
    }

    @Test fun `refunds use their own dates and original classification and preserve negative net spending`() {
        val result = CalculateDashboard.calculate(
            input().copy(
                operations = listOf(
                    operation("original", FOOD, DashboardOperationKind.Expense, "100", "2026-08-31"),
                    operation("variable", TRANSPORT, DashboardOperationKind.Expense, "60", "2026-09-02"),
                    operation(
                        "refund",
                        FOOD,
                        DashboardOperationKind.Refund,
                        "100",
                        "2026-09-03"
                    ).copy(refundOf = "original")
                ),
                excludedVariableExpenseIds = setOf("original")
            )
        )
        assertEquals(decimal("-40"), result.monthActuals.netExpenses.amount)
        assertEquals(decimal("60"), result.budgetExecution.variable.spent!!.amount)
        assertEquals(decimal("0"), result.budgetExecution.total.spent!!.amount)
        assertEquals(listOf("Транспорт"), result.expenseCategories.map { it.name })
        assertEquals(BigDecimal("100.0"), result.expenseCategories.single().sharePercent)
    }

    @Test fun `future instants do not increase balance and plans do not count as actual income`() {
        val result = CalculateDashboard.calculate(
            input().copy(
                operations = listOf(
                    operation("future", INCOME, DashboardOperationKind.Income, "25", "2026-09-06")
                        .copy(occurredAt = Instant.parse("2026-09-06T10:00:00Z"))
                ),
                incomes = listOf(DashboardExpectedIncome(LocalDate.parse("2026-09-30"), decimal("100")))
            )
        )
        assertEquals(decimal("0"), result.availableMoney.amount)
        assertEquals(decimal("100"), result.forecast.base.expectedIncome.amount)
        assertEquals(decimal("0"), result.forecast.cautious!!.expectedIncome.amount)
    }

    @Test fun `zero budgets have no percent and missing history has no confident pace`() {
        val fresh = CalculateDashboard.calculate(
            input().copy(accountingStartedAt = Instant.parse("2026-08-24T19:00:00Z"))
        )
        assertEquals("UNAVAILABLE", fresh.forecast.estimateSource)
        assertEquals(PaceStatus.InsufficientData, fresh.budgetExecution.total.paceStatus)
        assertNull(fresh.budgetExecution.total.usedPercent)
        assertEquals(FreeMoneyState.NoFreeFunds, fresh.freeMoney.state)
        val complete = CalculateDashboard.calculate(input())
        assertEquals(PaceStatus.OnPlan, complete.budgetExecution.total.paceStatus)
    }

    @Test fun `last day in leap February includes today and retains deficit`() {
        val result = CalculateDashboard.calculate(
            input().copy(
                asOf = Instant.parse("2024-02-28T20:00:00Z"),
                accountingStartedAt = Instant.parse("2024-02-01T00:00:00Z"),
                openingBalance = decimal("10.99"),
                protectedReserve = decimal("11")
            )
        )
        assertEquals(LocalDate.parse("2024-02-29"), result.date)
        assertEquals(1, result.freeMoney.daysRemaining)
        assertEquals(decimal("-0.01"), result.freeMoney.amount.amount)
        assertEquals(decimal("0"), result.freeMoney.safeDailyLimit.amount)
        assertEquals(FreeMoneyState.Deficit, result.freeMoney.state)
    }

    companion object {
        private const val FOOD = "00000000-0000-0000-0000-000000000001"
        private const val TRANSPORT = "00000000-0000-0000-0000-000000000002"
        private const val HOUSING = "00000000-0000-0000-0000-000000000003"
        private const val INCOME = "00000000-0000-0000-0000-000000000004"
        private fun decimal(value: String) = BigDecimal(value).setScale(2)
        private fun money(value: String) = Money(decimal(value), "KZT")
        private fun operation(
            id: String,
            category: String,
            kind: DashboardOperationKind,
            amount: String,
            date: String
        ) = DashboardOperation(
            id,
            category,
            kind,
            decimal(amount),
            LocalDate.parse(date),
            LocalDate.parse(date).atStartOfDay(ZoneId.of("Asia/Almaty")).toInstant()
        )
        private fun input() = DashboardCalculationInput(
            Instant.parse("2026-09-06T03:00:00Z"), Instant.parse("2026-08-01T00:00:00Z"),
            decimal("0"), decimal("0"), decimal("0"), emptyList(),
            listOf(
                DashboardCategory(FOOD, "Продукты", true),
                DashboardCategory(TRANSPORT, "Транспорт", true),
                DashboardCategory(HOUSING, "Жильё", true),
                DashboardCategory(INCOME, "Доход", false)
            ),
            emptyList(), emptyList(), emptySet()
        )
    }
}
