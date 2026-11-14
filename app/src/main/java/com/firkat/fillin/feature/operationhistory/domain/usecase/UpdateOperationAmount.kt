package com.firkat.fillin.feature.operationhistory.domain.usecase

import com.firkat.fillin.feature.operationhistory.domain.model.Operation
import com.firkat.fillin.feature.operationhistory.domain.model.OperationStatus
import com.firkat.fillin.feature.operationhistory.domain.repository.OperationHistoryException
import com.firkat.fillin.feature.operationhistory.domain.repository.OperationHistoryRepository
import java.math.BigDecimal

class UpdateOperationAmount(private val repository: OperationHistoryRepository) {
    suspend operator fun invoke(
        operation: Operation,
        amount: BigDecimal
    ): Operation {
        if (
            operation.status != OperationStatus.Posted ||
            operation.hasOriginalValuation ||
            operation.refundedAmount.signum() != 0 ||
            amount <= BigDecimal.ZERO ||
            amount > MAX_AMOUNT ||
            amount.scale() > MAX_FRACTION_DIGITS ||
            amount.compareTo(operation.originalAmount) == 0
        ) {
            throw OperationHistoryException.InvalidRequest
        }

        return repository.updateOperationAmount(
            id = operation.id,
            version = operation.version,
            amount = amount
        )
    }
}

private const val MAX_FRACTION_DIGITS = 2
private val MAX_AMOUNT = BigDecimal("99999999999999999.99")
