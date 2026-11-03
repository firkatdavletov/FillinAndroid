package com.firkat.fillin.feature.dashboard.domain.calculation

import com.firkat.fillin.feature.dashboard.domain.model.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID

/** A single local snapshot. All amounts are the persisted KZT valuations. */
data class DashboardCalculationInput(
    val asOf: Instant,
    val accountingStartedAt: Instant,
    val openingBalance: BigDecimal,
    val protectedReserve: BigDecimal,
    val goalReserve: BigDecimal,
    val operations: List<DashboardOperation>,
    val categories: List<DashboardCategory>,
    val payments: List<DashboardPayment>,
    val incomes: List<DashboardExpectedIncome>,
    val excludedVariableExpenseIds: Set<String>,
    val knownExpenseIds: Set<String> = operations.filter {
        it.kind == DashboardOperationKind.Expense
    }.mapTo(hashSetOf()) { it.id },
    val incomeWarnings: Set<com.firkat.fillin.feature.dailylimit.domain.ForecastWarning> = emptySet(),
)

enum class DashboardOperationKind { Income, Expense, Refund }

data class DashboardOperation(
    val id: String,
    val categoryId: String,
    val kind: DashboardOperationKind,
    val amount: BigDecimal,
    val occurredOn: LocalDate,
    val occurredAt: Instant,
    val refundOf: String? = null
)

data class DashboardCategory(val id: String, val name: String, val isExpense: Boolean)
data class DashboardPayment(val id: String, val categoryName: String, val dueOn: LocalDate, val remaining: BigDecimal)
data class DashboardExpectedIncome(val dueOn: LocalDate, val amount: BigDecimal, val id: String = dueOn.toString(), val name: String = "")

/** Mirrors DashboardCalculator, BudgetCalculator and the summary fields of BudgetForecastCalculator. */
object CalculateDashboard {
    private val zone = ZoneId.of("Asia/Almaty")

