package com.firkat.fillin.feature.purchasehistory.domain.usecase

import com.firkat.fillin.feature.purchasehistory.domain.model.PurchasePage
import com.firkat.fillin.feature.purchasehistory.domain.repository.PurchaseHistoryRepository

class GetPurchaseHistoryPage(
    private val repository: PurchaseHistoryRepository,
) {
    suspend operator fun invoke(cursor: Long?): PurchasePage = repository.getPurchases(
        limit = PAGE_SIZE,
        cursor = cursor,
    )

    private companion object {
        const val PAGE_SIZE = 20
    }
}
