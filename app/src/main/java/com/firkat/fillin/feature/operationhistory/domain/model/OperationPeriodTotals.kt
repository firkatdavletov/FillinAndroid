package com.firkat.fillin.feature.operationhistory.domain.model

import java.math.BigDecimal

/** Server totals for the entire period in KZT; expenses include refunds. */
data class OperationPeriodTotals(
    val expenses: BigDecimal = BigDecimal.ZERO,
    val incomes: BigDecimal = BigDecimal.ZERO,
)
