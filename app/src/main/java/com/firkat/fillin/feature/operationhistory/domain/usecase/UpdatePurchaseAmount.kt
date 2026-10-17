package com.firkat.fillin.feature.operationhistory.domain.usecase

import com.firkat.fillin.feature.operationhistory.domain.model.Operation
import com.firkat.fillin.feature.operationhistory.domain.model.OperationStatus
import com.firkat.fillin.feature.operationhistory.domain.model.OperationType
import com.firkat.fillin.feature.operationhistory.domain.repository.OperationHistoryException
import com.firkat.fillin.feature.operationhistory.domain.repository.OperationHistoryRepository
import java.math.BigDecimal

class UpdatePurchaseAmount(
    private val repository: OperationHistoryRepository,
) {
    suspend operator fun invoke(purchase: Operation, amount: BigDecimal): Operation {
        if (
            purchase.type != OperationType.Expense ||
            purchase.status != OperationStatus.Posted ||
            amount <= BigDecimal.ZERO ||
            amount.scale() > MAX_FRACTION_DIGITS ||
            amount.compareTo(purchase.originalAmount) == 0
        ) {
            throw OperationHistoryException.InvalidRequest
        }

        return repository.updatePurchase(
            id = purchase.id,
            version = purchase.version,
            amount = amount,
        )
    }
}

private const val MAX_FRACTION_DIGITS = 2
