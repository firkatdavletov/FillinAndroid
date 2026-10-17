package com.firkat.fillin.feature.operationhistory.domain.usecase

import com.firkat.fillin.feature.operationhistory.domain.model.Operation
import com.firkat.fillin.feature.operationhistory.domain.model.OperationStatus
import com.firkat.fillin.feature.operationhistory.domain.model.OperationType
import com.firkat.fillin.feature.operationhistory.domain.repository.OperationHistoryException
import com.firkat.fillin.feature.operationhistory.domain.repository.OperationHistoryRepository

class RefundPurchase(
    private val repository: OperationHistoryRepository,
) {
    suspend operator fun invoke(purchase: Operation) {
        if (purchase.status != OperationStatus.Posted || purchase.type != OperationType.Expense) {
            throw OperationHistoryException.InvalidRequest
        }
        repository.refundPurchase(id = purchase.id, version = purchase.version)
    }
}