    fun calculate(input: DashboardCalculationInput): Dashboard {
        val today = input.asOf.atZone(zone).toLocalDate()
        val monthStart = today.withDayOfMonth(1)
        val monthEnd = YearMonth.from(today).atEndOfMonth()
        val days = monthEnd.dayOfMonth - today.dayOfMonth + 1
        val categories = input.categories.associateBy { it.id }
        val month = input.operations.filter { it.occurredOn in monthStart..today }
        val income = month.filter { it.kind == DashboardOperationKind.Income }.sumOf { it.amount }
        val expenses = month.filter {
            it.kind == DashboardOperationKind.Expense &&
                categories[it.categoryId]?.isExpense == true
        }.sumOf { it.amount }
        val refunds = month.filter {
            it.kind == DashboardOperationKind.Refund &&
                categories[it.categoryId]?.isExpense == true
        }.sumOf { it.amount }
        val net = expenses - refunds
        val balance = input.openingBalance + input.operations.filter { it.occurredAt <= input.asOf }.sumOf {
            if (it.kind == DashboardOperationKind.Expense) -it.amount else it.amount
        }
        val obligations = input.payments.filter { it.dueOn <= monthEnd }.sumOf { it.remaining }
        val reserved = obligations + input.protectedReserve + input.goalReserve
        val free = balance - reserved
        val historyStart = maxOf(today.minusDays(30), input.accountingStartedAt.atZone(zone).toLocalDate())
        val historyDays = ChronoUnit.DAYS.between(historyStart, today).toInt().coerceAtLeast(0)
        val originalIds = input.knownExpenseIds
        fun variableNet(operations: List<DashboardOperation>): BigDecimal = operations.sumOf {
            when {
                it.kind == DashboardOperationKind.Expense && it.id !in input.excludedVariableExpenseIds -> it.amount

                it.kind == DashboardOperationKind.Refund && it.refundOf in originalIds &&
                    it.refundOf !in input.excludedVariableExpenseIds -> -it.amount

                else -> BigDecimal.ZERO
            }
        }
        val todayVariableNet = variableNet(input.operations.filter {
            it.occurredOn == today && it.occurredAt <= input.asOf
        })
        val complete = historyDays >= 14
        val daily = if (complete) {
            variableNet(input.operations.filter { it.occurredOn >= historyStart && it.occurredOn < today })
                .max(BigDecimal.ZERO)
                .divide(BigDecimal(historyDays), 2, RoundingMode.HALF_UP)
        } else {
            BigDecimal.ZERO.setScale(2)
        }
        val cautiousDaily = (daily * BigDecimal("1.20")).setScale(2, RoundingMode.CEILING)
        fun scenario(dailySpending: BigDecimal, incomeDelay: Long): ForecastScenario {
            val expectedIncome = input.incomes.filter {
                it.dueOn >= today && it.dueOn.plusDays(incomeDelay) <= monthEnd
            }.sumOf { it.amount }
            val expectedExpenses = obligations + dailySpending * BigDecimal(days)
            return ForecastScenario(
                money(expectedIncome),
                money(expectedExpenses),
                money(balance + expectedIncome - expectedExpenses)
            )
        }
        val base = scenario(daily, 0)
        val spent = net.max(BigDecimal.ZERO)
        val variableSpent = variableNet(month).max(BigDecimal.ZERO)
        val categoryAmounts = month.filter {
            it.kind != DashboardOperationKind.Income &&
                categories[it.categoryId]?.isExpense == true
        }
            .groupBy { it.categoryId }.mapValues { (_, items) ->
                items.sumOf { if (it.kind == DashboardOperationKind.Refund) -it.amount else it.amount }
            }.filterValues { it.signum() > 0 }
        val shareBasis = categoryAmounts.values.fold(BigDecimal.ZERO, BigDecimal::add)
        val topCategories = categoryAmounts.entries.sortedWith(
            compareByDescending<Map.Entry<String, BigDecimal>> { it.value }
                .thenBy { categories.getValue(it.key).name }
                .thenBy { UUID.fromString(it.key) }
        ).take(5).map {
            ExpenseCategory(categories.getValue(it.key).name, money(it.value), percent(it.value, shareBasis))
        }
        val cashFlowForecast = com.firkat.fillin.feature.dailylimit.domain.CashFlowForecaster().forecast(
                com.firkat.fillin.feature.dailylimit.domain.DailyLimitInput(
                    money(balance), money(todayVariableNet),
                    input.incomes.map { com.firkat.fillin.feature.dailylimit.domain.ForecastEvent(it.id, it.name, it.dueOn, money(it.amount), true) } +
                        input.payments.map { com.firkat.fillin.feature.dailylimit.domain.ForecastEvent(it.id, it.categoryName, it.dueOn, money(it.remaining), false) },
                    today, warnings = input.incomeWarnings,
                ),
                averageDailySpending = if (complete) money(daily) else null,
            )
        val dailyLimit = cashFlowForecast.dailyLimit
        return Dashboard(
            today,
            money(balance),
            FreeMoney(
                money(free),
                when (free.signum()) {
                    -1 -> FreeMoneyState.Deficit
                    0 -> FreeMoneyState.NoFreeFunds
                    else -> FreeMoneyState.Available
                },
                money(obligations),
                money(input.protectedReserve),
                money(input.goalReserve),
                money(reserved),
                days,
                dailyLimit.dailyTarget
            ),
            MonthActuals(money(income), money(expenses), money(refunds), money(net)),
            CashFlow(money(income - net)),
            BudgetExecution(
                BudgetBasis.Forecast,
                progress(spent, spent + base.expectedExpenses.amount, today, complete),
                progress(variableSpent, variableSpent + daily * BigDecimal(days), today, complete)
            ),
            Forecast(monthEnd, if (complete) "HISTORICAL_AVERAGE" else "UNAVAILABLE", base, scenario(cautiousDaily, 3)),
            topCategories,
            input.payments.sortedWith(compareBy({ it.dueOn }, { it.id })).take(5).map {
                UpcomingPayment(it.categoryName, it.dueOn, money(it.remaining))
            },
            money(todayVariableNet),
            cashFlowForecast.projection,
            dailyLimit,
        )
    }

    private fun progress(spent: BigDecimal, planned: BigDecimal, date: LocalDate, complete: Boolean): BudgetProgress {
        val comparison = (spent * BigDecimal(date.lengthOfMonth())).compareTo(planned * BigDecimal(date.dayOfMonth))
        val pace = when {
            !complete -> PaceStatus.InsufficientData
            planned.signum() == 0 -> PaceStatus.OnPlan
            comparison > 0 -> PaceStatus.AheadOfPlan
            comparison < 0 -> PaceStatus.BelowPlan
            else -> PaceStatus.OnPlan
        }
        return BudgetProgress(
            money(spent),
            money(planned),
            planned.takeIf {
                it.signum() > 0
            }?.let { percent(spent, it) },
            pace
        )
    }

    private fun money(amount: BigDecimal) = Money(amount.setScale(2), "KZT")
    private fun percent(numerator: BigDecimal, denominator: BigDecimal) =
        numerator.multiply(BigDecimal(100)).divide(denominator, 1, RoundingMode.HALF_UP)
}
