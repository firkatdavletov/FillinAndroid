package com.firkat.fillin.feature.purchasehistory.domain.repository

import com.firkat.fillin.feature.purchasehistory.domain.model.Purchase
import com.firkat.fillin.feature.purchasehistory.domain.model.PurchasePage
import java.math.BigDecimal

interface PurchaseHistoryRepository {
    suspend fun getPurchases(
        limit: Int,
        cursor: Long?,
    ): PurchasePage

    suspend fun updatePurchase(
        id: String,
        version: Long,
        amount: BigDecimal,
    ): Purchase

    suspend fun refundPurchase(id: String, version: Long)
}

sealed class PurchaseHistoryException : Exception() {
    data object MissingApiToken : PurchaseHistoryException()
    data object Unauthorized : PurchaseHistoryException()
    data object Forbidden : PurchaseHistoryException()
    data object InvalidRequest : PurchaseHistoryException()
    data object NotFound : PurchaseHistoryException()
    data object Conflict : PurchaseHistoryException()
    data object Network : PurchaseHistoryException()
    data object InvalidResponse : PurchaseHistoryException()
    data object Unknown : PurchaseHistoryException()
}
