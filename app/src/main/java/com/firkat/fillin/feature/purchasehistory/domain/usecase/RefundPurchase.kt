package com.firkat.fillin.feature.purchasehistory.domain.usecase

import com.firkat.fillin.feature.purchasehistory.domain.model.Purchase
import com.firkat.fillin.feature.purchasehistory.domain.model.PurchaseStatus
import com.firkat.fillin.feature.purchasehistory.domain.repository.PurchaseHistoryException
import com.firkat.fillin.feature.purchasehistory.domain.repository.PurchaseHistoryRepository

class RefundPurchase(
    private val repository: PurchaseHistoryRepository,
) {
    suspend operator fun invoke(purchase: Purchase) {
        if (purchase.status != PurchaseStatus.Posted) {
            throw PurchaseHistoryException.InvalidRequest
        }
        repository.refundPurchase(id = purchase.id, version = purchase.version)
    }
}
