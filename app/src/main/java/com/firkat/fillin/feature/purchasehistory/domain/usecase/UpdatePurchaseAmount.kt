package com.firkat.fillin.feature.purchasehistory.domain.usecase

import com.firkat.fillin.feature.purchasehistory.domain.model.Purchase
import com.firkat.fillin.feature.purchasehistory.domain.model.PurchaseStatus
import com.firkat.fillin.feature.purchasehistory.domain.repository.PurchaseHistoryException
import com.firkat.fillin.feature.purchasehistory.domain.repository.PurchaseHistoryRepository
import java.math.BigDecimal

class UpdatePurchaseAmount(
    private val repository: PurchaseHistoryRepository,
) {
    suspend operator fun invoke(purchase: Purchase, amount: BigDecimal): Purchase {
        if (
            purchase.status != PurchaseStatus.Posted ||
            amount <= BigDecimal.ZERO ||
            amount.scale() > MAX_FRACTION_DIGITS ||
            amount.compareTo(purchase.originalAmount) == 0
        ) {
            throw PurchaseHistoryException.InvalidRequest
        }

        return repository.updatePurchase(
            id = purchase.id,
            version = purchase.version,
            amount = amount,
        )
    }
}

private const val MAX_FRACTION_DIGITS = 2
