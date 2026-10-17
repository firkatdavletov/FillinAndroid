package com.firkat.fillin.feature.plannedoperations.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class PlannedOperationsResponseDto(
    val asOf: String,
    val dateFrom: String,
    val dateTo: String,
    val items: List<PlannedOperationDto>,
)

@Serializable
data class PlannedOperationDto(
    val id: String,
    val type: String,
    val category: PlannedOperationCategoryDto,
    val plannedAmount: PlannedOperationMoneyDto,
    val paidAmount: PlannedOperationMoneyDto,
    val remainingAmount: PlannedOperationMoneyDto,
    val dueOn: String,
    val overdue: Boolean,
    val status: String,
    val version: Long,
    val createdAt: String,
    val modifiedAt: String,
    val rule: PlannedOperationRuleDto? = null,
)

@Serializable
data class PlannedOperationCategoryDto(val id: String, val name: String)

@Serializable
data class PlannedOperationMoneyDto(val amount: String, val currency: String)

@Serializable
data class PlannedOperationRuleDto(val id: String, val version: Long)
