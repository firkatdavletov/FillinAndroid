package com.firkat.fillin.feature.operationhistory.domain.model

import java.math.BigDecimal

/** Totals for the entire period in the budget currency; expenses include refunds. */
data class OperationPeriodTotals(
    val expenses: BigDecimal = BigDecimal.ZERO,
    val incomes: BigDecimal = BigDecimal.ZERO,
    val currency: String = "KZT"
)
