package com.firkat.fillin.feature.purchasehistory.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class PurchasePageDto(
    val items: List<PurchaseDto>,
    val nextCursor: Long?,
)

@Serializable
data class PurchaseDto(
    val id: String,
    val category: PurchaseCategoryDto,
    val originalAmount: MoneyDto,
    val amountKzt: MoneyDto,
    val occurredOn: String,
    val status: String,
    val description: String? = null,
)

@Serializable
data class PurchaseCategoryDto(
    val id: String,
    val name: String,
)

@Serializable
data class MoneyDto(
    val amount: String,
    val currency: String,
)
