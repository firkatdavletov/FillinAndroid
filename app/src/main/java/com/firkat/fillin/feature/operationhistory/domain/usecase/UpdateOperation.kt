package com.firkat.fillin.feature.operationhistory.domain.usecase

import com.firkat.fillin.feature.operationhistory.domain.model.Operation
import com.firkat.fillin.feature.operationhistory.domain.model.OperationStatus
import com.firkat.fillin.feature.operationhistory.domain.model.OperationSyncStatus
import com.firkat.fillin.feature.operationhistory.domain.repository.OperationHistoryException
import com.firkat.fillin.feature.operationhistory.domain.repository.OperationHistoryRepository
import com.firkat.fillin.feature.operationhistory.domain.repository.OperationUpdate
import java.math.BigDecimal

class UpdateOperation(private val repository: OperationHistoryRepository) {
    suspend operator fun invoke(operation: Operation, update: OperationUpdate): Operation {
        val normalized = update.copy(description = update.description?.trim()?.takeIf(String::isNotEmpty))
        if (operation.status != OperationStatus.Posted || operation.hasOriginalValuation ||
            operation.syncStatus == OperationSyncStatus.Rejected || operation.refundedAmount.signum() != 0 ||
            normalized.amount <= BigDecimal.ZERO || normalized.amount > MAX_AMOUNT || normalized.amount.scale() > 2 ||
            normalized.categoryId.isBlank() || normalized.description.orEmpty().length > 160 ||
            (normalized.amount.compareTo(operation.originalAmount) == 0 && normalized.categoryId == operation.categoryId &&
                normalized.description == operation.description?.trim()?.takeIf(String::isNotEmpty))
        ) throw OperationHistoryException.InvalidRequest
        return repository.updateOperation(operation.id, operation.version, normalized)
    }
}

private val MAX_AMOUNT = BigDecimal("99999999999999999.99")
