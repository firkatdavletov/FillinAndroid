package com.firkat.fillin.feature.operationhistory.domain.model

import java.math.BigDecimal

/** Period totals in KZT, using the API's converted operation amounts. */
data class OperationPeriodTotals(
    val expenses: BigDecimal = BigDecimal.ZERO,
    val incomes: BigDecimal = BigDecimal.ZERO,
)
