package com.firkat.fillin.feature.operationhistory.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class OperationPageDto(
    val items: List<OperationDto>,
    val nextCursor: Long?,
    val totals: OperationTotalsDto,
)

@Serializable
data class OperationTotalsDto(
    val expenses: MoneyDto,
    val incomes: MoneyDto,
)

@Serializable
data class OperationDto(
    val id: String,
    val type: String,
    val category: OperationCategoryDto,
    val originalAmount: MoneyDto,
    val amountKzt: MoneyDto,
    val occurredOn: String,
    val status: String,
    val version: Long,
    val description: String? = null,
)

@Serializable
data class OperationCategoryDto(
    val id: String,
    val name: String,
)

@Serializable
data class MoneyDto(
    val amount: String,
    val currency: String,
)

@Serializable
data class UpdatePurchaseRequestDto(
    val version: Long,
    val amount: String,
)
