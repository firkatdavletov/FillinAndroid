package com.firkat.fillin.feature.plannedincomes.ui

import com.firkat.fillin.feature.plannedincomes.domain.model.PlannedIncome
import java.math.BigDecimal

internal data class PlannedIncomeTotal(
    val amount: BigDecimal,
    val currency: String,
)

internal fun plannedIncomeTotals(items: List<PlannedIncome>): List<PlannedIncomeTotal> = items
    .groupBy(PlannedIncome::currency)
    .map { (currency, incomes) ->
        PlannedIncomeTotal(
            amount = incomes.fold(BigDecimal.ZERO) { total, income ->
                total + income.remainingAmount
            },
            currency = currency,
        )
    }
