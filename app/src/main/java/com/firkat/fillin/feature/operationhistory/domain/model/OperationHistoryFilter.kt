package com.firkat.fillin.feature.operationhistory.domain.model

import java.time.LocalDate

data class OperationHistoryFilter(
    val type: OperationType? = null,
    val categoryId: String? = null,
    val dateFrom: LocalDate,
    val dateTo: LocalDate
) {
    init {
        require(!dateFrom.isAfter(dateTo)) { "Start date must not be after end date" }
    }

    companion object {
        fun currentMonth(
            today: LocalDate
        ) = OperationHistoryFilter(
            dateFrom = today.withDayOfMonth(1),
            dateTo = today
        )
    }
}
