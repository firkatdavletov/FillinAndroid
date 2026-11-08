package com.firkat.fillin.feature.plannedexpenses.domain.model

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

data class PlannedExpense(
    val id: String,
    val categoryId: String,
    val categoryName: String,
    val plannedAmount: BigDecimal,
    val paidAmount: BigDecimal,
    val remainingAmount: BigDecimal,
    val currency: String,
    val dueOn: LocalDate,
    val overdue: Boolean,
    val status: PlannedExpenseStatus,
    val version: Long,
    val createdAt: Instant,
    val modifiedAt: Instant,
    val ruleId: String = id,
    val ruleVersion: Long = version,
) {
    val canBeClosed: Boolean
        get() = status == PlannedExpenseStatus.Scheduled &&
            paidAmount > BigDecimal.ZERO &&
            paidAmount < plannedAmount &&
            remainingAmount > BigDecimal.ZERO
}

enum class PlannedExpenseStatus {
    Scheduled,
    Paid,
    Unknown,
}

data class PlannedExpenses(
    val asOf: LocalDate,
    val throughInclusive: LocalDate,
    val items: List<PlannedExpense>,
)

enum class PlannedExpenseFrequency {
    Once,
    Monthly,
    Weekly,
}

data class PlannedExpenseRule(
    val id: String,
    val version: Long,
)
