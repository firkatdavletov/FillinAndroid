package com.firkat.fillin.feature.plannedexpenses.ui

import com.firkat.fillin.feature.plannedexpenses.domain.model.PlannedExpense
import java.math.BigDecimal

internal data class PlannedExpenseTotal(
    val amount: BigDecimal,
    val currency: String,
)

internal fun remainingTotals(items: List<PlannedExpense>): List<PlannedExpenseTotal> = items
    .groupBy(PlannedExpense::currency)
    .map { (currency, expenses) ->
        PlannedExpenseTotal(
            amount = expenses.fold(BigDecimal.ZERO) { total, expense ->
                total + expense.remainingAmount
            },
            currency = currency,
        )
    }
