package com.firkat.fillin.feature.operationhistory.domain.repository

import com.firkat.fillin.feature.operationhistory.domain.model.Operation
import com.firkat.fillin.feature.operationhistory.domain.model.OperationHistoryFilter
import com.firkat.fillin.feature.operationhistory.domain.model.OperationPage
import java.math.BigDecimal

interface OperationHistoryRepository {
    suspend fun getOperations(
        limit: Int,
        cursor: Long?,
        filter: OperationHistoryFilter,
    ): OperationPage

    /** Full operation editing is supported by the local v4 command store. */
    suspend fun updateOperation(id: String, version: Long, update: OperationUpdate): Operation =
        throw OperationHistoryException.InvalidRequest

    suspend fun updateOperationAmount(
        id: String,
        version: Long,
        amount: BigDecimal,
    ): Operation

    suspend fun refundPurchase(id: String, version: Long)
}

data class OperationUpdate(
    val amount: BigDecimal,
    val categoryId: String,
    val description: String?,
)

sealed class OperationHistoryException : Exception() {
    data object MissingApiToken : OperationHistoryException()
    data object Unauthorized : OperationHistoryException()
    data object Forbidden : OperationHistoryException()
    data object InvalidRequest : OperationHistoryException()
    data object NotFound : OperationHistoryException()
    data object Conflict : OperationHistoryException()
    data object Network : OperationHistoryException()
    data object InvalidResponse : OperationHistoryException()
    data object UnlinkBeforeCategoryChange : OperationHistoryException()
    data object Unknown : OperationHistoryException()
}
