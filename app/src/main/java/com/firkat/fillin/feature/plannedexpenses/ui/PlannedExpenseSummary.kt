package com.firkat.fillin.feature.plannedexpenses.ui

import com.firkat.fillin.feature.plannedexpenses.domain.model.PlannedExpense
import java.math.BigDecimal
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

internal data class PlannedExpenseTotal(val amount: BigDecimal, val currency: String)

internal fun remainingTotals(
    items: List<PlannedExpense>
): List<PlannedExpenseTotal> = items
    .groupBy(PlannedExpense::currency)
    .map { (currency, expenses) ->
        PlannedExpenseTotal(
            amount = expenses.fold(BigDecimal.ZERO) { total, expense ->
                total + expense.remainingAmount
            },
            currency = currency
        )
    }

internal fun plannedExpensesForPeriod(
    items: List<PlannedExpense>,
    asOf: LocalDate?,
    throughInclusive: LocalDate?,
    period: PlannedExpensesPeriod
): List<PlannedExpense> {
    val periodEnd = plannedExpensesPeriodEnd(
        asOf = asOf,
        throughInclusive = throughInclusive,
        period = period
    ) ?: return items
    return items.filter { !it.dueOn.isAfter(periodEnd) }
}

internal fun plannedExpensesPeriodEnd(
    asOf: LocalDate?,
    throughInclusive: LocalDate?,
    period: PlannedExpensesPeriod
): LocalDate? = when (period) {
    PlannedExpensesPeriod.EndOfMonth ->
        asOf
            ?.with(TemporalAdjusters.lastDayOfMonth())
            ?.let { monthEnd ->
                throughInclusive?.let { minOf(monthEnd, it) } ?: monthEnd
            }
    PlannedExpensesPeriod.EndOfYear -> throughInclusive
}
