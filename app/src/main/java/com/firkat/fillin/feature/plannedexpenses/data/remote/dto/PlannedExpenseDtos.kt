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
    val rule: PlannedExpenseRuleDto? = null,
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

@Serializable
data class CreatePlannedExpenseRuleRequestDto(
    val categoryId: String,
    val amount: String,
    val frequency: String,
    val scheduleValue: Int? = null,
    val startsOn: String,
)

@Serializable
data class UpdatePlannedExpenseRuleRequestDto(
    val version: Long,
    val amount: String,
)

@Serializable
data class ClosePlannedExpenseRequestDto(
    val version: Long,
)

@Serializable
data class PlannedExpenseRuleDto(
    val id: String,
    val version: Long,
)
