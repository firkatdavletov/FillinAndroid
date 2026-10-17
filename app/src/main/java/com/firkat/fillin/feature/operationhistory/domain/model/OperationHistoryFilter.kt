package com.firkat.fillin.feature.operationhistory.domain.model

import java.time.LocalDate

data class OperationHistoryFilter(
    val type: OperationType? = OperationType.Expense,
    val categoryId: String? = null,
    val dateFrom: LocalDate,
    val dateTo: LocalDate,
) {
    init {
        require(!dateFrom.isAfter(dateTo)) { "Start date must not be after end date" }
    }

    companion object {
        fun lastMonth(today: LocalDate) = OperationHistoryFilter(
            dateFrom = today.minusMonths(1),
            dateTo = today,
        )
    }
}
