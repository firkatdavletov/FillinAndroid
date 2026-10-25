package com.firkat.fillin.feature.dashboard.domain.model

import java.math.BigDecimal
import java.time.LocalDate

data class Dashboard(
    val date: LocalDate,
    val availableMoney: Money,
    val freeMoney: FreeMoney,
    val monthActuals: MonthActuals,
    val cashFlow: CashFlow,
    val budgetExecution: BudgetExecution,
    val forecast: Forecast,
    val expenseCategories: List<ExpenseCategory>,
    val upcomingPayments: List<UpcomingPayment>,
    val todayVariableNetExpenses: Money,
    val balanceForecast: BalanceForecastProjection,
)

data class Money(
    val amount: BigDecimal,
    val currency: String,
)

data class FreeMoney(
    val amount: Money,
    val state: FreeMoneyState,
    val remainingObligations: Money,
    val protectedReserve: Money,
    val goalReserve: Money,
    val reservedTotal: Money,
    val daysRemaining: Int,
    val safeDailyLimit: Money,
)

enum class FreeMoneyState {
    Available,
    NoFreeFunds,
    Deficit,
    Unknown,
}

data class MonthActuals(
    val income: Money,
    val expenses: Money,
    val refunds: Money,
    val netExpenses: Money,
)

data class CashFlow(
    val net: Money,
)

data class BudgetExecution(
    val basis: BudgetBasis,
    val total: BudgetProgress,
    val variable: BudgetProgress,
)

enum class BudgetBasis {
    Forecast,
    Unknown,
}

data class BudgetProgress(
    val spent: Money?,
    val plannedAmount: Money?,
    val usedPercent: BigDecimal?,
    val paceStatus: PaceStatus,
)

enum class PaceStatus {
    AheadOfPlan,
    OnPlan,
    BelowPlan,
    InsufficientData,
    Unknown,
}

data class Forecast(
    val throughInclusive: LocalDate,
    val estimateSource: String,
    val base: ForecastScenario,
    val cautious: ForecastScenario?,
)

data class ForecastScenario(
    val expectedIncome: Money,
    val expectedExpenses: Money,
    val endingBalance: Money,
)

data class ExpenseCategory(
    val name: String,
    val amount: Money,
    val sharePercent: BigDecimal,
)

data class UpcomingPayment(
    val categoryName: String,
    val dueOn: LocalDate,
    val remainingAmount: Money,
)
