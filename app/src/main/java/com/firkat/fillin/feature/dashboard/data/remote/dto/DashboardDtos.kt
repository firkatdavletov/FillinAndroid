package com.firkat.fillin.feature.dashboard.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DashboardDto(
    val date: String,
    val availableMoney: DashboardMoneyDto,
    val freeMoney: FreeMoneyDto,
    val monthActuals: MonthActualsDto,
    val cashFlow: CashFlowDto,
    val budgetExecution: BudgetExecutionDto,
    val forecast: ForecastDto,
    val expenseStructure: ExpenseStructureDto,
    val upcomingPayments: List<UpcomingPaymentDto> = emptyList(),
)

@Serializable
data class DashboardMoneyDto(
    val amount: String,
    val currency: String,
)

@Serializable
data class FreeMoneyDto(
    val amount: DashboardMoneyDto,
    val state: String,
    val remainingObligations: DashboardMoneyDto,
    val protectedReserve: DashboardMoneyDto,
    val goalReserve: DashboardMoneyDto,
    val reservedTotal: DashboardMoneyDto,
    val daysRemaining: Int,
    val safeDailyLimit: DashboardMoneyDto,
)

@Serializable
data class MonthActualsDto(
    val income: DashboardMoneyDto,
    val expenses: DashboardMoneyDto,
    val refunds: DashboardMoneyDto,
    val netExpenses: DashboardMoneyDto,
)

@Serializable
data class CashFlowDto(
    val net: DashboardMoneyDto,
)

@Serializable
data class BudgetExecutionDto(
    val basis: String,
    val total: BudgetProgressDto,
    val variable: BudgetProgressDto,
)

@Serializable
data class BudgetProgressDto(
    val spent: DashboardMoneyDto? = null,
    val plannedAmount: DashboardMoneyDto? = null,
    val usedPercent: String,
    val paceStatus: String,
)

@Serializable
data class ForecastDto(
    val throughInclusive: String,
    val estimateSource: String,
    val base: ForecastScenarioDto,
    val cautious: ForecastScenarioDto? = null,
    @SerialName("conservative")
    val conservative: ForecastScenarioDto? = null,
)

@Serializable
data class ForecastScenarioDto(
    val expectedIncome: DashboardMoneyDto,
    val expectedExpenses: DashboardMoneyDto,
    val endingBalance: DashboardMoneyDto,
)

@Serializable
data class ExpenseStructureDto(
    val categories: List<ExpenseCategoryDto> = emptyList(),
)

@Serializable
data class ExpenseCategoryDto(
    val name: String,
    val amount: DashboardMoneyDto,
    val sharePercent: String,
)

@Serializable
data class UpcomingPaymentDto(
    val category: DashboardCategoryDto,
    val dueOn: String,
    val remainingAmount: DashboardMoneyDto,
)

@Serializable
data class DashboardCategoryDto(
    val name: String,
)
