package com.firkat.fillin.feature.plannedincomes.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class PlannedIncomesResponseDto(
    val asOf: String,
    val items: List<PlannedIncomeDto>,
)

@Serializable
data class PlannedIncomeDto(
    val id: String,
    val category: PlannedIncomeCategoryDto,
    val plannedAmount: PlannedIncomeMoneyDto,
    val dueOn: String,
    val overdue: Boolean,
    val status: String,
    val version: Long,
    val createdAt: String,
    val modifiedAt: String,
)

@Serializable
data class PlannedIncomeCategoryDto(
    val id: String,
    val name: String,
)

@Serializable
data class PlannedIncomeMoneyDto(
    val amount: String,
    val currency: String,
)
