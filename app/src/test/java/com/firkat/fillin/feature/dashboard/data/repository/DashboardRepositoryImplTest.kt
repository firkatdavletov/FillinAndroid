package com.firkat.fillin.feature.dashboard.data.repository

import com.firkat.fillin.feature.dashboard.data.remote.DashboardApi
import com.firkat.fillin.feature.dashboard.data.remote.dto.BudgetExecutionDto
import com.firkat.fillin.feature.dashboard.data.remote.dto.BudgetProgressDto
import com.firkat.fillin.feature.dashboard.data.remote.dto.CashFlowDto
import com.firkat.fillin.feature.dashboard.data.remote.dto.DashboardCategoryDto
import com.firkat.fillin.feature.dashboard.data.remote.dto.DashboardDto
import com.firkat.fillin.feature.dashboard.data.remote.dto.DashboardMoneyDto
import com.firkat.fillin.feature.dashboard.data.remote.dto.ExpenseCategoryDto
import com.firkat.fillin.feature.dashboard.data.remote.dto.ExpenseStructureDto
import com.firkat.fillin.feature.dashboard.data.remote.dto.ForecastDto
import com.firkat.fillin.feature.dashboard.data.remote.dto.ForecastScenarioDto
import com.firkat.fillin.feature.dashboard.data.remote.dto.FreeMoneyDto
import com.firkat.fillin.feature.dashboard.data.remote.dto.MonthActualsDto
import com.firkat.fillin.feature.dashboard.data.remote.dto.UpcomingPaymentDto
import com.firkat.fillin.feature.dashboard.domain.model.BudgetBasis
import com.firkat.fillin.feature.dashboard.domain.model.FreeMoneyState
import com.firkat.fillin.feature.dashboard.domain.model.PaceStatus
import com.firkat.fillin.feature.dashboard.domain.repository.DashboardException
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class DashboardRepositoryImplTest {
    @Test
    fun `maps dashboard without losing decimal precision`() = runBlocking {
        val api = RecordingDashboardApi(response())
        val repository = DashboardRepositoryImpl(
            api = api,
            apiToken = "test-token",
        )

        val result = repository.getDashboard()

        assertEquals("Bearer test-token", api.authorization)
        assertEquals(LocalDate.of(2026, 10, 8), result.date)
        assertEquals(BigDecimal("620000.00"), result.availableMoney.amount)
        assertEquals(BigDecimal("280000.00"), result.freeMoney.amount.amount)
        assertEquals(FreeMoneyState.Available, result.freeMoney.state)
        assertEquals(BigDecimal("530000.00"), result.monthActuals.netExpenses.amount)
        assertEquals(BudgetBasis.Forecast, result.budgetExecution.basis)
        assertEquals(BigDecimal("44.2"), result.budgetExecution.total.usedPercent)
        assertEquals(PaceStatus.OnPlan, result.budgetExecution.variable.paceStatus)
        assertEquals("Жильё", result.expenseCategories.single().name)
        assertEquals(BigDecimal("45.3"), result.expenseCategories.single().sharePercent)
        assertEquals(LocalDate.of(2026, 10, 12), result.upcomingPayments.single().dueOn)
        assertEquals(BigDecimal("55000.00"), result.upcomingPayments.single().remainingAmount.amount)
    }

    @Test
    fun `rejects missing token before network request`() {
        val api = RecordingDashboardApi(response())
        val repository = DashboardRepositoryImpl(api = api, apiToken = "")

        val exception = assertThrows(DashboardException.MissingApiToken::class.java) {
            runBlocking { repository.getDashboard() }
        }

        assertSame(DashboardException.MissingApiToken, exception)
        assertEquals(0, api.callCount)
    }

    @Test
    fun `rejects malformed decimal strings`() {
        val invalid = response().copy(
            availableMoney = money("not-a-number"),
        )
        val repository = DashboardRepositoryImpl(
            api = RecordingDashboardApi(invalid),
            apiToken = "test-token",
        )

        val exception = assertThrows(DashboardException.InvalidResponse::class.java) {
            runBlocking { repository.getDashboard() }
        }

        assertSame(DashboardException.InvalidResponse, exception)
    }

    private fun response(): DashboardDto = DashboardDto(
        date = "2026-10-08",
        availableMoney = money("620000.00"),
        freeMoney = FreeMoneyDto(
            amount = money("280000.00"),
            state = "AVAILABLE",
            remainingObligations = money("200000.00"),
            protectedReserve = money("100000.00"),
            goalReserve = money("40000.00"),
            reservedTotal = money("340000.00"),
            daysRemaining = 24,
            safeDailyLimit = money("11666.00"),
        ),
        monthActuals = MonthActualsDto(
            income = money("850000.00"),
            expenses = money("550000.00"),
            refunds = money("20000.00"),
            netExpenses = money("530000.00"),
        ),
        cashFlow = CashFlowDto(net = money("320000.00")),
        budgetExecution = BudgetExecutionDto(
            basis = "FORECAST",
            total = BudgetProgressDto(
                spent = money("530000.00"),
                plannedAmount = money("1200000.00"),
                usedPercent = "44.2",
                paceStatus = "AHEAD_OF_PLAN",
            ),
            variable = BudgetProgressDto(
                usedPercent = "25.8",
                paceStatus = "ON_PLAN",
            ),
        ),
        forecast = ForecastDto(
            throughInclusive = "2026-10-31",
            estimateSource = "HISTORICAL_AVERAGE",
            base = scenario(),
            cautious = scenario(),
        ),
        expenseStructure = ExpenseStructureDto(
            categories = listOf(
                ExpenseCategoryDto(
                    name = "Жильё",
                    amount = money("240000.00"),
                    sharePercent = "45.3",
                ),
            ),
        ),
        upcomingPayments = listOf(
            UpcomingPaymentDto(
                category = DashboardCategoryDto(name = "Кредит"),
                dueOn = "2026-10-12",
                remainingAmount = money("55000.00"),
            ),
        ),
    )

    private fun scenario() = ForecastScenarioDto(
        expectedIncome = money("100000.00"),
        expectedExpenses = money("670000.00"),
        endingBalance = money("50000.00"),
    )

    private fun money(amount: String) = DashboardMoneyDto(
        amount = amount,
        currency = "KZT",
    )
}

private class RecordingDashboardApi(
    private val response: DashboardDto,
) : DashboardApi {
    var authorization: String? = null
    var callCount: Int = 0

    override suspend fun getDashboard(authorization: String): DashboardDto {
        callCount += 1
        this.authorization = authorization
        return response
    }
}
