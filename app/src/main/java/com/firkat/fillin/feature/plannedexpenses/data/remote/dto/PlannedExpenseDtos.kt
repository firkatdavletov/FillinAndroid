package com.firkat.fillin.feature.plannedexpenses.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class PlannedExpensesResponseDto(
    val asOf: String,
    val throughInclusive: String,
    val items: List<PlannedExpenseDto>,
)

@Serializable
data class PlannedExpenseDto(
    val id: String,
    val category: PlannedExpenseCategoryDto,
    val plannedAmount: PlannedExpenseMoneyDto,
    val paidAmount: PlannedExpenseMoneyDto,
    val remainingAmount: PlannedExpenseMoneyDto,
    val dueOn: String,
    val overdue: Boolean,
    val status: String,
    val version: Long,
    val createdAt: String,
    val modifiedAt: String,
)

@Serializable
data class PlannedExpenseCategoryDto(
    val id: String,
    val name: String,
)

@Serializable
data class PlannedExpenseMoneyDto(
    val amount: String,
    val currency: String,
)
