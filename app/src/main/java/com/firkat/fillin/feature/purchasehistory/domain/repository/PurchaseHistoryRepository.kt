package com.firkat.fillin.feature.purchasehistory.domain.repository

import com.firkat.fillin.feature.purchasehistory.domain.model.PurchasePage

interface PurchaseHistoryRepository {
    suspend fun getPurchases(
        limit: Int,
        cursor: Long?,
    ): PurchasePage
}

sealed class PurchaseHistoryException : Exception() {
    data object MissingApiToken : PurchaseHistoryException()
    data object Unauthorized : PurchaseHistoryException()
    data object Forbidden : PurchaseHistoryException()
    data object Network : PurchaseHistoryException()
    data object InvalidResponse : PurchaseHistoryException()
    data object Unknown : PurchaseHistoryException()
}
