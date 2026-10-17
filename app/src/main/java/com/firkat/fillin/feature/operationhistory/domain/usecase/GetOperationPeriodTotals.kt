package com.firkat.fillin.feature.operationhistory.domain.usecase

import com.firkat.fillin.feature.operationhistory.domain.model.OperationHistoryFilter
import com.firkat.fillin.feature.operationhistory.domain.model.OperationPeriodTotals
import com.firkat.fillin.feature.operationhistory.domain.model.OperationStatus
import com.firkat.fillin.feature.operationhistory.domain.model.OperationType
import com.firkat.fillin.feature.operationhistory.domain.repository.OperationHistoryException
import com.firkat.fillin.feature.operationhistory.domain.repository.OperationHistoryRepository
import java.time.LocalDate
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

class GetOperationPeriodTotals(
    private val repository: OperationHistoryRepository,
) {
    suspend operator fun invoke(dateFrom: LocalDate, dateTo: LocalDate): OperationPeriodTotals {
        val filter = OperationHistoryFilter(type = null, dateFrom = dateFrom, dateTo = dateTo)
        val visitedCursors = mutableSetOf<Long?>()
        val operationIds = mutableSetOf<String>()
        var cursor: Long? = null
        var totals = OperationPeriodTotals()
        // The history endpoint has no aggregate fields, so totals must include every page.
        do {
            if (!visitedCursors.add(cursor)) throw OperationHistoryException.InvalidResponse
            val page = repository.getOperations(limit = 50, cursor = cursor, filter = filter)
            currentCoroutineContext().ensureActive()
            for (operation in page.items) {
                if (!operationIds.add(operation.id) || operation.status != OperationStatus.Posted) continue
                totals = when (operation.type) {
                    OperationType.Expense -> totals.copy(expenses = totals.expenses + operation.amount)
                    OperationType.Income -> totals.copy(incomes = totals.incomes + operation.amount)
                }
            }
            cursor = page.nextCursor
        } while (cursor != null)
        return totals
    }
}
