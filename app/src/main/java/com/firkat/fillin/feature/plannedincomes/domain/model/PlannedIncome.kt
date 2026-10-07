package com.firkat.fillin.feature.plannedincomes.domain.model

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

data class PlannedIncome(
    val id: String,
    val categoryId: String,
    val categoryName: String,
    val plannedAmount: BigDecimal,
    val currency: String,
    val dueOn: LocalDate,
    val overdue: Boolean,
    val status: PlannedIncomeStatus,
    val version: Long,
    val createdAt: Instant,
    val modifiedAt: Instant,
)

enum class PlannedIncomeStatus {
    Scheduled,
    Unknown,
}

data class PlannedIncomes(
    val asOf: LocalDate,
    val items: List<PlannedIncome>,
)
