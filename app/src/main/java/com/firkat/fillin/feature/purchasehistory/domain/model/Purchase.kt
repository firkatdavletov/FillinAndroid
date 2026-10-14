package com.firkat.fillin.feature.purchasehistory.domain.model

import java.math.BigDecimal
import java.time.LocalDate

data class Purchase(
    val id: String,
    val categoryName: String,
    val description: String?,
    val amount: BigDecimal,
    val currency: String,
    val originalAmount: BigDecimal,
    val originalCurrency: String,
    val occurredOn: LocalDate,
    val status: PurchaseStatus,
    val version: Long,
)

enum class PurchaseStatus {
    Posted,
    Voided,
    Unknown,
}

data class PurchasePage(
    val items: List<Purchase>,
    val nextCursor: Long?,
)
