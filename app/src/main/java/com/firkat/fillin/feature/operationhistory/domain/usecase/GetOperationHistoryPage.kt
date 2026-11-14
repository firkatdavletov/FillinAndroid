package com.firkat.fillin.feature.operationhistory.domain.usecase

import com.firkat.fillin.feature.operationhistory.domain.model.OperationHistoryFilter
import com.firkat.fillin.feature.operationhistory.domain.model.OperationPage
import com.firkat.fillin.feature.operationhistory.domain.repository.OperationHistoryRepository

class GetOperationHistoryPage(private val repository: OperationHistoryRepository) {
    suspend operator fun invoke(
        cursor: Long?,
        filter: OperationHistoryFilter
    ): OperationPage = repository.getOperations(
        limit = PAGE_SIZE,
        cursor = cursor,
        filter = filter
    )

    private companion object {
        const val PAGE_SIZE = 20
    }
}
