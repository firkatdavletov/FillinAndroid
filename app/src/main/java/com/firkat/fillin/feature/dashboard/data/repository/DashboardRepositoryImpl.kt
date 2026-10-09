package com.firkat.fillin.feature.dashboard.data.repository

import com.firkat.fillin.feature.dashboard.data.remote.DashboardApi
import com.firkat.fillin.feature.dashboard.data.remote.dto.BudgetProgressDto
import com.firkat.fillin.feature.dashboard.data.remote.dto.DashboardDto
import com.firkat.fillin.feature.dashboard.data.remote.dto.DashboardMoneyDto
import com.firkat.fillin.feature.dashboard.data.remote.dto.ForecastScenarioDto
import com.firkat.fillin.feature.dashboard.domain.model.BudgetBasis
import com.firkat.fillin.feature.dashboard.domain.model.BudgetExecution
import com.firkat.fillin.feature.dashboard.domain.model.BudgetProgress
import com.firkat.fillin.feature.dashboard.domain.model.CashFlow
import com.firkat.fillin.feature.dashboard.domain.model.Dashboard
import com.firkat.fillin.feature.dashboard.domain.model.ExpenseCategory
import com.firkat.fillin.feature.dashboard.domain.model.Forecast
import com.firkat.fillin.feature.dashboard.domain.model.ForecastScenario
import com.firkat.fillin.feature.dashboard.domain.model.FreeMoney
import com.firkat.fillin.feature.dashboard.domain.model.FreeMoneyState
import com.firkat.fillin.feature.dashboard.domain.model.Money
import com.firkat.fillin.feature.dashboard.domain.model.MonthActuals
import com.firkat.fillin.feature.dashboard.domain.model.PaceStatus
import com.firkat.fillin.feature.dashboard.domain.model.UpcomingPayment
import com.firkat.fillin.feature.dashboard.domain.repository.DashboardException
import com.firkat.fillin.feature.dashboard.domain.repository.DashboardRepository
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeParseException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import retrofit2.HttpException

class DashboardRepositoryImpl(
    private val api: DashboardApi,
    private val apiToken: String,
) : DashboardRepository {
    override suspend fun getDashboard(): Dashboard {
        if (apiToken.isBlank()) throw DashboardException.MissingApiToken

        return try {
            api.getDashboard(authorization = "Bearer $apiToken").toDomain()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: HttpException) {
            throw when (exception.code()) {
                401 -> DashboardException.Unauthorized
                403 -> DashboardException.Forbidden
                else -> DashboardException.Unknown
            }
        } catch (exception: IOException) {
            throw DashboardException.Network
        } catch (exception: NumberFormatException) {
            throw DashboardException.InvalidResponse
        } catch (exception: DateTimeParseException) {
            throw DashboardException.InvalidResponse
        } catch (exception: SerializationException) {
            throw DashboardException.InvalidResponse
        }
    }
}

private fun DashboardDto.toDomain(): Dashboard = Dashboard(
    date = LocalDate.parse(date),
    availableMoney = availableMoney.toDomain(),
    freeMoney = FreeMoney(
        amount = freeMoney.amount.toDomain(),
        state = when (freeMoney.state) {
            "AVAILABLE" -> FreeMoneyState.Available
            "DEFICIT" -> FreeMoneyState.Deficit
            else -> FreeMoneyState.Unknown
        },
        remainingObligations = freeMoney.remainingObligations.toDomain(),
        protectedReserve = freeMoney.protectedReserve.toDomain(),
        goalReserve = freeMoney.goalReserve.toDomain(),
        reservedTotal = freeMoney.reservedTotal.toDomain(),
        daysRemaining = freeMoney.daysRemaining,
        safeDailyLimit = freeMoney.safeDailyLimit.toDomain(),
    ),
    monthActuals = MonthActuals(
        income = monthActuals.income.toDomain(),
        expenses = monthActuals.expenses.toDomain(),
        refunds = monthActuals.refunds.toDomain(),
        netExpenses = monthActuals.netExpenses.toDomain(),
    ),
    cashFlow = CashFlow(net = cashFlow.net.toDomain()),
    budgetExecution = BudgetExecution(
        basis = when (budgetExecution.basis) {
            "FORECAST" -> BudgetBasis.Forecast
            else -> BudgetBasis.Unknown
        },
        total = budgetExecution.total.toDomain(),
        variable = budgetExecution.variable.toDomain(),
    ),
    forecast = Forecast(
        throughInclusive = LocalDate.parse(forecast.throughInclusive),
        estimateSource = forecast.estimateSource,
        base = forecast.base.toDomain(),
        cautious = (forecast.cautious ?: forecast.conservative)?.toDomain(),
    ),
    expenseCategories = expenseStructure.categories.map { category ->
        ExpenseCategory(
            name = category.name,
            amount = category.amount.toDomain(),
            sharePercent = category.sharePercent.toBigDecimal(),
        )
    },
    upcomingPayments = upcomingPayments.map { payment ->
        UpcomingPayment(
            categoryName = payment.category.name,
            dueOn = LocalDate.parse(payment.dueOn),
            remainingAmount = payment.remainingAmount.toDomain(),
        )
    },
)

private fun DashboardMoneyDto.toDomain(): Money = Money(
    amount = amount.toBigDecimal(),
    currency = currency,
)

private fun BudgetProgressDto.toDomain(): BudgetProgress = BudgetProgress(
    spent = spent?.toDomain(),
    plannedAmount = plannedAmount?.toDomain(),
    usedPercent = usedPercent.toBigDecimal(),
    paceStatus = when (paceStatus) {
        "AHEAD_OF_PLAN" -> PaceStatus.AheadOfPlan
        "ON_PLAN" -> PaceStatus.OnPlan
        "BELOW_PLAN" -> PaceStatus.BelowPlan
        "INSUFFICIENT_DATA" -> PaceStatus.InsufficientData
        else -> PaceStatus.Unknown
    },
)

private fun ForecastScenarioDto.toDomain(): ForecastScenario = ForecastScenario(
    expectedIncome = expectedIncome.toDomain(),
    expectedExpenses = expectedExpenses.toDomain(),
    endingBalance = endingBalance.toDomain(),
)
